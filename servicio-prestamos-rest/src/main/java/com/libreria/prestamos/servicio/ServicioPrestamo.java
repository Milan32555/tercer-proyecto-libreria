package com.libreria.prestamos.servicio;

import com.libreria.prestamos.dto.PrestamoInput;
import com.libreria.prestamos.excepciones.PrestamoException;
import com.libreria.prestamos.excepciones.PrestamoException.Tipo;
import com.libreria.prestamos.model.EstadoPrestamo;
import com.libreria.prestamos.model.PaginaPrestamos;
import com.libreria.prestamos.model.Prestamo;
import com.libreria.prestamos.repository.PrestamoRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;

/** Lógica de negocio del CRUD de Préstamo (clase C). */
@Service
@Validated
public class ServicioPrestamo {

    static final int TAMANIO_MAXIMO = 100;

    private static final Logger log = LoggerFactory.getLogger(ServicioPrestamo.class);
    private static final Sort ORDEN = Sort.by("codigo");
    private static final String MENSAJE_CONFLICTO = "Otro usuario modificó este préstamo; vuelva a buscarlo.";

    private final PrestamoRepository prestamos;

    public ServicioPrestamo(PrestamoRepository prestamos) {
        this.prestamos = prestamos;
    }

    /** Lista los préstamos, con filtro opcional por ejemplar y/o estado. */
    public PaginaPrestamos listar(int pagina, int tamanio, String codigoEjemplar, EstadoPrestamo estado) {
        if (pagina < 0) {
            throw new PrestamoException(Tipo.VALIDACION, "La página no puede ser negativa.");
        }
        if (tamanio < 1 || tamanio > TAMANIO_MAXIMO) {
            throw new PrestamoException(Tipo.VALIDACION,
                    "El tamaño de página debe estar entre 1 y " + TAMANIO_MAXIMO + ".");
        }
        String ejemplar = normalizar(codigoEjemplar);
        try {
            PaginaPrestamos resultado = PaginaPrestamos.de(prestamos.buscar(ejemplar.isEmpty() ? null : ejemplar,
                    estado, PageRequest.of(pagina, tamanio, ORDEN)));
            log.info("Listar préstamos (ejemplar={}, estado={}): {} encontrados", ejemplar, estado,
                    resultado.totalElementos());
            return resultado;
        } catch (DataAccessException e) {
            throw errorBaseDatos("listar los préstamos", e);
        }
    }

    /** Consulta UN préstamo por su código. */
    public Prestamo buscar(String codigo) {
        String buscado = normalizar(codigo);
        try {
            return prestamos.findByCodigo(buscado).orElseThrow(() -> noExiste(buscado));
        } catch (DataAccessException e) {
            throw errorBaseDatos("buscar el préstamo", e);
        }
    }

    /** Cuántos préstamos tiene un ejemplar (integridad al eliminar el ejemplar). */
    public long contarPorEjemplar(String codigoEjemplar) {
        try {
            return prestamos.countByCodigoEjemplar(normalizar(codigoEjemplar));
        } catch (DataAccessException e) {
            throw errorBaseDatos("contar los préstamos del ejemplar", e);
        }
    }

    /** Crea el préstamo. Reglas: código único y un solo préstamo activo por ejemplar. */
    public Prestamo crear(@Valid PrestamoInput in) {
        String codigo = normalizar(in.getCodigo());
        String ejemplar = normalizar(in.getCodigoEjemplar());
        log.info("Crear préstamo: código={}, ejemplar={}", codigo, ejemplar);
        try {
            if (prestamos.existsByCodigo(codigo)) {
                throw new PrestamoException(Tipo.DUPLICADO, "Ya existe un préstamo con el código " + codigo + ".");
            }
            exigirSinOtroActivo(in.getEstado(), ejemplar, codigo);
            Prestamo nuevo = Prestamo.builder().codigo(codigo).codigoEjemplar(ejemplar).build();
            copiarDatos(in, nuevo);
            return prestamos.insert(nuevo);
        } catch (DuplicateKeyException e) {
            log.warn("Alta rechazada por un índice único: {}", codigo);
            throw new PrestamoException(Tipo.DUPLICADO,
                    "Ya existe ese código de préstamo o el ejemplar ya tiene un préstamo activo.", e);
        } catch (DataAccessException e) {
            throw errorBaseDatos("guardar el préstamo", e);
        }
    }

