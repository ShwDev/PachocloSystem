package com.pachoclosystem.medicamentos.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validaciones estrictas del perfil {@code prod} ({@code SPRING_PROFILES_ACTIVE=prod}).
 * Sin el perfil (desarrollo y tests) no actúa y todo funciona como siempre.
 *
 * <p>Se ejecuta antes de crear cualquier bean (también antes de conectar con la
 * base de datos) y, si algo falta, el servicio no arranca. El error enumera todos
 * los problemas a la vez con el nombre de cada variable, nunca su valor.</p>
 *
 * <ul>
 *   <li>{@code MEDICAMENTOS_API_KEY} de al menos {@value #LONGITUD_MINIMA_CLAVE}
 *       caracteres: en producción la API nunca queda sin clave de servicio.</li>
 *   <li>{@code SPRING_MONGODB_URI} definida y que no apunte a localhost.</li>
 * </ul>
 */
@Component
@Profile(ValidacionProduccion.PERFIL)
public class ValidacionProduccion implements BeanFactoryPostProcessor, EnvironmentAware {

    public static final String PERFIL = "prod";
    static final int LONGITUD_MINIMA_CLAVE = 32;

    private Environment entorno;

    @Override
    public void setEnvironment(Environment entorno) {
        this.entorno = entorno;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory fabrica) {
        validar(entorno);
    }

    /** Lanza {@link IllegalStateException} con todos los problemas, o nada si no hay ninguno. */
    static void validar(Environment entorno) {
        List<String> errores = new ArrayList<>();
        String claveServicio = entorno.getProperty("medicamentos.api-key");
        if (vacia(claveServicio) || claveServicio.trim().length() < LONGITUD_MINIMA_CLAVE) {
            errores.add("MEDICAMENTOS_API_KEY es obligatoria, con al menos " + LONGITUD_MINIMA_CLAVE
                    + " caracteres, y debe ser la misma en PachocloSystem.");
        }
        if (apuntaALocalhostMongo(entorno.getProperty("spring.mongodb.uri"))) {
            errores.add("SPRING_MONGODB_URI debe ser la base de datos de producción, no localhost.");
        }
        if (!errores.isEmpty()) {
            throw new IllegalStateException("Configuración de producción incompleta (perfil prod): "
                    + String.join(" ", errores));
        }
    }

    private static boolean vacia(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * Sin URI, o con algún host localhost / 127.x / ::1. Admite la forma de
     * MongoDB con lista de hosts ({@code mongodb://a:27017,b:27017/db}) y
     * {@code mongodb+srv://}, donde {@code URI.create(String)} no basta.
     */
    static boolean apuntaALocalhostMongo(String uri) {
        if (vacia(uri)) {
            return true;
        }
        String autoridad = uri.trim();
        int esquema = autoridad.indexOf("://");
        if (esquema >= 0) {
            autoridad = autoridad.substring(esquema + 3);
        }
        int finAutoridad = autoridad.indexOf('/');
        if (finAutoridad >= 0) {
            autoridad = autoridad.substring(0, finAutoridad);
        }
        int arroba = autoridad.lastIndexOf('@');
        if (arroba >= 0) {
            autoridad = autoridad.substring(arroba + 1);
        }
        for (String hostPuerto : autoridad.split(",")) {
            String host = hostPuerto.trim();
            if (host.startsWith("[")) {
                int cierre = host.indexOf(']');
                host = cierre > 0 ? host.substring(1, cierre) : host;
            } else {
                int dosPuntos = host.indexOf(':');
                if (dosPuntos >= 0) {
                    host = host.substring(0, dosPuntos);
                }
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.startsWith("127.") || host.equals("::1")) {
                return true;
            }
        }
        return false;
    }
}
