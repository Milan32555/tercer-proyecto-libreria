package com.libreria.servidor.servicio;

import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.ResultadoCarga;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Paginación, revisiones, Observer y generador de carga, sin MongoDB. */
class ApoyoServiciosUnitTest {

    private MongoTemplate mongo;
    private ServicioRevisiones revisiones;

    @BeforeEach
    void preparar() {
        mongo = mock(MongoTemplate.class);
        revisiones = mock(ServicioRevisiones.class);
    }

    private static void esperaError(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, Tipo tipo) {
        assertThatThrownBy(accion).isInstanceOf(LibreriaException.class)
                .satisfies(e -> assertThat(((LibreriaException) e).getTipo()).isEqualTo(tipo));
    }

    // ---------------------------------------------------------------- Paginación

    @Test
    void paginacionValida() {
        assertThat(Paginacion.crear(2, Paginacion.TAMANIO_MAXIMO, Sort.by("x")).getPageNumber()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"-1,10", "0,0", "0,101"})
    void paginacionInvalida(int pagina, int tamanio) {
        esperaError(() -> Paginacion.crear(pagina, tamanio, Sort.unsorted()), Tipo.VALIDACION);
    }

    @Test
    void limpiarTexto() {
        assertThat(Paginacion.limpiar(null)).isNull();
        assertThat(Paginacion.limpiar("   ")).isNull();
        assertThat(Paginacion.limpiar("  hola ")).isEqualTo("hola");
    }

    // ---------------------------------------------------------------- Revisiones

    @Test
    void incrementarHaceUpsert() {
        new ServicioRevisiones(mongo).incrementar("libros");
        verify(mongo).upsert(any(Query.class), any(UpdateDefinition.class), eq(ServicioRevisiones.COLECCION));
    }

    @Test
    void incrementarToleraFallaDeBase() {
        when(mongo.upsert(any(Query.class), any(UpdateDefinition.class), anyString()))
                .thenThrow(new DataAccessResourceFailureException("caída"));
        new ServicioRevisiones(mongo).incrementar("libros");
        verify(mongo).upsert(any(Query.class), any(UpdateDefinition.class), anyString());
    }

    @Test
    void valorDeLaRevision() {
        when(mongo.findById("libros", Document.class, ServicioRevisiones.COLECCION))
                .thenReturn(new Document("valor", 15));
        when(mongo.findById("ejemplares", Document.class, ServicioRevisiones.COLECCION)).thenReturn(null);

        ServicioRevisiones servicio = new ServicioRevisiones(mongo);

        assertThat(servicio.valor("libros")).isEqualTo(15);
        assertThat(servicio.valor("ejemplares")).isZero();
    }

    @Test
    void valorConFallaDeBaseEsMenosUno() {
        when(mongo.findById(any(), eq(Document.class), anyString()))
                .thenThrow(new DataAccessResourceFailureException("caída"));
        assertThat(new ServicioRevisiones(mongo).valor("libros")).isEqualTo(-1);
    }

    // ---------------------------------------------------------------- Observer

    @Test
    void observadorNotificaLasColeccionesObservadas() {
        ObservadorColecciones observador = new ObservadorColecciones(revisiones);

        observador.onAfterSave(new AfterSaveEvent<>(new Object(), new Document(), "libros"));
        observador.onAfterDelete(new AfterDeleteEvent<>(new Document(), Object.class, "ejemplares"));

        verify(revisiones).incrementar("libros");
        verify(revisiones).incrementar("ejemplares");
    }

    @Test
    void observadorIgnoraOtrasColecciones() {
        new ObservadorColecciones(revisiones).onAfterSave(new AfterSaveEvent<>(new Object(), new Document(), "otra"));
        verify(revisiones, never()).incrementar(any());
    }

    // ---------------------------------------------------------------- Generador de carga

    @SuppressWarnings("unchecked")
    private MongoCollection<Document> prepararGenerador(long librosPrevios, long ejemplaresPrevios) {
        MongoCollection<Document> coleccion = mock(MongoCollection.class);
        when(mongo.count(any(Query.class), eq(Libro.class))).thenReturn(librosPrevios);
        when(mongo.count(any(Query.class), eq(Ejemplar.class))).thenReturn(ejemplaresPrevios);
        when(mongo.getConverter()).thenReturn(mock(MongoConverter.class));
        when(mongo.getCollection(anyString())).thenReturn(coleccion);
        return coleccion;
    }

    @ParameterizedTest
    @CsvSource({"0,0", "6001,0", "1,-1", "1,200001"})
    void generadorRechazaCantidadesFueraDeRango(int libros, int ejemplares) {
        esperaError(() -> new GeneradorDatosCarga(mongo, revisiones).generar(libros, ejemplares), Tipo.VALIDACION);
    }

    @Test
    void generadorConLoteIncompleto() {
        MongoCollection<Document> coleccion = prepararGenerador(0, 0);

        ResultadoCarga resultado = new GeneradorDatosCarga(mongo, revisiones).generar(16, 16);

        assertThat(resultado.librosCreados()).isEqualTo(16);
        assertThat(resultado.ejemplaresCreados()).isEqualTo(16);
        verify(coleccion, times(2)).insertMany(anyList());
        verify(revisiones).incrementar("libros");
        verify(revisiones).incrementar("ejemplares");
    }

    @Test
    void generadorConLotesCompletosYSinEjemplares() {
        MongoCollection<Document> coleccion = prepararGenerador(0, 0);
        GeneradorDatosCarga generador = new GeneradorDatosCarga(mongo, revisiones);

        generador.generar(1000, 1000);
        verify(coleccion, times(2)).insertMany(anyList());

        generador.generar(1, 0);
        verify(coleccion, atLeastOnce()).insertMany(anyList());
    }

    @Test
    void generadorRechazaSiNoCabenMasLibros() {
        when(mongo.count(any(Query.class), eq(Libro.class))).thenReturn(6000L);
        esperaError(() -> new GeneradorDatosCarga(mongo, revisiones).generar(1, 0), Tipo.VALIDACION);
    }

    @Test
    void generadorRechazaSiNoCabenMasEjemplares() {
        when(mongo.count(any(Query.class), eq(Libro.class))).thenReturn(0L);
        when(mongo.count(any(Query.class), eq(Ejemplar.class))).thenReturn(899_999L);
        esperaError(() -> new GeneradorDatosCarga(mongo, revisiones).generar(1, 1), Tipo.VALIDACION);
    }

    @Test
    void generadorInformaFallaDeBase() {
        when(mongo.count(any(Query.class), eq(Libro.class)))
                .thenThrow(new DataAccessResourceFailureException("caída"));
        esperaError(() -> new GeneradorDatosCarga(mongo, revisiones).generar(1, 0), Tipo.ERROR_INTERNO);
    }
}
