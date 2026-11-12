import type { Rol } from './tipos';

/**
 * Clave de `sessionStorage` que usa `AuthService` del frontend Angular. La
 * segunda interfaz comparte la sesión: no inicia sesión por su cuenta, solo lee
 * el token que el usuario ya obtuvo en la SPA.
 */
export const CLAVE_SESION = 'pachoclosystem.sesion';

interface SesionGuardada {
  token?: unknown;
  expiraEn?: unknown;
  usuario?: { rol?: unknown } | null;
}

function leerSesion(): SesionGuardada | null {
  try {
    const sesion = JSON.parse(sessionStorage.getItem(CLAVE_SESION) ?? 'null') as unknown;
    return typeof sesion === 'object' && sesion !== null ? (sesion as SesionGuardada) : null;
  } catch {
    // JSON corrupto o almacenamiento no disponible: se comporta como sin sesión.
    return null;
  }
}

/** Token vigente de la sesión compartida, o `null` si no hay o ya expiró. */
export function leerToken(): string | null {
  const sesion = leerSesion();
  if (typeof sesion?.token !== 'string') {
    return null;
  }
  if (typeof sesion.expiraEn === 'number' && sesion.expiraEn <= Date.now()) {
    return null;
  }
  return sesion.token;
}

const ROLES: readonly Rol[] = ['ADMIN', 'DOCTOR', 'ENFERMERO'];

/** Rol del usuario de la sesión, validado contra los que entiende el backend. */
export function leerRol(): Rol | null {
  const rol = leerSesion()?.usuario?.rol;
  return typeof rol === 'string' && (ROLES as readonly string[]).includes(rol)
    ? (rol as Rol)
    : null;
}
