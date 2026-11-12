package com.pachoclosystem.medicamentos;

import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Presentacion;
import com.pachoclosystem.medicamentos.service.MedicamentoService;
import com.pachoclosystem.medicamentos.service.SalidasIdempotentesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los medicamentos, su stock y las claves de
 * idempotencia sobreviven a un reinicio. Se simula arrancando un segundo
 * contexto de la aplicación, independiente del de los tests, contra la misma
 * base.
 */
class PersistenciaTrasReinicioIntegrationTest extends MongoTestBase {

    private static final String CLAVE = "clave-antes-del-reinicio-0001";

    @Autowired
    private MedicamentoService medicamentos;

    @Autowired
    private SalidasIdempotentesService salidas;

    @Test
    void elStockYLasClavesSobrevivenAUnReinicio() {
        String id = medicamentos.registrarMedicamento(new DatosMedicamento("Dolex", "Paracetamol",
                Presentacion.TABLETA, "500 mg", "Genfar", "L-1", 5, HOY.plusYears(1), "Estante A3"), 20)
                .getIdMedicamento();
        SalidasIdempotentesService.Resultado antes = salidas.registrarSalida(id, 3, CLAVE);

        // Una hora después, el servicio "se reinicia".
        reloj.avanzar(Duration.ofHours(1));
        // Caducidad amplia: este contexto usa el reloj del sistema, no el fijado de
        // los tests, y la clave no debe caducar por eso. Va como argumento de línea
        // de comandos porque así tiene prioridad sobre application.properties.
        try (ConfigurableApplicationContext reiniciado = new SpringApplicationBuilder(MedicamentosApplication.class)
                .web(WebApplicationType.NONE)
                .run("--medicamentos.idempotencia.caducidad=3650d")) {
            MedicamentoService medicamentosTrasReinicio = reiniciado.getBean(MedicamentoService.class);
            SalidasIdempotentesService salidasTrasReinicio = reiniciado.getBean(SalidasIdempotentesService.class);

            assertThat(medicamentosTrasReinicio.obtenerMedicamento(id).getCantidadStock()).isEqualTo(17);

            // Repetir la salida con la misma clave no vuelve a descontar.
            SalidasIdempotentesService.Resultado despues = salidasTrasReinicio.registrarSalida(id, 3, CLAVE);
            assertThat(despues.repetida()).isTrue();
            assertThat(despues.respuesta()).isEqualTo(antes.respuesta());
            assertThat(medicamentosTrasReinicio.obtenerMedicamento(id).getCantidadStock()).isEqualTo(17);
        }
    }
}
