package com.libreria.servidor.servicio;

import com.libreria.servidor.dto.EjemplarInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.integracion.ClientePrestamos;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EjemplarConLibro;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.repository.EjemplarRepository;
import com.libreria.servidor.repository.LibroRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de Ejemplar con los repositorios y el microservicio simulados. */
class ServicioEjemplarUnitTest {

    private static final ObjectId LIBRO_ID = new ObjectId();
    private static final DataAccessResourceFailureException CAIDA =
            new DataAccessResourceFailureException("MongoDB no responde");

    private EjemplarRepository ejemplares;
    private LibroRepository libros;
    private ClientePrestamos prestamos;
    private ServicioEjemplar servicio;

    @BeforeEach
    void preparar() {
        ejemplares = mock(EjemplarRepository.class);
        libros = mock(LibroRepository.class);
        prestamos = mock(ClientePrestamos.class);
        servicio = new ServicioEjemplar(ejemplares, libros, prestamos);
    }

    private static Libro libro() {
        return Libro.builder().id(LIBRO_ID.toHexString()).isbn("1001").titulo("Cien años").autor("Gabo").build();
    }

    private static Ejemplar ejemplar(EstadoEjemplar estado) {
        return Ejemplar.builder().id("e1").codigo("EJ-001").libroId(LIBRO_ID).estado(estado).ubicacion("A1")
                .fechaIngreso(LocalDateTime.of(2024, 1, 1, 8, 0)).precioReposicion(1000).numeroPrestamos(2)
                .version(1L).build();
    }

    private static EjemplarInput.EjemplarInputBuilder entrada() {
        return EjemplarInput.builder().codigo(" ej-001 ").isbnLibro(" 1001 ").estado(EstadoEjemplar.DISPONIBLE)
                .ubicacion(" Estante B ").fechaIngreso(LocalDateTime.of(2024, 1, 1, 8, 0)).precioReposicion(2000)
                .observaciones("  Nota ");
    }

    private static void esperaError(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, Tipo tipo) {
        assertThatThrownBy(accion).isInstanceOf(LibreriaException.class)
                .satisfies(e -> assertThat(((LibreriaException) e).getTipo()).isEqualTo(tipo));
    }

    // ---------------------------------------------------------------- Listar

