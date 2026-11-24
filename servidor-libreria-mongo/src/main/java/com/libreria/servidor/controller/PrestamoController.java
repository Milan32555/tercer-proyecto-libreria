package com.libreria.servidor.controller;

import com.libreria.servidor.dto.PrestamoInput;
import com.libreria.servidor.model.EstadoPrestamo;
import com.libreria.servidor.model.Pagina;
import com.libreria.servidor.model.Prestamo;
import com.libreria.servidor.servicio.ServicioPrestamo;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Optional;

/** Controller GraphQL de Préstamo: la entrada transparente de los clientes a la clase C. */
@Controller
public class PrestamoController {

    private final ServicioPrestamo servicio;

    public PrestamoController(ServicioPrestamo servicio) {
        this.servicio = servicio;
    }

    @QueryMapping
    public Pagina<Prestamo> prestamos(@Argument int pagina, @Argument int tamanio,
                                      @Argument String codigoEjemplar, @Argument EstadoPrestamo estado) {
        return servicio.listar(pagina, tamanio, codigoEjemplar, estado);
    }

    @QueryMapping
    public Optional<Prestamo> prestamoPorCodigo(@Argument String codigo) {
        return servicio.buscarPorCodigo(codigo);
    }

    @MutationMapping
    public Prestamo crearPrestamo(@Argument PrestamoInput input) {
        return servicio.crear(input);
    }

    @MutationMapping
    public Prestamo actualizarPrestamo(@Argument PrestamoInput input) {
        return servicio.actualizar(input);
    }

    @MutationMapping
    public Prestamo eliminarPrestamo(@Argument String codigo) {
        return servicio.eliminar(codigo);
    }
}
