package com.libreria.prestamos.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Clase C: préstamo de un ejemplar. Vive en la base de este microservicio; el ejemplar se
 * referencia por su código y su existencia la garantiza el microservicio de Libro y Ejemplar.
 * El índice parcial impide que un mismo ejemplar tenga dos préstamos activos.
 */
@Document("prestamos")
@CompoundIndex(name = "un_activo_por_ejemplar", def = "{'codigoEjemplar': 1}", unique = true,
        partialFilter = "{'estado': 'ACTIVO'}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prestamo {

    @Id
    private String id;

    @Indexed(unique = true)
    private String codigo;

    private String codigoEjemplar;
    private String prestatario;
    private LocalDateTime fechaPrestamo;
    private int diasPrestamo;
    private double multa;
    private EstadoPrestamo estado;
    private String observaciones;

    @Version
    private Long version;
}
