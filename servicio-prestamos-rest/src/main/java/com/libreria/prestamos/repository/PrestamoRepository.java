package com.libreria.prestamos.repository;

import com.libreria.prestamos.model.EstadoPrestamo;
import com.libreria.prestamos.model.Prestamo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** Acceso a la colección prestamos. */
public interface PrestamoRepository extends MongoRepository<Prestamo, String>, PrestamoRepositoryCustom {

    Optional<Prestamo> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    long countByCodigoEjemplar(String codigoEjemplar);

    boolean existsByCodigoEjemplarAndEstadoAndCodigoNot(String codigoEjemplar, EstadoPrestamo estado, String codigo);
}
