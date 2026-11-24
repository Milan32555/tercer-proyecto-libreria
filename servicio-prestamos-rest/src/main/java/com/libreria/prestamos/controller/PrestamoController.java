package com.libreria.prestamos.controller;

import com.libreria.prestamos.dto.PrestamoInput;
import com.libreria.prestamos.model.EstadoPrestamo;
import com.libreria.prestamos.model.PaginaPrestamos;
import com.libreria.prestamos.model.Prestamo;
import com.libreria.prestamos.servicio.ServicioPrestamo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API REST de Préstamo. La consume el microservicio GraphQL de Libro y Ejemplar. */
@RestController
@RequestMapping("/prestamos")
public class PrestamoController {

    private final ServicioPrestamo servicio;

    public PrestamoController(ServicioPrestamo servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/healthCheck")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping
    public ResponseEntity<PaginaPrestamos> listar(@RequestParam(defaultValue = "0") int pagina,
                                                  @RequestParam(defaultValue = "10") int tamanio,
                                                  @RequestParam(required = false) String codigoEjemplar,
                                                  @RequestParam(required = false) EstadoPrestamo estado) {
        return ResponseEntity.ok(servicio.listar(pagina, tamanio, codigoEjemplar, estado));
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<Prestamo> consultar(@PathVariable String codigo) {
        return ResponseEntity.ok(servicio.buscar(codigo));
    }

    @GetMapping("/ejemplar/{codigoEjemplar}/cantidad")
    public ResponseEntity<Long> cantidadPorEjemplar(@PathVariable String codigoEjemplar) {
        return ResponseEntity.ok(servicio.contarPorEjemplar(codigoEjemplar));
    }

    @PostMapping
    public ResponseEntity<Prestamo> crear(@RequestBody PrestamoInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(input));
    }

    @PutMapping("/{codigo}")
    public ResponseEntity<Prestamo> actualizar(@PathVariable String codigo, @RequestBody PrestamoInput input) {
        return ResponseEntity.ok(servicio.actualizar(codigo, input));
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Prestamo> eliminar(@PathVariable String codigo) {
        return ResponseEntity.ok(servicio.eliminar(codigo));
    }
}
