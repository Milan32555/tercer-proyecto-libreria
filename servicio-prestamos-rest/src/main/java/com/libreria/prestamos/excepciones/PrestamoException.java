package com.libreria.prestamos.excepciones;

import org.springframework.http.HttpStatus;

/** Excepción de negocio con un mensaje listo para mostrarle al usuario. */
public class PrestamoException extends RuntimeException {

    public enum Tipo {
        VALIDACION(HttpStatus.BAD_REQUEST),
        NO_ENCONTRADO(HttpStatus.NOT_FOUND),
        DUPLICADO(HttpStatus.CONFLICT),
        CONFLICTO(HttpStatus.CONFLICT),
        REGLA_NEGOCIO(HttpStatus.UNPROCESSABLE_CONTENT),
        ERROR_INTERNO(HttpStatus.SERVICE_UNAVAILABLE);

        private final HttpStatus estadoHttp;

        Tipo(HttpStatus estadoHttp) {
            this.estadoHttp = estadoHttp;
        }

        public HttpStatus getEstadoHttp() {
            return estadoHttp;
        }
    }

    private final Tipo tipo;

    public PrestamoException(Tipo tipo, String mensaje) {
        super(mensaje);
        this.tipo = tipo;
    }

    public PrestamoException(Tipo tipo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }
}
