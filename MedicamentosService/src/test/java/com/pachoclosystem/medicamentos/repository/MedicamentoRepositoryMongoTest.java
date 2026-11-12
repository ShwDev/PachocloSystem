package com.pachoclosystem.medicamentos.repository;

import com.pachoclosystem.medicamentos.MongoTestBase;
import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Medicamento;
import com.pachoclosystem.medicamentos.model.Presentacion;
import com.pachoclosystem.medicamentos.repository.IMedicamentoRepository.ResultadoActualizacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Medicamentos en MongoDB: ids, unicidad de la clave, orden y guardas atómicas de stock. */
class MedicamentoRepositoryMongoTest extends MongoTestBase {

    @Autowired
    private IMedicamentoRepository repositorio;

    private static DatosMedicamento datos(String lote, Presentacion presentacion) {
        return new DatosMedicamento("Dolex", "Paracetamol", presentacion, "500 mg", "Genfar", lote, 10,
                LocalDate.of(2027, 1, 31), "Estante A3");
    }

    private Medicamento nuevo(String lote) {
        return new Medicamento(repositorio.generarNuevoId(), datos(lote, Presentacion.TABLETA), 10);
    }

    @Test
    void elRepositorioDeLaAplicacionEsElDeMongo() {
        assertThat(repositorio).isInstanceOf(MedicamentoRepositoryMongo.class);
    }

    @Test
    void generaIdsConsecutivosConFormatoMed() {
        assertThat(repositorio.generarNuevoId()).isEqualTo("MED-0001");
        assertThat(repositorio.generarNuevoId()).isEqualTo("MED-0002");
    }

    @Test
    void guardarYLeerDevuelveLosMismosDatos() {
        Medicamento medicamento = new Medicamento(repositorio.generarNuevoId(),
                new DatosMedicamento("Amoxil", "Amoxicilina", Presentacion.SUSPENSION, "250 mg/5 ml",
                        "GSK", "L-9", 3, LocalDate.of(2027, 2, 28), "Nevera 2"), 42);
        assertThat(repositorio.guardar(medicamento)).isTrue();

        Medicamento leido = repositorio.buscarPorId(medicamento.getIdMedicamento());

        assertThat(leido).isNotSameAs(medicamento);
        assertThat(leido.getIdMedicamento()).isEqualTo(medicamento.getIdMedicamento());
        assertThat(leido.getDatos()).isEqualTo(medicamento.getDatos());
        assertThat(leido.getCantidadStock()).isEqualTo(42);
    }

    @Test
    void guardarRechazaOtroConLaMismaClave() {
        assertThat(repositorio.guardar(nuevo("L-1"))).isTrue();

        assertThat(repositorio.guardar(nuevo("L-1"))).isFalse();
        assertThat(repositorio.listarTodos()).hasSize(1);
    }

    @Test
    void laClaveNoDistingueMayusculasNiEspaciosRepetidos() {
        repositorio.guardar(nuevo("Lote A"));
        Medicamento variante = new Medicamento(repositorio.generarNuevoId(),
                new DatosMedicamento("DOLEX", "Paracetamol", Presentacion.TABLETA, "500   MG", "Otro",
                        "lote a", 1, LocalDate.of(2027, 1, 31), "Otro"), 1);

        assertThat(repositorio.guardar(variante)).isFalse();
    }

    @Test
    void distintaPresentacionEsOtraClave() {
        repositorio.guardar(nuevo("L-1"));
        Medicamento jarabe = new Medicamento(repositorio.generarNuevoId(), datos("L-1", Presentacion.JARABE), 1);

        assertThat(repositorio.guardar(jarabe)).isTrue();
    }

    @Test
    void guardarEsSoloParaAltasYNoDuplicaElMismoId() {
        Medicamento medicamento = nuevo("L-1");
        repositorio.guardar(medicamento);

        assertThat(repositorio.guardar(medicamento)).isFalse();
        assertThat(repositorio.listarTodos()).hasSize(1);
    }

    @Test
    void actualizarDatosLiberaLaClaveVieja() {
        Medicamento medicamento = nuevo("L-1");
        repositorio.guardar(medicamento);

        assertThat(repositorio.actualizarDatos(medicamento, datos("L-2", Presentacion.TABLETA)))
                .isEqualTo(ResultadoActualizacion.ACTUALIZADO);

        assertThat(medicamento.getDatos().lote()).isEqualTo("L-2");
        assertThat(repositorio.buscarPorId(medicamento.getIdMedicamento()).getDatos().lote()).isEqualTo("L-2");
        assertThat(repositorio.guardar(nuevo("L-1"))).isTrue();
        assertThat(repositorio.guardar(nuevo("L-2"))).isFalse();
    }

    @Test
    void actualizarDatosHaciaUnaClaveOcupadaNoCambiaNada() {
        repositorio.guardar(nuevo("L-1"));
        Medicamento otro = nuevo("L-2");
        repositorio.guardar(otro);

        assertThat(repositorio.actualizarDatos(otro, datos("L-1", Presentacion.TABLETA)))
                .isEqualTo(ResultadoActualizacion.DUPLICADO);

        assertThat(otro.getDatos().lote()).isEqualTo("L-2");
        assertThat(repositorio.buscarPorId(otro.getIdMedicamento()).getDatos().lote()).isEqualTo("L-2");
        assertThat(repositorio.guardar(nuevo("L-2"))).isFalse();
    }

