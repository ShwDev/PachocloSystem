import type { Rol } from './tipos';

/**
 * Clave de `sessionStorage` con la sesión de esta aplicación. Igual que en la SPA
 * Angular, el token sobrevive a una recarga y se pierde al cerrar la pestaña.
 */
export const CLAVE_SESION = 'pachoclosystem.medicamentos-react.sesion';

/** Datos del usuario (`GET /api/auth/me`) que usa la interfaz. */
export interface Usuario {
  username: string;
  rol: Rol;
  /** Nombre del trabajador vinculado; `null` para el administrador. */
  nombreCompleto?: string | null;
  debeCambiarPassword: boolean;
}

export interface Sesion {
  token: string;
  /** Instante de expiración del token, en ms desde epoch. */
  expiraEn: number;
  usuario: Usuario;
}

const ROLES: readonly Rol[] = ['ADMIN', 'DOCTOR', 'ENFERMERO'];

export function esRol(valor: unknown): valor is Rol {
  return typeof valor === 'string' && (ROLES as readonly string[]).includes(valor);
}

/** Sesión guardada y vigente, o `null` si no hay, está corrupta o ya expiró. */
export function leerSesion(): Sesion | null {
  try {
    const guardada = JSON.parse(sessionStorage.getItem(CLAVE_SESION) ?? 'null') as {
      token?: unknown;
      expiraEn?: unknown;
      usuario?: { username?: unknown; rol?: unknown; nombreCompleto?: unknown } | null;
    } | null;
    const usuario = guardada?.usuario;
    if (
      typeof guardada?.token === 'string' &&
      typeof guardada.expiraEn === 'number' &&
      guardada.expiraEn > Date.now() &&
      typeof usuario?.username === 'string' &&
      esRol(usuario.rol)
    ) {
      return {
        token: guardada.token,
        expiraEn: guardada.expiraEn,
        usuario: {
          username: usuario.username,
          rol: usuario.rol,
          nombreCompleto:
            typeof usuario.nombreCompleto === 'string' ? usuario.nombreCompleto : null,
          debeCambiarPassword: false,
        },
      };
    }
  } catch {
    // JSON corrupto o almacenamiento no disponible: se empieza sin sesión.
  }
  return null;
}

export function guardarSesion(sesion: Sesion): void {
  try {
    sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
  } catch {
    // Sin almacenamiento (p. ej. bloqueado): la sesión dura hasta recargar.
  }
}

export function borrarSesion(): void {
  try {
    sessionStorage.removeItem(CLAVE_SESION);
  } catch {
    // Nada que borrar.
  }
}

/** Token vigente de la sesión guardada, o `null` si no hay o ya expiró. */
export function leerToken(): string | null {
  return leerSesion()?.token ?? null;
}

/** Rol del usuario de la sesión guardada, o `null` si no hay sesión. */
export function leerRol(): Rol | null {
  return leerSesion()?.usuario.rol ?? null;
}
