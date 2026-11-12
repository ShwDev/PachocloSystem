package com.pachoclosystem.pachoclosystem;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Solo en los tests: cada contexto de Spring arranca con la base de pruebas
 * vacía, igual que antes arrancaba con los repositorios en memoria vacíos.
 * Antes de borrar nada comprueba que la base termina en {@code _test}; si no,
 * el contexto no arranca.
 *
 * <p>Se ejecuta al terminar de crear los singletons, antes de los
 * {@code ApplicationRunner} (así {@code AdminInicial} vuelve a crear el
 * administrador después de la limpieza). Con
 * {@value #PROP_CONSERVAR_DATOS}{@code =true} no borra nada (lo usa la prueba
 * de reinicio para arrancar sobre los datos del arranque anterior).</p>
 *
 * <p>Está en {@code src/test}, así que solo lo encuentra el escaneo de
 * componentes de los tests.</p>
 */
@Configuration(proxyBeanMethods = false)
public class EsquemaLimpioEnTestsConfig {

    public static final String PROP_CONSERVAR_DATOS = "pruebas.conservar-datos";

    @Bean
    SmartInitializingSingleton esquemaLimpio(MongoTemplate mongoTemplate, Environment entorno) {
        return () -> {
            BaseDeDatosDePruebas.comprobarQueEsDePruebas(mongoTemplate);
            if (!entorno.getProperty(PROP_CONSERVAR_DATOS, Boolean.class, false)) {
                BaseDeDatosDePruebas.limpiar(mongoTemplate);
            }
        };
    }
}
