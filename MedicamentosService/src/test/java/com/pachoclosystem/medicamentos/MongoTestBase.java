package com.pachoclosystem.medicamentos;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Base de los tests con la aplicación completa contra el MongoDB de pruebas
 * ({@code src/test/resources/config/application.properties}). Antes de cada
 * test vacía la base (con la salvaguarda de {@link BaseDeDatosDePruebas}) y
 * pone el reloj al inicio de {@link #HOY}, en UTC.
 */
@SpringBootTest(classes = {MedicamentosApplication.class, MongoTestBase.Configuracion.class})
public abstract class MongoTestBase {

    protected static final LocalDate HOY = LocalDate.of(2026, 6, 15);
    protected static final Instant INICIO_DE_HOY = HOY.atStartOfDay().toInstant(ZoneOffset.UTC);

    @TestConfiguration
    public static class Configuracion {
        @Bean
        @Primary
        RelojControlado relojControlado() {
            return new RelojControlado(INICIO_DE_HOY);
        }
    }

    @Autowired
    protected RelojControlado reloj;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @BeforeEach
    void prepararBaseYReloj() {
        reloj.fijar(INICIO_DE_HOY);
        BaseDeDatosDePruebas.vaciar(mongoTemplate);
    }
}
