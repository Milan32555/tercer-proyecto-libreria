package com.libreria.prestamos.repository;

import com.libreria.prestamos.model.EstadoPrestamo;
import com.libreria.prestamos.model.Prestamo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Consulta con filtros opcionales. */
public interface PrestamoRepositoryCustom {

    Page<Prestamo> buscar(String codigoEjemplar, EstadoPrestamo estado, Pageable pageable);
}
