import { ErrorApi, solicitar, urlApi } from './api';
import { esRol, type Sesion, type Usuario } from './sesion';

export const URL_LOGIN = '/api/auth/login';
export const URL_USUARIO_ACTUAL = '/api/auth/me';

export const MENSAJE_CAMBIAR_PASSWORD =
  'Debe cambiar su contraseña en la aplicación principal de PachocloSystem antes de usar esta interfaz.';

interface LoginResponse {
  token: string;
  expiraEnSegundos: number;
}

export interface Credenciales {
  username: string;
  password: string;
}

/**
 * Inicia sesión contra PachocloSystem (`POST /api/auth/login`) y carga el usuario
 * actual (`GET /api/auth/me`). Si el usuario debe cambiar su contraseña, el
 * backend le bloquea el resto de la API, así que se rechaza con un mensaje claro.
 */
export async function iniciarSesion(
  apiBaseUrl: string,
  credenciales: Credenciales,
  fetchImpl: typeof fetch = fetch,
): Promise<Sesion> {
  const login = await solicitar<LoginResponse>(
    urlApi(apiBaseUrl, URL_LOGIN),
    { method: 'POST', body: JSON.stringify(credenciales) },
    { fetchImpl },
  );
  const expiraEn = Date.now() + login.expiraEnSegundos * 1000;

  const usuario = await solicitar<Usuario>(
    urlApi(apiBaseUrl, URL_USUARIO_ACTUAL),
    {},
    { token: login.token, fetchImpl },
  );
  if (usuario.debeCambiarPassword) {
    throw new ErrorApi(403, [MENSAJE_CAMBIAR_PASSWORD]);
  }
  if (!esRol(usuario.rol)) {
    throw new ErrorApi(403, ['Su usuario tiene un rol que esta interfaz no reconoce.']);
  }

  return {
    token: login.token,
    expiraEn,
    usuario: {
      username: usuario.username,
      rol: usuario.rol,
      nombreCompleto: usuario.nombreCompleto ?? null,
      debeCambiarPassword: false,
    },
  };
}
