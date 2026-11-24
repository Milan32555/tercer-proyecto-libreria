package com.libreria.servidor.servicio;

import com.libreria.servidor.dto.PrestamoInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.integracion.ClientePrestamos;
import com.libreria.servidor.model.EstadoPrestamo;
import com.libreria.servidor.model.Pagina;
import com.libreria.servidor.model.Prestamo;
import com.libreria.servidor.repository.EjemplarRepository;
import com.libreria.servidor.repository.LibroRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

/**
 * Fachada de la clase C: los clientes GUI solo hablan con este microservicio GraphQL, y aquí se
 * invoca el microservicio REST de préstamos. Garantiza la integridad referencial (el ejemplar
 * debe existir) y completa cada préstamo, uno a uno por consulta, con título y autor del libro.
 */
@Service
@Validated
public class ServicioPrestamo {

    private static final Logger log = LoggerFactory.getLogger(ServicioPrestamo.class);

    private final ClientePrestamos cliente;
    private final EjemplarRepository ejemplares;
    private final LibroRepository libros;

    public ServicioPrestamo(ClientePrestamos cliente, EjemplarRepository ejemplares, LibroRepository libros) {
        this.cliente = cliente;
        this.ejemplares = ejemplares;
        this.libros = libros;
    }

    public Pagina<Prestamo> listar(int pagina, int tamanio, String codigoEjemplar, EstadoPrestamo estado) {
        Pagina<Prestamo> resultado = cliente.listar(pagina, tamanio,
                ServicioEjemplar.normalizarCodigo(codigoEjemplar), estado);
        resultado.contenido().forEach(this::completarConLibro);
        log.info("Listar préstamos (ejemplar={}, estado={}): {} encontrados", codigoEjemplar, estado,
                resultado.totalElementos());
        return resultado;
    }

    public Optional<Prestamo> buscarPorCodigo(String codigo) {
        String buscado = ServicioEjemplar.normalizarCodigo(codigo);
        if (buscado == null) {
            log.warn("Consultar préstamo: no se indicó el código");
            return Optional.empty();
        }
        Optional<Prestamo> prestamo = cliente.buscar(buscado);
        prestamo.ifPresent(this::completarConLibro);
        return prestamo;
    }

    /** Crea el préstamo. Integridad referencial: el ejemplar debe existir. */
    public Prestamo crear(@Valid PrestamoInput in) {
        String codigoEjemplar = ServicioEjemplar.normalizarCodigo(in.getCodigoEjemplar());
        log.info("Crear préstamo: código={}, ejemplar={}", in.getCodigo(), codigoEjemplar);
        try {
            if (!ejemplares.existsByCodigo(codigoEjemplar)) {
                throw new LibreriaException(Tipo.NO_ENCONTRADO, "No existe un ejemplar con el código "
                        + codigoEjemplar + "; el préstamo debe pertenecer a un ejemplar registrado.");
            }
        } catch (DataAccessException e) {
            throw errorBaseDatos("verificar el ejemplar", e);
        }
        return completarConLibro(cliente.crear(in));
    }

    public Prestamo actualizar(@Valid PrestamoInput in) {
        String codigo = ServicioEjemplar.normalizarCodigo(in.getCodigo());
        log.info("Actualizar préstamo: código={}, versión leída={}", codigo, in.getVersion());
        return completarConLibro(cliente.actualizar(codigo, in));
    }

    /** Elimina el préstamo; los datos del libro se leen antes para devolver toda la información. */
    public Prestamo eliminar(String codigo) {
        String buscado = ServicioEjemplar.normalizarCodigo(codigo);
        Prestamo actual = buscarPorCodigo(codigo).orElseThrow(() -> new LibreriaException(Tipo.NO_ENCONTRADO,
                "No existe un préstamo con el código " + (buscado == null ? "indicado" : buscado) + "."));
        Prestamo eliminado = cliente.eliminar(buscado);
        eliminado.setTituloLibro(actual.getTituloLibro());
        eliminado.setAutorLibro(actual.getAutorLibro());
        log.info("Préstamo eliminado: código={}", buscado);
        return eliminado;
    }

    /** Una consulta por préstamo: ejemplar por código y luego libro por su _id. */
    private Prestamo completarConLibro(Prestamo prestamo) {
        try {
            ejemplares.findByCodigo(prestamo.getCodigoEjemplar())
                    .flatMap(ejemplar -> libros.findById(ejemplar.getLibroId().toHexString()))
                    .ifPresent(libro -> {
                        prestamo.setTituloLibro(libro.getTitulo());
                        prestamo.setAutorLibro(libro.getAutor());
                    });
            return prestamo;
        } catch (DataAccessException e) {
            throw errorBaseDatos("leer el libro del préstamo", e);
        }
    }

    private static LibreriaException errorBaseDatos(String accion, DataAccessException e) {
        log.error("Error de MongoDB al {}", accion, e);
        return new LibreriaException(Tipo.ERROR_INTERNO,
                "No se pudo " + accion + ": la base de datos no respondió. Intente de nuevo.", e);
    }
}
