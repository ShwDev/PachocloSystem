package com.pachoclosystem.medicamentos;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Arranca el MongoDB de pruebas antes de que JUnit ejecute el primer test (y,
 * por tanto, antes de crear cualquier contexto de Spring). Se registra en
 * {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}.
 */
public class MongoDePruebasListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession sesion) {
        MongoDePruebas.iniciar();
    }
}
