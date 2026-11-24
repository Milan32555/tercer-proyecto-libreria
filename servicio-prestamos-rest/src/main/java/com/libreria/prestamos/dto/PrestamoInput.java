package com.libreria.prestamos.dto;

import com.libreria.prestamos.model.EstadoPrestamo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Datos de un préstamo para crearlo o actualizarlo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrestamoInput {

    @NotNull(message = "El código del préstamo es obligatorio.")
    @Pattern(regexp = "(?i)^\\s*PR-\\d{3,6}\\s*$", message = "El código del préstamo debe tener el formato PR-000.")
    private String codigo;

    @NotNull(message = "El código del ejemplar es obligatorio.")
    @Pattern(regexp = "(?i)^\\s*EJ-\\d{3,6}\\s*$", message = "El código del ejemplar debe tener el formato EJ-000.")
    private String codigoEjemplar;

    @NotBlank(message = "El prestatario es obligatorio.")
    @Size(max = 120, message = "El prestatario admite máximo 120 caracteres.")
    private String prestatario;

    @NotNull(message = "La fecha del préstamo es obligatoria.")
    @PastOrPresent(message = "La fecha del préstamo no puede ser futura.")
    private LocalDateTime fechaPrestamo;

    @NotNull(message = "Los días del préstamo son obligatorios.")
    @Positive(message = "Los días del préstamo deben ser mayores que cero.")
    @Max(value = 365, message = "Los días del préstamo no pueden superar 365.")
    private Integer diasPrestamo;

    @NotNull(message = "La multa es obligatoria.")
    @PositiveOrZero(message = "La multa no puede ser negativa.")
    private Double multa;

    @NotNull(message = "El estado del préstamo es obligatorio.")
    private EstadoPrestamo estado;

    @Size(max = 500, message = "Las observaciones admiten máximo 500 caracteres.")
    private String observaciones;

    /** Solo al actualizar: la versión que se leyó al buscar el préstamo. */
    private Long version;
}
