import { useCallback, useEffect, useMemo, useState } from 'react';
import { crearCliente } from './api';
import { Login } from './Login';
import { Medicamentos } from './Medicamentos';
import { borrarSesion, guardarSesion, leerSesion, type Sesion } from './sesion';

export const MENSAJE_SESION_EXPIRADA = 'Su sesión expiró. Inicie sesión de nuevo.';
export const MENSAJE_SESION_INVALIDA = 'Su sesión ya no es válida. Inicie sesión de nuevo.';

/** `setTimeout` no admite esperas mayores (unos 24,8 días). */
const ESPERA_MAXIMA_MS = 2_147_483_647;

const ETIQUETAS_ROL = { ADMIN: 'Administrador', DOCTOR: 'Doctor', ENFERMERO: 'Enfermero' };

interface Props {
  /** Origen de la API sin barra final, o vacío si está en el mismo origen. */
  apiBaseUrl: string;
  /** Inyectable en los tests; en el navegador es `fetch`. */
  fetchImpl?: typeof fetch;
}

/**
 * Aplicación independiente de Medicamentos: login propio contra PachocloSystem y,
 * con sesión, el inventario. El token se guarda en `sessionStorage` y la sesión se
 * cierra sola al expirar o si la API responde 401.
 */
export function App({ apiBaseUrl, fetchImpl }: Props) {
  const [sesion, setSesion] = useState<Sesion | null>(leerSesion);
  const [aviso, setAviso] = useState<string | null>(null);

  const cerrarSesion = useCallback((motivo: string | null = null) => {
    borrarSesion();
    setSesion(null);
    setAviso(motivo);
  }, []);

  useEffect(() => {
    if (!sesion) {
      return;
    }
    const restante = Math.min(Math.max(sesion.expiraEn - Date.now(), 0), ESPERA_MAXIMA_MS);
    const temporizador = window.setTimeout(() => cerrarSesion(MENSAJE_SESION_EXPIRADA), restante);
    return () => window.clearTimeout(temporizador);
  }, [sesion, cerrarSesion]);

  const cliente = useMemo(
    () =>
      sesion
        ? crearCliente({
            apiBaseUrl,
            obtenerToken: () => sesion.token,
            alNoAutorizado: () => cerrarSesion(MENSAJE_SESION_INVALIDA),
            fetchImpl,
          })
        : null,
    [apiBaseUrl, sesion, cerrarSesion, fetchImpl],
  );

  if (!sesion || !cliente) {
    return (
      <Login
        apiBaseUrl={apiBaseUrl}
        aviso={aviso}
        fetchImpl={fetchImpl}
        onSesion={(nueva) => {
          guardarSesion(nueva);
          setAviso(null);
          setSesion(nueva);
        }}
      />
    );
  }

  const { usuario } = sesion;
  return (
    <>
      <header className="barra-app">
        <span className="marca">PachocloSystem · Medicamentos</span>
        <div className="usuario">
          <span>
            {usuario.nombreCompleto || usuario.username}{' '}
            <span className="secundario">({ETIQUETAS_ROL[usuario.rol]})</span>
          </span>
          <button type="button" className="boton secundario" onClick={() => cerrarSesion()}>
            Cerrar sesión
          </button>
        </div>
      </header>
      <main className="contenido">
        <Medicamentos cliente={cliente} rol={usuario.rol} />
      </main>
    </>
  );
}
