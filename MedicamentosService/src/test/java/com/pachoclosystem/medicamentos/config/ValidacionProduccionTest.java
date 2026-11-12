package com.pachoclosystem.medicamentos.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** Reglas del perfil prod: qué hace falta para arrancar en producción. */
class ValidacionProduccionTest {

    private static final String CLAVE_SERVICIO = "clave-servicio-de-prueba-de-32-caracteres";
    private static final String PASSWORD_BD = "password-bd-de-prueba";
    private static final String URI_MONGO = "mongodb+srv://usuario:" + PASSWORD_BD
            + "@cluster0.mongodb.net/medicamentos?retryWrites=true&w=majority";

    private static MockEnvironment completa() {
        return new MockEnvironment()
                .withProperty("medicamentos.api-key", CLAVE_SERVICIO)
                .withProperty("spring.mongodb.uri", URI_MONGO);
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
                .withMessageContaining("MEDICAMENTOS_API_KEY")
                .withMessageContaining("SPRING_MONGODB_URI");
    }

    @Test
    void sinClaveDeServicioNuncaArrancaYElMensajeNoIncluyeValores() {
        MockEnvironment entorno = completa().withProperty("medicamentos.api-key", "clave-corta-secreta");

        assertThatIllegalStateException()
                .isThrownBy(() -> ValidacionProduccion.validar(entorno))
                .withMessageContaining("MEDICAMENTOS_API_KEY")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("clave-corta-secreta", PASSWORD_BD));
        assertThatNoException().isThrownBy(() -> ValidacionProduccion.validar(
                completa().withProperty("medicamentos.api-key", "a".repeat(32))));
    }

    @Test
    void laBaseNoPuedeSerLocal() {
        assertThatIllegalStateException().isThrownBy(() -> ValidacionProduccion.validar(
                completa().withProperty("spring.mongodb.uri", "mongodb://localhost:27017/medicamentos")))
                .withMessageContaining("SPRING_MONGODB_URI");
        assertThatIllegalStateException().isThrownBy(() -> ValidacionProduccion.validar(
                completa().withProperty("spring.mongodb.uri", " ")))
                .withMessageContaining("SPRING_MONGODB_URI");
        assertThat(ValidacionProduccion.apuntaALocalhostMongo("mongodb://127.0.0.1:27017/x")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo("mongodb://[::1]:27017/x")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo("mongodb://otro:27017,127.0.0.1:27017/x")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo("mongodb://LOCALHOST:27017/x")).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo(null)).isTrue();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo(
                "mongodb://usuario:clave@mongo.railway.internal:27017/medicamentos?authSource=admin")).isFalse();
        assertThat(ValidacionProduccion.apuntaALocalhostMongo(URI_MONGO)).isFalse();
    }
}
