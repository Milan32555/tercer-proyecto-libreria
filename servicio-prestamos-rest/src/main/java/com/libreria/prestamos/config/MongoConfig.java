package com.libreria.prestamos.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Conexión a la base de datos propia del microservicio de préstamos. */
@Configuration
@EnableMongoRepositories(basePackages = "com.libreria.prestamos.repository")
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${libreria.mongo.uri}")
    private String uri;

    @Value("${libreria.mongo.base}")
    private String base;

    @Override
    protected String getDatabaseName() {
        return base;
    }

    @Override
    protected void configureClientSettings(MongoClientSettings.Builder builder) {
        builder.applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(5, TimeUnit.SECONDS));
    }

    /** Crea los índices declarados en Prestamo al arrancar. */
    @Override
    protected boolean autoIndexCreation() {
        return true;
    }

    @Override
    protected Collection<String> getMappingBasePackages() {
        return List.of("com.libreria.prestamos.model");
    }
}
