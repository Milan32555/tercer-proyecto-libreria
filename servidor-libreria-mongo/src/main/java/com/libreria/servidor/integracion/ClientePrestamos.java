package com.libreria.servidor.integracion;

import com.libreria.servidor.dto.PrestamoInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.model.EstadoPrestamo;
import com.libreria.servidor.model.Pagina;
import com.libreria.servidor.model.Prestamo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cliente del microservicio REST de préstamos. Traduce sus respuestas de error a
 * LibreriaException para que los clientes GraphQL reciban siempre un mensaje legible.
 */
@Component
public class ClientePrestamos {

    private static final Logger log = LoggerFactory.getLogger(ClientePrestamos.class);
    private static final String NO_DISPONIBLE =
            "El servicio de préstamos no está disponible en este momento. Intente de nuevo.";

    private final RestClient rest;

    public ClientePrestamos(@Value("${libreria.prestamos.url}") String url,
                            @Value("${libreria.prestamos.usuario}") String usuario,
                            @Value("${libreria.prestamos.clave}") String clave) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(8));
        this.rest = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeaders(h -> h.setBasicAuth(usuario, clave, StandardCharsets.UTF_8))
                .build();
    }

    public Pagina<Prestamo> listar(int pagina, int tamanio, String codigoEjemplar, EstadoPrestamo estado) {
        return invocar("listar", () -> rest.get()
                .uri(b -> b.path("/prestamos")
                        .queryParam("pagina", pagina)
                        .queryParam("tamanio", tamanio)
                        .queryParamIfPresent("codigoEjemplar", Optional.ofNullable(codigoEjemplar))
                        .queryParamIfPresent("estado", Optional.ofNullable(estado))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<Prestamo>>() {
                }));
    }

    /** Consulta un préstamo; si no existe devuelve vacío en lugar de un error. */
    public Optional<Prestamo> buscar(String codigo) {
        try {
            return Optional.ofNullable(invocar("consultar", () -> rest.get()
                    .uri("/prestamos/{codigo}", codigo)
                    .retrieve()
                    .body(Prestamo.class)));
        } catch (LibreriaException e) {
            if (e.getTipo() == Tipo.NO_ENCONTRADO) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public long contarPorEjemplar(String codigoEjemplar) {
        Long cantidad = invocar("contar", () -> rest.get()
                .uri("/prestamos/ejemplar/{codigo}/cantidad", codigoEjemplar)
                .retrieve()
                .body(Long.class));
        return cantidad == null ? 0 : cantidad;
    }

    public Prestamo crear(PrestamoInput input) {
        return invocar("crear", () -> rest.post()
                .uri("/prestamos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(input)
                .retrieve()
                .body(Prestamo.class));
    }

    public Prestamo actualizar(String codigo, PrestamoInput input) {
        return invocar("actualizar", () -> rest.put()
                .uri("/prestamos/{codigo}", codigo)
                .contentType(MediaType.APPLICATION_JSON)
                .body(input)
                .retrieve()
                .body(Prestamo.class));
    }

    public Prestamo eliminar(String codigo) {
        return invocar("eliminar", () -> rest.delete()
                .uri("/prestamos/{codigo}", codigo)
                .retrieve()
                .body(Prestamo.class));
    }

    private <T> T invocar(String accion, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            throw traducir(accion, e);
        } catch (RestClientException e) {
            log.error("No se pudo {} en el servicio de préstamos", accion, e);
            throw new LibreriaException(Tipo.ERROR_INTERNO, NO_DISPONIBLE, e);
        }
    }

    private static LibreriaException traducir(String accion, RestClientResponseException e) {
        if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                || e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
            log.error("El servicio de préstamos rechazó la credencial al {}", accion);
            return new LibreriaException(Tipo.ERROR_INTERNO, NO_DISPONIBLE, e);
        }
        try {
            ErrorRemoto remoto = e.getResponseBodyAs(ErrorRemoto.class);
            return new LibreriaException(Tipo.valueOf(remoto.codigo()), remoto.mensaje(), e);
        } catch (RuntimeException sinDetalle) {
            log.error("Respuesta inesperada del servicio de préstamos al {}: {}", accion, e.getStatusCode());
            return new LibreriaException(Tipo.ERROR_INTERNO, NO_DISPONIBLE, e);
        }
    }

    /** Cuerpo de error del microservicio de préstamos. */
    record ErrorRemoto(String codigo, String mensaje) {
    }
}
