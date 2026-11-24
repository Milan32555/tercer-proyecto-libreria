package com.libreria.servidor.servicio;

import com.libreria.servidor.dto.LibroInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.model.ConteoEstado;
import com.libreria.servidor.model.DetalleLibro;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Genero;
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
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de Libro con los repositorios simulados. */
class ServicioLibroUnitTest {

    private static final ObjectId LIBRO_ID = new ObjectId();
    private static final DataAccessResourceFailureException CAIDA =
            new DataAccessResourceFailureException("MongoDB no responde");

    private LibroRepository libros;
    private EjemplarRepository ejemplares;
    private ServicioLibro servicio;

    @BeforeEach
    void preparar() {
        libros = mock(LibroRepository.class);
        ejemplares = mock(EjemplarRepository.class);
        servicio = new ServicioLibro(libros, ejemplares);
    }

    private static Libro libro() {
        return Libro.builder().id(LIBRO_ID.toHexString()).isbn("1001").titulo("Cien años").autor("Gabo")
                .precio(100).numeroPaginas(10).fechaPublicacion(LocalDateTime.of(1967, 5, 30, 0, 0))
                .genero(Genero.FICCION).version(2L).build();
    }

    private static LibroInput.LibroInputBuilder entrada() {
        return LibroInput.builder().isbn(" 1001 ").titulo(" Cien años ").autor(" Gabo ").precio(200)
                .numeroPaginas(20).fechaPublicacion(LocalDateTime.of(1967, 5, 30, 0, 0)).genero(Genero.FICCION)
                .editorial(" Sudamericana ").sinopsis("  ");
    }

    private static void esperaError(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, Tipo tipo) {
        assertThatThrownBy(accion).isInstanceOf(LibreriaException.class)
                .satisfies(e -> assertThat(((LibreriaException) e).getTipo()).isEqualTo(tipo));
    }

    // ---------------------------------------------------------------- Listar

