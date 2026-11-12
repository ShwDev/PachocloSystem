package com.pachoclosystem.pachoclosystem.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** Reglas del perfil prod: qué hace falta para arrancar en producción. */
class ValidacionProduccionTest {

    private static final String SECRETO_JWT = "secreto-jwt-de-prueba-de-al-menos-32-bytes";
    private static final String CLAVE_SERVICIO = "clave-servicio-de-prueba-de-32-caracteres";
    private static final String PASSWORD_BD = "password-bd-de-prueba";
    private static final String URI_MONGO = "mongodb+srv://usuario:" + PASSWORD_BD
            + "@cluster0.mongodb.net/pachoclosystem?retryWrites=true&w=majority";

    private static MockEnvironment completa() {
        return new MockEnvironment()
                .withProperty("app.jwt.secret", SECRETO_JWT)
                .withProperty("medicamentos.api-key", CLAVE_SERVICIO)
                .withProperty("medicamentos.url", "http://medicamentos.railway.internal:8081")
                .withProperty("spring.mongodb.uri", URI_MONGO)
                .withProperty("app.cors.origenes", "https://pachoclosystem.vercel.app");
    }

    @Test
    void conTodaLaConfiguracionArranca() {
        assertThatNoException().isThrownBy(() -> ValidacionProduccion.validar(completa()));
    }

    @Test
    void sinNadaEnumeraTodosLosProblemasALaVez() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ValidacionProduccion.validar(new MockEnvironment()))
                .withMessageContaining("perfil prod")
                .withMessageContaining("JWT_SECRET")
                .withMessageContaining("MEDICAMENTOS_API_KEY")
                .withMessageContaining("MEDICAMENTOS_URL")
                .withMessageContaining("SPRING_MONGODB_URI")
                .withMessageContaining("CORS_ORIGENES");
    }

    @Test
    void elMensajeNuncaIncluyeLosValores() {
        MockEnvironment entorno = completa()
                .withProperty("medicamentos.api-key", "clave-corta-secreta")
                .withProperty("app.cors.origenes", "http://pachoclosystem.vercel.app");

        assertThatIllegalStateException()
                .isThrownBy(() -> ValidacionProduccion.validar(entorno))
                .withMessageContaining("MEDICAMENTOS_API_KEY")
                .withMessageContaining("https")
                .satisfies(error -> assertThat(error.getMessage())
                        .doesNotContain("clave-corta-secreta", SECRETO_JWT, PASSWORD_BD));
    }

    @Test
    void laClaveDeServicioNecesitaAlMenos32Caracteres() {
        assertThatIllegalStateException().isThrownBy(() -> ValidacionProduccion.validar(
                completa().withProperty("medicamentos.api-key", "a".repeat(31))));
        assertThatNoException().isThrownBy(() -> ValidacionProduccion.validar(
                completa().withProperty("medicamentos.api-key", "a".repeat(32))));
    }

    @Test
    void corsSoloConOrigenesHttps() {
        assertThatIllegalStateException().isThrownBy(() -> ValidacionProduccion.validar(
                completa().withProperty("app.cors.origenes", "https://a.vercel.app,http://b.vercel.app")))
                .withMessageContaining("CORS_ORIGENES");
        assertThatIllegalStateException().isThrownBy(() -> ValidacionProduccion.validar(
                completa().withProperty("app.cors.origenes", " ")))
                .withMessageContaining("CORS_ORIGENES");
    }

    @Test
    void laBaseNoPuedeSerLocal() {
        assertThatIllegalStateException().isThrownBy(() -> ValidacionProduccion.validar(
                completa().withProperty("spring.mongodb.uri", "mongodb://localhost:27017/pachoclosystem")))
                .withMessageContaining("SPRING_MONGODB_URI");
        assertThat(ValidacionProduccion.apuntaALocalhostMongo("mongodb://127.0.0.1:27017/x")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo("mongodb://[::1]:27017/x")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo("mongodb://otro:27017,127.0.0.1:27017/x")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo(null)).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo(
                "mongodb://usuario:clave@mongo.railway.internal:27017/pachoclosystem?authSource=admin")).isFalse();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo(URI_MONGO)).isFalse();
    }

    @Test
    void reconoceLasDireccionesLocales() {
        assertThat(ValidacionProduccion.apuntaALocalhost(null)).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhost("http://localhost:8081")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhost("http://127.0.0.1:8081")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhost("http://[::1]:8081")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhost("jdbc:postgresql://LOCALHOST:5432/x")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhost("http://medicamentos.railway.internal:8081")).isFalse();
        assertThat(ValidacionProduccion.apuntaALocalhost("jdbc:postgresql://postgres.railway.internal:5432/x"))
                .isFalse();
    }
}
