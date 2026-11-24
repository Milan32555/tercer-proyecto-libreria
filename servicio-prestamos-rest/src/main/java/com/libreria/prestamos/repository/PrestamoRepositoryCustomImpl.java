package com.libreria.prestamos.repository;

import com.libreria.prestamos.model.EstadoPrestamo;
import com.libreria.prestamos.model.Prestamo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/** Filtra en MongoDB por ejemplar y/o estado; un filtro nulo no se aplica. */
public class PrestamoRepositoryCustomImpl implements PrestamoRepositoryCustom {

    private final MongoTemplate mongo;

    public PrestamoRepositoryCustomImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Page<Prestamo> buscar(String codigoEjemplar, EstadoPrestamo estado, Pageable pageable) {
        Query consulta = new Query();
        if (codigoEjemplar != null) {
            consulta.addCriteria(Criteria.where("codigoEjemplar").is(codigoEjemplar));
        }
        if (estado != null) {
            consulta.addCriteria(Criteria.where("estado").is(estado));
        }
        long total = mongo.count(consulta, Prestamo.class);
        return new PageImpl<>(mongo.find(consulta.with(pageable), Prestamo.class), pageable, total);
    }
}
