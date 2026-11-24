package com.libreria.servidor.tareas;

import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.ResumenInventario;
import com.libreria.servidor.repository.EjemplarRepository;
import com.libreria.servidor.repository.LibroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Las 3 tareas del resumen con los repositorios simulados. */
class ServicioResumenInventarioUnitTest {

    private LibroRepository libros;
    private EjemplarRepository ejemplares;

    @BeforeEach
    void preparar() {
        libros = mock(LibroRepository.class);
        ejemplares = mock(EjemplarRepository.class);
    }

    @Test
    void calculaElResumenConLasTresTareas() {
        when(libros.count()).thenReturn(4L);
        when(ejemplares.contarPorEstado(null)).thenReturn(Map.of(EstadoEjemplar.DISPONIBLE, 3L,
                EstadoEjemplar.PRESTADO, 1L));
        when(ejemplares.sumarPrecioReposicion()).thenReturn(1000.0);

        ResumenInventario resumen = new ServicioResumenInventario(libros, ejemplares, new SyncTaskExecutor(), 1000)
                .resumen();

        assertThat(resumen.totalLibros()).isEqualTo(4);
        assertThat(resumen.totalEjemplares()).isEqualTo(4);
        assertThat(resumen.valorInventario()).isEqualTo(1000);
        assertThat(resumen.ejemplaresPorEstado().get(0).porcentaje()).isEqualTo(75);
        assertThat(resumen.tareas()).allMatch(t -> t.exitosa());
    }

    @Test
    void unaTareaFallidaNoDañaElResumen() {
        when(libros.count()).thenThrow(new DataAccessResourceFailureException("caída"));
        when(ejemplares.contarPorEstado(null)).thenReturn(Map.of());
        when(ejemplares.sumarPrecioReposicion()).thenReturn(0.0);

        ResumenInventario resumen = new ServicioResumenInventario(libros, ejemplares, new SyncTaskExecutor(), 1000)
                .resumen();

        assertThat(resumen.totalLibros()).isEqualTo(-1);
        assertThat(resumen.totalEjemplares()).isZero();
        assertThat(resumen.tareas().get(0).exitosa()).isFalse();
        assertThat(resumen.tareas().get(0).mensaje()).contains("DataAccessResourceFailureException");
    }

    @Test
    void unaTareaDemoradaSeReportaPorTiempo() {
        ResumenInventario resumen = new ServicioResumenInventario(libros, ejemplares, tarea -> { }, 30).resumen();

        assertThat(resumen.tareas()).allSatisfy(t -> {
            assertThat(t.exitosa()).isFalse();
            assertThat(t.mensaje()).contains("tiempo máximo");
        });
    }

    @Test
    void causaRealQuitaElEnvoltorio() {
        IllegalStateException origen = new IllegalStateException("x");

        assertThat(ServicioResumenInventario.causaReal(new CompletionException(origen))).isSameAs(origen);
        assertThat(ServicioResumenInventario.causaReal(origen)).isSameAs(origen);
        CompletionException sinCausa = new CompletionException(null);
        assertThat(ServicioResumenInventario.causaReal(sinCausa)).isSameAs(sinCausa);
    }
}
