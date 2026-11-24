package com.libreria.prestamos.excepciones;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/** Convierte las excepciones en una respuesta JSON {codigo, mensaje} con el estado HTTP adecuado. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    public record ErrorRespuesta(String codigo, String mensaje) {
    }

    @ExceptionHandler(PrestamoException.class)
    public ResponseEntity<ErrorRespuesta> negocio(PrestamoException e) {
        return respuesta(e.getTipo(), e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorRespuesta> validacion(ConstraintViolationException e) {
        String mensaje = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining(" "));
        log.warn("Validación rechazada: {}", mensaje);
        return respuesta(PrestamoException.Tipo.VALIDACION, mensaje);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorRespuesta> solicitudInvalida(Exception e) {
        log.warn("Solicitud inválida: {}", e.getMessage());
        return respuesta(PrestamoException.Tipo.VALIDACION, "La solicitud tiene datos con un formato inválido.");
    }

    private static ResponseEntity<ErrorRespuesta> respuesta(PrestamoException.Tipo tipo, String mensaje) {
        return ResponseEntity.status(tipo.getEstadoHttp()).body(new ErrorRespuesta(tipo.name(), mensaje));
    }
}
