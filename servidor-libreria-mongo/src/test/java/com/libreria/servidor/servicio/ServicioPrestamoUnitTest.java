package com.libreria.servidor.servicio;

import com.libreria.servidor.dto.PrestamoInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.integracion.ClientePrestamos;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EstadoPrestamo;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.Pagina;
import com.libreria.servidor.model.Prestamo;
import com.libreria.servidor.repository.EjemplarRepository;
import com.libreria.servidor.repository.LibroRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fachada de la clase C con el microservicio REST y los repositorios simulados. */
class ServicioPrestamoUnitTest {

    private static final ObjectId LIBRO_ID = new ObjectId();

    private ClientePrestamos cliente;
    private EjemplarRepository ejemplares;
    private LibroRepository libros;
    private ServicioPrestamo servicio;

    @BeforeEach
    void preparar() {
        cliente = mock(ClientePrestamos.class);
        ejemplares = mock(EjemplarRepository.class);
        libros = mock(LibroRepository.class);
        servicio = new ServicioPrestamo(cliente, ejemplares, libros);
    }

    private static Prestamo prestamo() {
        return Prestamo.builder().id("p1").codigo("PR-001").codigoEjemplar("EJ-001").prestatario("Ana")
                .fechaPrestamo(LocalDateTime.of(2026, 1, 10, 8, 30)).diasPrestamo(7)
                .estado(EstadoPrestamo.ACTIVO).version(0L).build();
    }

    private static PrestamoInput entrada() {
        return PrestamoInput.builder().codigo(" pr-001 ").codigoEjemplar(" ej-001 ").prestatario("Ana")
                .fechaPrestamo(LocalDateTime.of(2026, 1, 10, 8, 30)).diasPrestamo(7).multa(0.0)
                .estado(EstadoPrestamo.ACTIVO).build();
    }

    private void conLibro() {
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.of(
                Ejemplar.builder().codigo("EJ-001").libroId(LIBRO_ID).build()));
        when(libros.findById(LIBRO_ID.toHexString())).thenReturn(Optional.of(
                Libro.builder().titulo("Cien años de soledad").autor("García Márquez").build()));
    }

    private static void esperaError(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, Tipo tipo) {
        assertThatThrownBy(accion).isInstanceOf(LibreriaException.class)
                .satisfies(e -> assertThat(((LibreriaException) e).getTipo()).isEqualTo(tipo));
    }

    @Test
    void listarCompletaCadaPrestamoUnoAUno() {
        conLibro();
        Prestamo primero = prestamo();
        Prestamo segundo = prestamo();
        when(cliente.listar(0, 10, "EJ-001", EstadoPrestamo.ACTIVO))
                .thenReturn(new Pagina<>(List.of(primero, segundo), 0, 10, 2, 1));

        Pagina<Prestamo> resultado = servicio.listar(0, 10, " ej-001 ", EstadoPrestamo.ACTIVO);

        assertThat(resultado.contenido()).allSatisfy(p -> {
            assertThat(p.getTituloLibro()).isEqualTo("Cien años de soledad");
            assertThat(p.getAutorLibro()).isEqualTo("García Márquez");
        });
        verify(ejemplares, times(2)).findByCodigo("EJ-001");
        verify(libros, times(2)).findById(LIBRO_ID.toHexString());
    }

    @Test
    void buscarDevuelveElPrestamoConSuLibro() {
        conLibro();
        when(cliente.buscar("PR-001")).thenReturn(Optional.of(prestamo()));

        assertThat(servicio.buscarPorCodigo(" pr-001 ")).get()
                .extracting(Prestamo::getTituloLibro).isEqualTo("Cien años de soledad");
    }

    @Test
    void buscarInexistenteDevuelveVacio() {
        when(cliente.buscar("PR-404")).thenReturn(Optional.empty());
        assertThat(servicio.buscarPorCodigo("PR-404")).isEmpty();
    }

    @Test
    void buscarSinCodigoNoInvocaElMicroservicio() {
        assertThat(servicio.buscarPorCodigo("  ")).isEmpty();
        verify(cliente, never()).buscar(any());
    }

    @Test
    void crearConEjemplarExistente() {
        conLibro();
        PrestamoInput in = entrada();
        when(ejemplares.existsByCodigo("EJ-001")).thenReturn(true);
        when(cliente.crear(in)).thenReturn(prestamo());

        assertThat(servicio.crear(in).getAutorLibro()).isEqualTo("García Márquez");
    }

    @Test
    void crearConEjemplarInexistenteNoInvocaElMicroservicio() {
        when(ejemplares.existsByCodigo("EJ-001")).thenReturn(false);

        esperaError(() -> servicio.crear(entrada()), Tipo.NO_ENCONTRADO);
        verify(cliente, never()).crear(any());
    }

    @Test
    void crearInformaFallaDeBase() {
        when(ejemplares.existsByCodigo(any())).thenThrow(new DataAccessResourceFailureException("caída"));
        esperaError(() -> servicio.crear(entrada()), Tipo.ERROR_INTERNO);
    }

    @Test
    void actualizarNormalizaElCodigo() {
        conLibro();
        PrestamoInput in = entrada();
        when(cliente.actualizar("PR-001", in)).thenReturn(prestamo());

        assertThat(servicio.actualizar(in).getTituloLibro()).isEqualTo("Cien años de soledad");
    }

    @Test
    void completarToleraEjemplarSinLibro() {
        when(ejemplares.findByCodigo("EJ-001")).thenReturn(Optional.empty());
        when(cliente.buscar("PR-001")).thenReturn(Optional.of(prestamo()));

        assertThat(servicio.buscarPorCodigo("PR-001")).get()
                .extracting(Prestamo::getTituloLibro).isNull();
    }

    @Test
    void completarInformaFallaDeBase() {
        when(ejemplares.findByCodigo(any())).thenThrow(new DataAccessResourceFailureException("caída"));
        when(cliente.buscar("PR-001")).thenReturn(Optional.of(prestamo()));

        esperaError(() -> servicio.buscarPorCodigo("PR-001"), Tipo.ERROR_INTERNO);
    }

    @Test
    void eliminarDevuelveTodaLaInformacion() {
        conLibro();
        when(cliente.buscar("PR-001")).thenReturn(Optional.of(prestamo()));
        when(cliente.eliminar("PR-001")).thenReturn(prestamo());

        Prestamo eliminado = servicio.eliminar(" pr-001 ");

        assertThat(eliminado.getTituloLibro()).isEqualTo("Cien años de soledad");
        assertThat(eliminado.getAutorLibro()).isEqualTo("García Márquez");
    }

    @Test
    void eliminarInexistenteFalla() {
        when(cliente.buscar("PR-404")).thenReturn(Optional.empty());

        esperaError(() -> servicio.eliminar("PR-404"), Tipo.NO_ENCONTRADO);
        verify(cliente, never()).eliminar(any());
    }

    @Test
    void eliminarSinCodigoFalla() {
        esperaError(() -> servicio.eliminar(null), Tipo.NO_ENCONTRADO);
        verify(cliente, never()).eliminar(any());
    }
}
