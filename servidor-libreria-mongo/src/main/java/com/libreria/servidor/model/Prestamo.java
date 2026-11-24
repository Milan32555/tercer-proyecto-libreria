package com.libreria.servidor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Clase C tal como la ven los clientes GraphQL. No se guarda aquí: sus datos vienen del
 * microservicio REST de préstamos; tituloLibro y autorLibro los completa este servidor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prestamo {

    private String id;
    private String codigo;
    private String codigoEjemplar;
    private String prestatario;
    private LocalDateTime fechaPrestamo;
    private int diasPrestamo;
    private double multa;
    private EstadoPrestamo estado;
    private String observaciones;
    private Long version;
    private String tituloLibro;
    private String autorLibro;
}
