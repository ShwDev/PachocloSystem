import { afterEach, describe, expect, it } from 'vitest';
import { formatearFecha } from './fechas';
import { nuevaClaveIdempotencia } from './idempotencia';
import { CLAVE_SESION, leerRol, leerToken } from './sesion';

afterEach(() => sessionStorage.clear());

function guardarSesion(sesion: unknown) {
  sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
}

describe('utilidades de la interfaz React', () => {
  it('formatea fechas sin pasarlas por Date', () => {
    expect(formatearFecha('2027-01-31')).toBe('31/01/2027');
    expect(formatearFecha('texto')).toBe('texto');
  });

  it('genera claves compatibles con Idempotency-Key', () => {
    expect(nuevaClaveIdempotencia()).toMatch(/^[A-Za-z0-9_-]{16,100}$/);
  });

  it('lee el token vigente de la sesión compartida con Angular', () => {
    guardarSesion({ token: 'jwt-1', expiraEn: Date.now() + 60_000, usuario: { rol: 'ADMIN' } });

    expect(leerToken()).toBe('jwt-1');
    expect(leerRol()).toBe('ADMIN');
  });

  it('ignora una sesión expirada, corrupta o con rol desconocido', () => {
    guardarSesion({ token: 'jwt-1', expiraEn: Date.now() - 1, usuario: { rol: 'ADMIN' } });
    expect(leerToken()).toBeNull();

    sessionStorage.setItem(CLAVE_SESION, 'no es json');
    expect(leerToken()).toBeNull();
    expect(leerRol()).toBeNull();

    guardarSesion({ token: 'jwt-2', expiraEn: Date.now() + 60_000, usuario: { rol: 'OTRO' } });
    expect(leerToken()).toBe('jwt-2');
    expect(leerRol()).toBeNull();
  });
});
