package com.libreria.prestamos.model;

import org.springframework.data.domain.Page;

import java.util.List;

/** Una página de préstamos (la paginación la hace MongoDB). */
public record PaginaPrestamos(List<Prestamo> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static PaginaPrestamos de(Page<Prestamo> page) {
        return new PaginaPrestamos(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