    @Test
    void listarDevuelveLaPagina() {
        when(libros.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(libro()), PageRequest.of(0, 10), 1));
        assertThat(servicio.listar(0, 10).contenido()).hasSize(1);
    }

    @Test
    void listarRechazaPaginaInvalida() {
        esperaError(() -> servicio.listar(-1, 10), Tipo.VALIDACION);
    }

    @Test
    void listarInformaFallaDeBase() {
        when(libros.findAll(any(Pageable.class))).thenThrow(CAIDA);
        esperaError(() -> servicio.listar(0, 10), Tipo.ERROR_INTERNO);
    }

    @Test
    void filtrarConYSinPrecios() {
        when(libros.buscarPorPrecioYGenero(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertThat(servicio.listarPorPrecioYGenero(null, null, null, 0, 10).contenido()).isEmpty();
        assertThat(servicio.listarPorPrecioYGenero(10.0, 20.0, Genero.CIENCIA, 0, 10).contenido()).isEmpty();
        assertThat(servicio.listarPorPrecioYGenero(10.0, null, null, 0, 10).contenido()).isEmpty();
        assertThat(servicio.listarPorPrecioYGenero(null, 20.0, null, 0, 10).contenido()).isEmpty();
    }

    @Test
    void filtrarRechazaPreciosNegativos() {
        esperaError(() -> servicio.listarPorPrecioYGenero(-1.0, null, null, 0, 10), Tipo.VALIDACION);
        esperaError(() -> servicio.listarPorPrecioYGenero(null, -1.0, null, 0, 10), Tipo.VALIDACION);
    }

    @Test
    void filtrarRechazaRangoInvertido() {
        esperaError(() -> servicio.listarPorPrecioYGenero(30.0, 20.0, null, 0, 10), Tipo.VALIDACION);
    }

    @Test
    void filtrarInformaFallaDeBase() {
        when(libros.buscarPorPrecioYGenero(any(), any(), any(), any())).thenThrow(CAIDA);
        esperaError(() -> servicio.listarPorPrecioYGenero(null, null, null, 0, 10), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Consultar

    @Test
    void buscarPorIsbn() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(libros.findByIsbn("9999")).thenReturn(Optional.empty());

        assertThat(servicio.buscarPorIsbn(" 1001 ")).isPresent();
        assertThat(servicio.buscarPorIsbn("9999")).isEmpty();
        assertThat(servicio.buscarPorIsbn(" ")).isEmpty();
    }

    @Test
    void buscarPorIsbnInformaFallaDeBase() {
        when(libros.findByIsbn(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.buscarPorIsbn("1001"), Tipo.ERROR_INTERNO);
    }

    @Test
    void buscarPorTitulo() {
        when(libros.findByTituloIgnoreCase("cien años")).thenReturn(List.of(libro()));
        when(libros.findByTituloIgnoreCase("otro")).thenReturn(List.of());

        assertThat(servicio.buscarPorTitulo(" cien años ")).isPresent();
        assertThat(servicio.buscarPorTitulo("otro")).isEmpty();
        assertThat(servicio.buscarPorTitulo(null)).isEmpty();
    }

    @Test
    void buscarPorTituloInformaFallaDeBase() {
        when(libros.findByTituloIgnoreCase(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.buscarPorTitulo("Cien"), Tipo.ERROR_INTERNO);
    }

    @Test
    void detalleCalculaLosConteos() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.findByLibroId(any(), any(Sort.class))).thenReturn(List.of(
                Ejemplar.builder().codigo("EJ-001").numeroPrestamos(2).precioReposicion(100).build(),
                Ejemplar.builder().codigo("EJ-002").numeroPrestamos(3).precioReposicion(50).build()));
        when(ejemplares.contarPorEstado(LIBRO_ID)).thenReturn(Map.of(EstadoEjemplar.DISPONIBLE, 2L));

        DetalleLibro detalle = servicio.detalle("1001").orElseThrow();

        assertThat(detalle.totalEjemplares()).isEqualTo(2);
        assertThat(detalle.totalPrestamos()).isEqualTo(5);
        assertThat(detalle.valorReposicion()).isEqualTo(150);
        assertThat(detalle.conteoPorEstado().get(0).porcentaje()).isEqualTo(100);
    }

    @Test
    void detalleDeLibroInexistente() {
        when(libros.findByIsbn("9999")).thenReturn(Optional.empty());
        assertThat(servicio.detalle("9999")).isEmpty();
    }

    @Test
    void detalleInformaFallaDeBase() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.findByLibroId(any(), any(Sort.class))).thenThrow(CAIDA);
        esperaError(() -> servicio.detalle("1001"), Tipo.ERROR_INTERNO);
    }

    @Test
    void conteoEstadoConsideraNulosYTotalCero() {
        ConteoEstado sinDatos = ServicioLibro.conteoEstado(EstadoEjemplar.DANIADO, null, 0);
        ConteoEstado mitad = ServicioLibro.conteoEstado(EstadoEjemplar.PRESTADO, 1L, 2);

        assertThat(sinDatos.cantidad()).isZero();
        assertThat(sinDatos.porcentaje()).isZero();
        assertThat(mitad.porcentaje()).isEqualTo(50);
    }

    // ---------------------------------------------------------------- Crear

    @Test
    void crearLimpiaLosDatos() {
        when(libros.insert(any(Libro.class))).thenAnswer(i -> i.getArgument(0));

        Libro creado = servicio.crear(entrada().build());

        assertThat(creado.getIsbn()).isEqualTo("1001");
        assertThat(creado.getTitulo()).isEqualTo("Cien años");
        assertThat(creado.getEditorial()).isEqualTo("Sudamericana");
        assertThat(creado.getSinopsis()).isNull();
    }

    @Test
    void crearRechazaIsbnRepetido() {
        when(libros.existsByIsbn("1001")).thenReturn(true);
        esperaError(() -> servicio.crear(entrada().build()), Tipo.DUPLICADO);
    }

    @Test
    void crearTraduceElIndiceUnico() {
        when(libros.insert(any(Libro.class))).thenThrow(new DuplicateKeyException("duplicado"));
        esperaError(() -> servicio.crear(entrada().build()), Tipo.DUPLICADO);
    }

    @Test
    void crearInformaFallaDeBase() {
        when(libros.existsByIsbn(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.crear(entrada().build()), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Actualizar

    @Test
    void actualizarConVersion() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(libros.save(any(Libro.class))).thenAnswer(i -> i.getArgument(0));

        Libro actualizado = servicio.actualizar(entrada().version(7L).build());

        assertThat(actualizado.getVersion()).isEqualTo(7L);
        assertThat(actualizado.getPrecio()).isEqualTo(200);
    }

    @Test
    void actualizarSinVersionConservaLaLeida() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(libros.save(any(Libro.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(servicio.actualizar(entrada().build()).getVersion()).isEqualTo(2L);
    }

    @Test
    void actualizarInexistenteFalla() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.empty());
        esperaError(() -> servicio.actualizar(entrada().build()), Tipo.NO_ENCONTRADO);
    }

    @Test
    void actualizarTraduceConflicto() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(libros.save(any(Libro.class))).thenThrow(new OptimisticLockingFailureException("versión"));
        esperaError(() -> servicio.actualizar(entrada().build()), Tipo.CONFLICTO);
    }

    @Test
    void actualizarInformaFallaDeBase() {
        when(libros.findByIsbn(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.actualizar(entrada().build()), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Eliminar

    @Test
    void eliminarSinEjemplares() {
        Libro libro = libro();
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro));
        when(ejemplares.countByLibroId(LIBRO_ID)).thenReturn(0L);

        assertThat(servicio.eliminar(" 1001 ")).isSameAs(libro);
        verify(libros).delete(libro);
    }

    @Test
    void eliminarConEjemplaresSeRechaza() {
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro()));
        when(ejemplares.countByLibroId(LIBRO_ID)).thenReturn(2L);

        esperaError(() -> servicio.eliminar("1001"), Tipo.REGLA_NEGOCIO);
        verify(libros, never()).delete(any(Libro.class));
    }

    @Test
    void eliminarSinIsbnFalla() {
        when(libros.findByIsbn("")).thenReturn(Optional.empty());
        esperaError(() -> servicio.eliminar(null), Tipo.NO_ENCONTRADO);
    }

    @Test
    void eliminarTraduceConflicto() {
        Libro libro = libro();
        when(libros.findByIsbn("1001")).thenReturn(Optional.of(libro));
        doThrow(new OptimisticLockingFailureException("versión")).when(libros).delete(libro);
        esperaError(() -> servicio.eliminar("1001"), Tipo.CONFLICTO);
    }

    @Test
    void eliminarInformaFallaDeBase() {
        when(libros.findByIsbn(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.eliminar("1001"), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Apoyo para GraphQL

    @Test
    void porIdsArmaElMapa() {
        Libro libro = libro();
        when(libros.findAllById(any())).thenReturn(List.of(libro));
        assertThat(servicio.porIds(List.of(libro.getId()))).containsEntry(libro.getId(), libro);
    }

    @Test
    void porIdsInformaFallaDeBase() {
        when(libros.findAllById(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.porIds(List.of("x")), Tipo.ERROR_INTERNO);
    }

    @Test
    void contarEjemplares() {
        when(ejemplares.contarPorLibros(anyCollection())).thenReturn(Map.of(LIBRO_ID, 3L));
        assertThat(servicio.contarEjemplares(List.of(LIBRO_ID))).containsEntry(LIBRO_ID, 3L);
    }

    @Test
    void contarEjemplaresInformaFallaDeBase() {
        when(ejemplares.contarPorLibros(anyCollection())).thenThrow(CAIDA);
        esperaError(() -> servicio.contarEjemplares(List.of(LIBRO_ID)), Tipo.ERROR_INTERNO);
    }

    @Test
    void ejemplaresDe() {
        when(ejemplares.findByLibroId(any(), any(Sort.class))).thenReturn(List.of());
        assertThat(servicio.ejemplaresDe(libro())).isEmpty();
    }

    @Test
    void ejemplaresDeInformaFallaDeBase() {
        when(ejemplares.findByLibroId(any(), any(Sort.class))).thenThrow(CAIDA);
        esperaError(() -> servicio.ejemplaresDe(libro()), Tipo.ERROR_INTERNO);
    }
}
