package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.TrabajadorHospital;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Doble en memoria de {@link ITrabajadoresRepository} para los tests unitarios,
 * sin base de datos. Reproduce el contrato de {@link TrabajadorRepositoryMongo}.
 */
public class TrabajadorRepositoryEnMemoria implements ITrabajadoresRepository {

    private final Map<String, TrabajadorHospital> trabajadores = new LinkedHashMap<>();
    private final Map<String, Integer> contadoresPorPrefijo = new HashMap<>();

    @Override
    public synchronized String generarNuevoId(String prefijo) {
        int siguiente = contadoresPorPrefijo.getOrDefault(prefijo, 1);
        contadoresPorPrefijo.put(prefijo, siguiente + 1);
        return String.format("%s-%04d", prefijo, siguiente);
    }

    @Override
    public synchronized boolean guardarTrabajador(TrabajadorHospital u) {
        if (u == null) {
            return false;
        }
        trabajadores.put(u.getIdTrabajador(), u);
        return true;
    }

    /** El objeto guardado es el mismo que modificó el servicio: basta con que exista. */
    @Override
    public synchronized boolean actualizarTrabajador(TrabajadorHospital t) {
        return trabajadores.containsKey(t.getIdTrabajador());
    }

    @Override
    public synchronized TrabajadorHospital buscarPorId(String id) {
        return trabajadores.get(id);
    }

    @Override
    public TrabajadorHospital buscarPorIdParaActualizar(String id) {
        return buscarPorId(id);
    }

    @Override
    public synchronized boolean eliminarTrabajador(String id) {
        return trabajadores.remove(id) != null;
    }

    @Override
    public synchronized List<TrabajadorHospital> obtenerTodos() {
        return new ArrayList<>(trabajadores.values());
    }
}
