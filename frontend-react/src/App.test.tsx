import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App, MENSAJE_SESION_INVALIDA } from './App';
import { MENSAJE_CAMBIAR_PASSWORD } from './auth';
import { CLAVE_SESION, leerSesion } from './sesion';
import type { Medicamento } from './tipos';

const MEDICAMENTO: Medicamento = {
  idMedicamento: 'MED-1',
  nombre: 'Ibuprofeno',
  principioActivo: 'Ibuprofeno',
  presentacion: 'TABLETA',
  concentracion: '400 mg',
  laboratorio: 'Genfar',
  lote: 'L-01',
  cantidadStock: 10,
  stockMinimo: 5,
  fechaVencimiento: '2027-01-31',
  ubicacion: 'Farmacia',
  stockBajo: false,
  vencido: false,
};

function json(cuerpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** `fetch` falso que responde según la ruta pedida. */
function apiFalsa(rutas: Record<string, () => Response>) {
  return vi.fn<typeof fetch>(async (entrada) => {
    const url = new URL(String(entrada), 'http://localhost');
    const respuesta = rutas[url.pathname];
    if (!respuesta) {
      throw new Error(`Ruta no esperada: ${url.pathname}`);
    }
    return respuesta();
  });
}

function guardarSesionVigente() {
  sessionStorage.setItem(
    CLAVE_SESION,
    JSON.stringify({
      token: 'jwt-guardado',
      expiraEn: Date.now() + 60_000,
      usuario: { username: 'enf', rol: 'ENFERMERO', nombreCompleto: 'Ana Pérez' },
    }),
  );
}

function iniciarSesion(usuario = 'admin', password = 'secreta') {
  fireEvent.change(screen.getByLabelText('Usuario'), { target: { value: usuario } });
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
}

afterEach(() => {
  cleanup();
  sessionStorage.clear();
});

describe('aplicación React de medicamentos', () => {
  it('sin sesión muestra el login y, al entrar, el inventario', async () => {
    const fetchImpl = apiFalsa({
      '/api/auth/login': () => json({ token: 'jwt-1', expiraEnSegundos: 3600 }),
      '/api/auth/me': () =>
        json({ username: 'admin', rol: 'ADMIN', nombreCompleto: null, debeCambiarPassword: false }),
      '/api/medicamentos': () => json([MEDICAMENTO]),
    });
    render(<App apiBaseUrl="" fetchImpl={fetchImpl} />);

    iniciarSesion();

    expect(await screen.findByText('Ibuprofeno 400 mg')).toBeTruthy();
    expect(screen.getByText('(Administrador)')).toBeTruthy();
    expect(leerSesion()?.token).toBe('jwt-1');

    const [, opcionesLogin] = fetchImpl.mock.calls[0];
    expect(JSON.parse(String(opcionesLogin?.body))).toEqual({
      username: 'admin',
      password: 'secreta',
    });
    const [, opcionesLista] = fetchImpl.mock.calls[2];
    expect(new Headers(opcionesLista?.headers).get('Authorization')).toBe('Bearer jwt-1');
  });

  it('muestra el mensaje del backend si las credenciales son inválidas', async () => {
    const fetchImpl = apiFalsa({
      '/api/auth/login': () => json({ mensajes: ['Credenciales inválidas.'] }, 401),
    });
    render(<App apiBaseUrl="" fetchImpl={fetchImpl} />);

    iniciarSesion();

    expect(await screen.findByText('Credenciales inválidas.')).toBeTruthy();
    expect(leerSesion()).toBeNull();
  });

  it('no deja entrar a quien debe cambiar su contraseña', async () => {
    const fetchImpl = apiFalsa({
      '/api/auth/login': () => json({ token: 'jwt-1', expiraEnSegundos: 3600 }),
      '/api/auth/me': () => json({ username: 'doc', rol: 'DOCTOR', debeCambiarPassword: true }),
    });
    render(<App apiBaseUrl="" fetchImpl={fetchImpl} />);

    iniciarSesion('doc');

    expect(await screen.findByText(MENSAJE_CAMBIAR_PASSWORD)).toBeTruthy();
    expect(leerSesion()).toBeNull();
  });

  it('reutiliza la sesión guardada y la cierra si la API responde 401', async () => {
    guardarSesionVigente();
    const fetchImpl = apiFalsa({
      '/api/medicamentos': () => json({ mensajes: ['Token inválido.'] }, 401),
    });
    render(<App apiBaseUrl="" fetchImpl={fetchImpl} />);

    expect(await screen.findByText(MENSAJE_SESION_INVALIDA)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeTruthy();
    expect(leerSesion()).toBeNull();
  });

  it('cierra la sesión con el botón de la barra', async () => {
    guardarSesionVigente();
    const fetchImpl = apiFalsa({ '/api/medicamentos': () => json([MEDICAMENTO]) });
    render(<App apiBaseUrl="" fetchImpl={fetchImpl} />);

    expect(await screen.findByText('Ana Pérez')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    await waitFor(() => expect(screen.getByLabelText('Usuario')).toBeTruthy());
    expect(leerSesion()).toBeNull();
  });
});