    @Test
    void listarDevuelveLaPagina() {
        when(ejemplares.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ejemplar(EstadoEjemplar.DISPONIBLE)), PageRequest.of(0, 10), 1));
        assertThat(servicio.listar(0, 10).totalElementos()).isEqualTo(1);
    }

    @Test
    void listarRechazaPaginaInvalida() {
        esperaError(() -> servicio.listar(-1, 10), Tipo.VALIDACION);
    }

    @Test
    void listarInformaFallaDeBase() {
        when(ejemplares.findAll(any(Pageable.class))).thenThrow(CAIDA);
        esperaError(() -> servicio.listar(0, 10), Tipo.ERROR_INTERNO);
    }

    @Test
    void listarConLibroSinIsbn() {
        when(ejemplares.ejemplaresConLibro(eq(EstadoEjemplar.PRESTADO), isNull(), any()))
                .thenReturn(new PageImpl<EjemplarConLibro>(List.of(), PageRequest.of(0, 10), 0));
        assertThat(servicio.listarConLibro(EstadoEjemplar.PRESTADO, " ", 0, 10).contenido()).isEmpty();
    }

    @Test
    void listarConLibroConIsbnExistente() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.ejemplaresConLibro(isNull(), eq(LIBRO_ID), any()))
                .thenReturn(new PageImpl<EjemplarConLibro>(List.of(), PageRequest.of(0, 10), 0));
        assertThat(servicio.listarConLibro(null, "1001", 0, 10).totalElementos()).isZero();
    }

    @Test
    void listarConLibroConIsbnInexistente() {
        when(libros.findByIsbn("9999")).thenReturn(Optional.empty());
        assertThat(servicio.listarConLibro(null, "9999", 0, 10).contenido()).isEmpty();
        verify(ejemplares, never()).ejemplaresConLibro(any(), any(), any());
    }

    @Test
    void listarConLibroRechazaPaginaInvalida() {
        esperaError(() -> servicio.listarConLibro(null, null, 0, 0), Tipo.VALIDACION);
    }

    @Test
    void listarConLibroInformaFallaDeBase() {
        when(ejemplares.ejemplaresConLibro(any(), any(), any())).thenThrow(CAIDA);
        esperaError(() -> servicio.listarConLibro(null, null, 0, 10), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Consultar

    @Test
    void buscarPorCodigoEncontrado() {
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.of(ejemplar(EstadoEjemplar.DISPONIBLE)));
        assertThat(servicio.buscarPorCodigo(" ej-001 ")).isPresent();
    }

    @Test
    void buscarPorCodigoInexistente() {
        when(ejemplares.findByCodigo("EJ-404")).thenReturn(Optional.empty());
        assertThat(servicio.buscarPorCodigo("EJ-404")).isEmpty();
    }

    @Test
    void buscarPorCodigoVacio() {
        assertThat(servicio.buscarPorCodigo(null)).isEmpty();
        verify(ejemplares, never()).findByCodigo(any());
    }

    @Test
    void buscarPorCodigoInformaFallaDeBase() {
        when(ejemplares.findByCodigo(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.buscarPorCodigo("EJ-001"), Tipo.ERROR_INTERNO);
    }

    @Test
    void buscarPorLibroYUbicacionSinDatos() {
        assertThat(servicio.buscarPorLibroYUbicacion(null, "A1")).isEmpty();
        assertThat(servicio.buscarPorLibroYUbicacion("1001", " ")).isEmpty();
        verify(libros, never()).findByIsbn(any());
    }

    @Test
    void buscarPorLibroYUbicacionLibroInexistente() {
        when(libros.findByIsbn("9999")).thenReturn(Optional.empty());
        assertThat(servicio.buscarPorLibroYUbicacion("9999", "A1")).isEmpty();
    }

    @Test
    void buscarPorLibroYUbicacionSinEjemplares() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.findByLibroIdAndUbicacion(LIBRO_ID, "A1")).thenReturn(List.of());
        assertThat(servicio.buscarPorLibroYUbicacion("1001", "A1")).isEmpty();
    }

    @Test
    void buscarPorLibroYUbicacionConEjemplares() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.findByLibroIdAndUbicacion(LIBRO_ID, "A1"))
                .thenReturn(List.of(ejemplar(EstadoEjemplar.DISPONIBLE)));
        assertThat(servicio.buscarPorLibroYUbicacion("1001", "A1")).hasSize(1);
    }

    @Test
    void buscarPorLibroYUbicacionInformaFallaDeBase() {
        when(libros.findByIsbn(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.buscarPorLibroYUbicacion("1001", "A1"), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Crear

    @Test
    void crearConPrestamosIndicados() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.insert(any(Ejemplar.class))).thenAnswer(i -> i.getArgument(0));

        Ejemplar creado = servicio.crear(entrada().numeroPrestamos(4).build());

        assertThat(creado.getCodigo()).isEqualTo("EJ-001");
        assertThat(creado.getLibroId()).isEqualTo(LIBRO_ID);
        assertThat(creado.getNumeroPrestamos()).isEqualTo(4);
        assertThat(creado.getObservaciones()).isEqualTo("Nota");
    }

    @Test
    void crearSinPrestamosEmpiezaEnCero() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.insert(any(Ejemplar.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(servicio.crear(entrada().build()).getNumeroPrestamos()).isZero();
    }

    @Test
    void crearRechazaCodigoRepetido() {
        when(ejemplares.existsByCodigo("EJ-001")).thenReturn(true);
        esperaError(() -> servicio.crear(entrada().build()), Tipo.DUPLICADO);
    }

    @Test
    void crearRechazaLibroInexistente() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.empty());
        esperaError(() -> servicio.crear(entrada().build()), Tipo.NO_ENCONTRADO);
    }

    @Test
    void crearTraduceElIndiceUnico() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.insert(any(Ejemplar.class))).thenThrow(new DuplicateKeyException("duplicado"));
        esperaError(() -> servicio.crear(entrada().build()), Tipo.DUPLICADO);
    }

    @Test
    void crearInformaFallaDeBase() {
        when(ejemplares.existsByCodigo(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.crear(entrada().build()), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Actualizar

    private void conEjemplar(EstadoEjemplar estado) {
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.of(ejemplar(estado)));
        when(libros.findById(LIBRO_ID.toHexString())).thenReturn(Optional.of(libro()));
        when(ejemplares.save(any(Ejemplar.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void actualizarAPrestadoSumaUnPrestamo() {
        conEjemplar(EstadoEjemplar.DISPONIBLE);

        Ejemplar actualizado = servicio.actualizar(entrada().estado(EstadoEjemplar.PRESTADO).version(5L).build());

        assertThat(actualizado.getNumeroPrestamos()).isEqualTo(3);
        assertThat(actualizado.getVersion()).isEqualTo(5L);
    }

    @Test
    void actualizarYaPrestadoNoSuma() {
        conEjemplar(EstadoEjemplar.PRESTADO);

        Ejemplar actualizado = servicio.actualizar(entrada().estado(EstadoEjemplar.PRESTADO).build());

        assertThat(actualizado.getNumeroPrestamos()).isEqualTo(2);
        assertThat(actualizado.getVersion()).isEqualTo(1L);
    }

    @Test
    void actualizarADisponibleNoSuma() {
        conEjemplar(EstadoEjemplar.PRESTADO);
        assertThat(servicio.actualizar(entrada().build()).getNumeroPrestamos()).isEqualTo(2);
    }

    @Test
    void actualizarIgnoraPrestamosDelCliente() {
        conEjemplar(EstadoEjemplar.DISPONIBLE);
        assertThat(servicio.actualizar(entrada().numeroPrestamos(99).build()).getNumeroPrestamos()).isEqualTo(2);
    }

    @Test
    void actualizarAceptaLosMismosPrestamos() {
        conEjemplar(EstadoEjemplar.DISPONIBLE);
        assertThat(servicio.actualizar(entrada().numeroPrestamos(2).build()).getNumeroPrestamos()).isEqualTo(2);
    }

    @Test
    void actualizarInexistenteFalla() {
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.empty());
        esperaError(() -> servicio.actualizar(entrada().build()), Tipo.NO_ENCONTRADO);
    }

    @Test
    void actualizarRechazaCambioDeLibro() {
        conEjemplar(EstadoEjemplar.DISPONIBLE);
        esperaError(() -> servicio.actualizar(entrada().isbnLibro("2002").build()), Tipo.REGLA_NEGOCIO);
    }

    @Test
    void actualizarConLibroPerdidoRechaza() {
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.of(ejemplar(EstadoEjemplar.DISPONIBLE)));
        when(libros.findById(LIBRO_ID.toHexString())).thenReturn(Optional.empty());
        esperaError(() -> servicio.actualizar(entrada().build()), Tipo.REGLA_NEGOCIO);
    }

    @Test
    void actualizarTraduceConflicto() {
        conEjemplar(EstadoEjemplar.DISPONIBLE);
        when(ejemplares.save(any(Ejemplar.class))).thenThrow(new OptimisticLockingFailureException("versión"));
        esperaError(() -> servicio.actualizar(entrada().build()), Tipo.CONFLICTO);
    }

    @Test
    void actualizarInformaFallaDeBase() {
        when(ejemplares.findByCodigo(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.actualizar(entrada().build()), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Eliminar

    @Test
    void eliminarSinPrestamos() {
        Ejemplar ejemplar = ejemplar(EstadoEjemplar.DISPONIBLE);
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.of(ejemplar));
        when(prestamos.contarPorEjemplar("EJ-001")).thenReturn(0L);

        assertThat(servicio.eliminar(" ej-001 ")).isSameAs(ejemplar);
        verify(ejemplares).delete(ejemplar);
    }

    @Test
    void eliminarConPrestamosSeRechaza() {
        Ejemplar ejemplar = ejemplar(EstadoEjemplar.DISPONIBLE);
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.of(ejemplar));
        when(prestamos.contarPorEjemplar("EJ-001")).thenReturn(2L);

        esperaError(() -> servicio.eliminar("EJ-001"), Tipo.REGLA_NEGOCIO);
        verify(ejemplares, never()).delete(any(Ejemplar.class));
    }

    @Test
    void eliminarSiElMicroservicioNoRespondeNoBorra() {
        Ejemplar ejemplar = ejemplar(EstadoEjemplar.DISPONIBLE);
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.of(ejemplar));
        when(prestamos.contarPorEjemplar("EJ-001"))
                .thenThrow(new LibreriaException(Tipo.ERROR_INTERNO, "no disponible"));

        esperaError(() -> servicio.eliminar("EJ-001"), Tipo.ERROR_INTERNO);
        verify(ejemplares, never()).delete(any(Ejemplar.class));
    }

    @Test
    void eliminarInexistenteFalla() {
        when(ejemplares.findByCodigo("EJ-404")).thenReturn(Optional.empty());
        esperaError(() -> servicio.eliminar("EJ-404"), Tipo.NO_ENCONTRADO);
    }

    @Test
    void eliminarSinCodigoFalla() {
        when(ejemplares.findByCodigo("")).thenReturn(Optional.empty());
        esperaError(() -> servicio.eliminar(null), Tipo.NO_ENCONTRADO);
    }

    @Test
    void eliminarTraduceConflicto() {
        Ejemplar ejemplar = ejemplar(EstadoEjemplar.DISPONIBLE);
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.of(ejemplar));
        doThrow(new OptimisticLockingFailureException("versión")).when(ejemplares).delete(ejemplar);
        esperaError(() -> servicio.eliminar("EJ-001"), Tipo.CONFLICTO);
    }

    @Test
    void eliminarInformaFallaDeBase() {
        when(ejemplares.findByCodigo(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.eliminar("EJ-001"), Tipo.ERROR_INTERNO);
    }
}
