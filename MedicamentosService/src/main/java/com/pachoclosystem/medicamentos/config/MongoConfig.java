package com.pachoclosystem.medicamentos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Transacciones de MongoDB.
 *
 * <p>Spring Boot no registra por defecto el gestor de transacciones de MongoDB,
 * ni con él la plantilla que inyectan los servicios ni el soporte de
 * {@code @Transactional}: este bean habilita las tres cosas a la vez (la
 * transacción de una salida con su clave de idempotencia es de varios
 * documentos).</p>
 *
 * <p>Las transacciones de MongoDB exigen un réplica set (Atlas lo es; el
 * docker-compose de desarrollo arranca {@code mongod} con {@code --replSet}):
 * contra un servidor standalone fallan al confirmar.</p>
 */
@Configuration
public class MongoConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory fabricaBases) {
        return new MongoTransactionManager(fabricaBases);
    }
}