    /** Actualiza el préstamo. Reglas: no cambia de código ni de ejemplar. */
    public Prestamo actualizar(String codigo, @Valid PrestamoInput in) {
        String buscado = normalizar(codigo);
        if (!buscado.equals(normalizar(in.getCodigo()))) {
            throw new PrestamoException(Tipo.VALIDACION, "El código del préstamo no se puede cambiar.");
        }
        log.info("Actualizar préstamo: código={}, versión leída={}", buscado, in.getVersion());
        try {
            Prestamo prestamo = prestamos.findByCodigo(buscado).orElseThrow(() -> noExiste(buscado));
            if (!prestamo.getCodigoEjemplar().equals(normalizar(in.getCodigoEjemplar()))) {
                throw new PrestamoException(Tipo.REGLA_NEGOCIO, "El préstamo " + buscado + " pertenece al ejemplar "
                        + prestamo.getCodigoEjemplar() + "; no se puede cambiar de ejemplar.");
            }
            exigirSinOtroActivo(in.getEstado(), prestamo.getCodigoEjemplar(), buscado);
            if (in.getVersion() != null) {
                prestamo.setVersion(in.getVersion());
            }
            copiarDatos(in, prestamo);
            return prestamos.save(prestamo);
        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de versión al actualizar el préstamo {}", buscado);
            throw new PrestamoException(Tipo.CONFLICTO, MENSAJE_CONFLICTO, e);
        } catch (DuplicateKeyException e) {
            log.warn("Actualización rechazada por el índice de préstamo activo: {}", buscado);
            throw new PrestamoException(Tipo.DUPLICADO, "El ejemplar ya tiene un préstamo activo.", e);
        } catch (DataAccessException e) {
            throw errorBaseDatos("actualizar el préstamo", e);
        }
    }

    /** Elimina el préstamo y devuelve sus datos. */
    public Prestamo eliminar(String codigo) {
        String buscado = normalizar(codigo);
        log.info("Eliminar préstamo: código={}", buscado);
        try {
            Prestamo prestamo = prestamos.findByCodigo(buscado).orElseThrow(() -> noExiste(buscado));
            prestamos.delete(prestamo);
            return prestamo;
        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de versión al eliminar el préstamo {}", buscado);
            throw new PrestamoException(Tipo.CONFLICTO, MENSAJE_CONFLICTO, e);
        } catch (DataAccessException e) {
            throw errorBaseDatos("eliminar el préstamo", e);
        }
    }

    private void exigirSinOtroActivo(EstadoPrestamo estado, String ejemplar, String codigoPropio) {
        if (estado == EstadoPrestamo.ACTIVO
                && prestamos.existsByCodigoEjemplarAndEstadoAndCodigoNot(ejemplar, EstadoPrestamo.ACTIVO, codigoPropio)) {
            throw new PrestamoException(Tipo.REGLA_NEGOCIO, "El ejemplar " + ejemplar + " ya tiene un préstamo activo.");
        }
    }

    private static void copiarDatos(PrestamoInput in, Prestamo destino) {
        destino.setPrestatario(in.getPrestatario().trim());
        destino.setFechaPrestamo(in.getFechaPrestamo());
        destino.setDiasPrestamo(in.getDiasPrestamo());
        destino.setMulta(in.getMulta());
        destino.setEstado(in.getEstado());
        destino.setObservaciones(limpiar(in.getObservaciones()));
    }

    static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static PrestamoException noExiste(String codigo) {
        return new PrestamoException(Tipo.NO_ENCONTRADO, "No existe un préstamo con el código " + codigo + ".");
    }

    private static PrestamoException errorBaseDatos(String accion, DataAccessException e) {
        log.error("Error de base de datos al {}", accion, e);
        return new PrestamoException(Tipo.ERROR_INTERNO, "No se pudo " + accion + ": la base de datos no responde.", e);
    }
}