    @Test
    void actualizarDatosDeUnoEliminadoDevuelveNoExiste() {
        Medicamento medicamento = nuevo("L-1");
        repositorio.guardar(medicamento);
        repositorio.eliminar(medicamento.getIdMedicamento());

        assertThat(repositorio.actualizarDatos(medicamento, datos("L-2", Presentacion.TABLETA)))
                .isEqualTo(ResultadoActualizacion.NO_EXISTE);
        assertThat(medicamento.getDatos().lote()).isEqualTo("L-1");
    }

    @Test
    void guardarLosMismosDatosNoEsUn404() {
        Medicamento medicamento = nuevo("L-1");
        repositorio.guardar(medicamento);

        assertThat(repositorio.actualizarDatos(medicamento, datos("L-1", Presentacion.TABLETA)))
                .isEqualTo(ResultadoActualizacion.ACTUALIZADO);
    }

    @Test
    void sumarYDescontarStockLoGuardanSinTocarLosDatos() {
        Medicamento medicamento = nuevo("L-1");
        repositorio.guardar(medicamento);

        Medicamento sumado = repositorio.sumarStock(medicamento.getIdMedicamento(), 5);
        assertThat(sumado.getCantidadStock()).isEqualTo(15);

        Medicamento descontado = repositorio.descontarStock(medicamento.getIdMedicamento(), 3, HOY);
        assertThat(descontado.getCantidadStock()).isEqualTo(12);

        Medicamento leido = repositorio.buscarPorId(medicamento.getIdMedicamento());
        assertThat(leido.getCantidadStock()).isEqualTo(12);
        assertThat(leido.getDatos()).isEqualTo(medicamento.getDatos());
    }

    @Test
    void lasGuardasDeStockNoAplicanElCambio() {
        Medicamento medicamento = nuevo("L-1");
        repositorio.guardar(medicamento);

        assertThat(repositorio.descontarStock(medicamento.getIdMedicamento(), 11, HOY)).isNull();
        assertThat(repositorio.sumarStock(medicamento.getIdMedicamento(), Integer.MAX_VALUE)).isNull();
        assertThat(repositorio.descontarStock(medicamento.getIdMedicamento(), 1, HOY.plusYears(2))).isNull();
        assertThat(repositorio.sumarStock("MED-9999", 1)).isNull();
        assertThat(repositorio.descontarStock("MED-9999", 1, HOY)).isNull();
        assertThat(repositorio.buscarPorId(medicamento.getIdMedicamento()).getCantidadStock()).isEqualTo(10);
    }

    @Test
    void elMedicamentoQueVenceHoyTodaviaSePuedeDescontar() {
        Medicamento medicamento = nuevo("L-1");
        repositorio.guardar(medicamento);

        assertThat(repositorio.descontarStock(medicamento.getIdMedicamento(), 1,
                medicamento.getDatos().fechaVencimiento())).isNotNull();
    }

    @Test
    void eliminarQuitaElMedicamentoYLiberaSuClave() {
        Medicamento medicamento = nuevo("L-1");
        repositorio.guardar(medicamento);

        assertThat(repositorio.eliminar(medicamento.getIdMedicamento())).isTrue();

        assertThat(repositorio.buscarPorId(medicamento.getIdMedicamento())).isNull();
        assertThat(repositorio.eliminar(medicamento.getIdMedicamento())).isFalse();
        assertThat(repositorio.guardar(nuevo("L-1"))).isTrue();
    }

    @Test
    void listarTodosDevuelveOrdenadoPorId() {
        Medicamento primero = nuevo("L-1");
        Medicamento segundo = nuevo("L-2");
        repositorio.guardar(segundo);
        repositorio.guardar(primero);

        assertThat(repositorio.listarTodos()).extracting(Medicamento::getIdMedicamento)
                .containsExactly(primero.getIdMedicamento(), segundo.getIdMedicamento());
    }

    @Test
    void elOrdenEsElDeStringComoEnJava() {
        // Con más de 9999 medicamentos los ids crecen de dígitos; el orden es el de
        // String.compareTo (el de la versión en memoria), no el numérico.
        repositorio.guardar(new Medicamento("MED-9999", datos("L-1", Presentacion.TABLETA), 1));
        repositorio.guardar(new Medicamento("MED-10000", datos("L-2", Presentacion.TABLETA), 1));

        assertThat(repositorio.listarTodos()).extracting(Medicamento::getIdMedicamento)
                .containsExactly("MED-10000", "MED-9999");
    }

    @Test
    void buscarConIdNuloDevuelveNulo() {
        assertThat(repositorio.buscarPorId(null)).isNull();
        assertThat(repositorio.eliminar(null)).isFalse();
        assertThat(repositorio.sumarStock(null, 1)).isNull();
        assertThat(repositorio.descontarStock(null, 1, HOY)).isNull();
    }
}
