package com.libreria.prestamos.servicio;

import com.libreria.prestamos.dto.PrestamoInput;
import com.libreria.prestamos.excepciones.PrestamoException;
import com.libreria.prestamos.excepciones.PrestamoException.Tipo;
import com.libreria.prestamos.model.EstadoPrestamo;
import com.libreria.prestamos.model.PaginaPrestamos;
import com.libreria.prestamos.model.Prestamo;
import com.libreria.prestamos.repository.PrestamoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias del servicio con el repositorio simulado (no necesitan MongoDB). */
class ServicioPrestamoTest {

    private static final DataAccessResourceFailureException CAIDA =
            new DataAccessResourceFailureException("MongoDB no responde");

    private PrestamoRepository repositorio;
    private ServicioPrestamo servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(PrestamoRepository.class);
        servicio = new ServicioPrestamo(repositorio);
    }

    private static PrestamoInput.PrestamoInputBuilder entrada() {
        return PrestamoInput.builder()
                .codigo(" pr-001 ")
                .codigoEjemplar(" ej-001 ")
                .prestatario("  Ana Pérez ")
                .fechaPrestamo(LocalDateTime.of(2026, 1, 10, 8, 30))
                .diasPrestamo(7)
                .multa(1500.5)
                .estado(EstadoPrestamo.ACTIVO)
                .observaciones("  Sin novedad ");
    }

    private static Prestamo guardado() {
        return Prestamo.builder().id("abc").codigo("PR-001").codigoEjemplar("EJ-001").prestatario("Ana")
                .fechaPrestamo(LocalDateTime.of(2026, 1, 10, 8, 30)).diasPrestamo(7).multa(0)
                .estado(EstadoPrestamo.ACTIVO).version(3L).build();
    }

    private static void esperaError(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, Tipo tipo) {
        assertThatThrownBy(accion).isInstanceOf(PrestamoException.class)
                .satisfies(e -> assertThat(((PrestamoException) e).getTipo()).isEqualTo(tipo));
    }

    // ---------------------------------------------------------------- Listar

    @Test
    void listarSinFiltrosEnviaNulosAlRepositorio() {
        Pageable pagina = PageRequest.of(0, 10);
        when(repositorio.buscar(isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(guardado()), pagina, 1));

        PaginaPrestamos resultado = servicio.listar(0, 10, "  ", null);

        assertThat(resultado.totalElementos()).isEqualTo(1);
        assertThat(resultado.contenido()).hasSize(1);
    }

    @Test
    void listarConFiltrosNormalizaElEjemplar() {
        when(repositorio.buscar(eq("EJ-001"), eq(EstadoPrestamo.DEVUELTO), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        assertThat(servicio.listar(1, 5, " ej-001 ", EstadoPrestamo.DEVUELTO).contenido()).isEmpty();
    }

    @Test
    void listarAceptaElTamanioMaximo() {
        when(repositorio.buscar(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, ServicioPrestamo.TAMANIO_MAXIMO), 0));

        assertThat(servicio.listar(0, ServicioPrestamo.TAMANIO_MAXIMO, null, null).tamanio())
                .isEqualTo(ServicioPrestamo.TAMANIO_MAXIMO);
    }

    @Test
    void listarRechazaPaginaNegativa() {
        esperaError(() -> servicio.listar(-1, 10, null, null), Tipo.VALIDACION);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void listarRechazaTamanioFueraDeRango(int tamanio) {
        esperaError(() -> servicio.listar(0, tamanio, null, null), Tipo.VALIDACION);
    }

    @Test
    void listarInformaFallaDeBase() {
        when(repositorio.buscar(any(), any(), any())).thenThrow(CAIDA);
        esperaError(() -> servicio.listar(0, 10, null, null), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Consultar y contar

    @Test
    void buscarDevuelveElPrestamo() {
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(guardado()));
        assertThat(servicio.buscar(" pr-001 ").getId()).isEqualTo("abc");
    }

    @Test
    void buscarInexistenteFalla() {
        when(repositorio.findByCodigo("PR-404")).thenReturn(Optional.empty());
        esperaError(() -> servicio.buscar("PR-404"), Tipo.NO_ENCONTRADO);
    }

    @Test
    void buscarSinCodigoFalla() {
        when(repositorio.findByCodigo("")).thenReturn(Optional.empty());
        esperaError(() -> servicio.buscar(null), Tipo.NO_ENCONTRADO);
    }

    @Test
    void buscarInformaFallaDeBase() {
        when(repositorio.findByCodigo(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.buscar("PR-001"), Tipo.ERROR_INTERNO);
    }

    @Test
    void contarPorEjemplarNormalizaElCodigo() {
        when(repositorio.countByCodigoEjemplar("EJ-001")).thenReturn(2L);
        assertThat(servicio.contarPorEjemplar(" ej-001 ")).isEqualTo(2);
    }

    @Test
    void contarInformaFallaDeBase() {
        when(repositorio.countByCodigoEjemplar(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.contarPorEjemplar("EJ-001"), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Crear

    @Test
    void crearNormalizaYGuarda() {
        when(repositorio.insert(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        Prestamo creado = servicio.crear(entrada().build());

        assertThat(creado.getCodigo()).isEqualTo("PR-001");
        assertThat(creado.getCodigoEjemplar()).isEqualTo("EJ-001");
        assertThat(creado.getPrestatario()).isEqualTo("Ana Pérez");
        assertThat(creado.getDiasPrestamo()).isEqualTo(7);
        assertThat(creado.getMulta()).isEqualTo(1500.5);
        assertThat(creado.getObservaciones()).isEqualTo("Sin novedad");
    }

    @Test
    void crearNoActivoNoConsultaOtrosActivos() {
        when(repositorio.insert(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        Prestamo creado = servicio.crear(entrada().estado(EstadoPrestamo.DEVUELTO).observaciones("   ").build());

        assertThat(creado.getObservaciones()).isNull();
        verify(repositorio, never()).existsByCodigoEjemplarAndEstadoAndCodigoNot(any(), any(), any());
    }

    @Test
    void crearSinObservacionesLasDejaNulas() {
        when(repositorio.insert(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(servicio.crear(entrada().observaciones(null).build()).getObservaciones()).isNull();
    }

    @Test
    void crearRechazaCodigoRepetido() {
        when(repositorio.existsByCodigo("PR-001")).thenReturn(true);
        esperaError(() -> servicio.crear(entrada().build()), Tipo.DUPLICADO);
        verify(repositorio, never()).insert(any(Prestamo.class));
    }

    @Test
    void crearRechazaSegundoPrestamoActivo() {
        when(repositorio.existsByCodigoEjemplarAndEstadoAndCodigoNot("EJ-001", EstadoPrestamo.ACTIVO, "PR-001"))
                .thenReturn(true);
        esperaError(() -> servicio.crear(entrada().build()), Tipo.REGLA_NEGOCIO);
    }

    @Test
    void crearTraduceElIndiceUnico() {
        when(repositorio.insert(any(Prestamo.class))).thenThrow(new DuplicateKeyException("duplicado"));
        esperaError(() -> servicio.crear(entrada().build()), Tipo.DUPLICADO);
    }

    @Test
    void crearInformaFallaDeBase() {
        when(repositorio.existsByCodigo(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.crear(entrada().build()), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Actualizar

    @Test
    void actualizarConVersionLaRespeta() {
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(guardado()));
        when(repositorio.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        Prestamo actualizado = servicio.actualizar("pr-001", entrada().version(9L).multa(200.0).build());

        assertThat(actualizado.getVersion()).isEqualTo(9L);
        assertThat(actualizado.getMulta()).isEqualTo(200.0);
    }

    @Test
    void actualizarSinVersionConservaLaLeida() {
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(guardado()));
        when(repositorio.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        Prestamo actualizado = servicio.actualizar("PR-001",
                entrada().estado(EstadoPrestamo.DEVUELTO).build());

        assertThat(actualizado.getVersion()).isEqualTo(3L);
        assertThat(actualizado.getEstado()).isEqualTo(EstadoPrestamo.DEVUELTO);
    }

    @Test
    void actualizarRechazaCambioDeCodigo() {
        esperaError(() -> servicio.actualizar("PR-002", entrada().build()), Tipo.VALIDACION);
        verify(repositorio, never()).findByCodigo(any());
    }

    @Test
    void actualizarInexistenteFalla() {
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.empty());
        esperaError(() -> servicio.actualizar("PR-001", entrada().build()), Tipo.NO_ENCONTRADO);
    }

    @Test
    void actualizarRechazaCambioDeEjemplar() {
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(guardado()));
        esperaError(() -> servicio.actualizar("PR-001", entrada().codigoEjemplar("EJ-002").build()),
                Tipo.REGLA_NEGOCIO);
    }

    @Test
    void actualizarRechazaOtroPrestamoActivo() {
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(guardado()));
        when(repositorio.existsByCodigoEjemplarAndEstadoAndCodigoNot("EJ-001", EstadoPrestamo.ACTIVO, "PR-001"))
                .thenReturn(true);
        esperaError(() -> servicio.actualizar("PR-001", entrada().build()), Tipo.REGLA_NEGOCIO);
    }

    @Test
    void actualizarTraduceConflictoDeVersion() {
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(guardado()));
        when(repositorio.save(any(Prestamo.class))).thenThrow(new OptimisticLockingFailureException("versión"));
        esperaError(() -> servicio.actualizar("PR-001", entrada().build()), Tipo.CONFLICTO);
    }

    @Test
    void actualizarTraduceElIndiceDeActivo() {
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(guardado()));
        when(repositorio.save(any(Prestamo.class))).thenThrow(new DuplicateKeyException("duplicado"));
        esperaError(() -> servicio.actualizar("PR-001", entrada().build()), Tipo.DUPLICADO);
    }

    @Test
    void actualizarInformaFallaDeBase() {
        when(repositorio.findByCodigo(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.actualizar("PR-001", entrada().build()), Tipo.ERROR_INTERNO);
    }

    // ---------------------------------------------------------------- Eliminar

    @Test
    void eliminarBorraYDevuelve() {
        Prestamo prestamo = guardado();
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(prestamo));

        assertThat(servicio.eliminar(" pr-001 ")).isSameAs(prestamo);
        verify(repositorio).delete(prestamo);
    }

    @Test
    void eliminarInexistenteFalla() {
        when(repositorio.findByCodigo("PR-404")).thenReturn(Optional.empty());
        esperaError(() -> servicio.eliminar("PR-404"), Tipo.NO_ENCONTRADO);
    }

    @Test
    void eliminarTraduceConflictoDeVersion() {
        Prestamo prestamo = guardado();
        when(repositorio.findByCodigo("PR-001")).thenReturn(Optional.of(prestamo));
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("versión")).when(repositorio).delete(prestamo);
        esperaError(() -> servicio.eliminar("PR-001"), Tipo.CONFLICTO);
    }

    @Test
    void eliminarInformaFallaDeBase() {
        when(repositorio.findByCodigo(any())).thenThrow(CAIDA);
        esperaError(() -> servicio.eliminar("PR-001"), Tipo.ERROR_INTERNO);
    }
}
