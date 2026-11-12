import { useState, type FormEvent } from 'react';
import { mensajesDeError } from './api';
import { iniciarSesion } from './auth';
import type { Sesion } from './sesion';

interface Props {
  apiBaseUrl: string;
  /** Por qué se cerró la sesión anterior (expiró, token inválido...). */
  aviso: string | null;
  onSesion: (sesion: Sesion) => void;
  fetchImpl?: typeof fetch;
}

/** Pantalla de inicio de sesión con las mismas cuentas que la SPA Angular. */
export function Login({ apiBaseUrl, aviso, onSesion, fetchImpl }: Props) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [errores, setErrores] = useState<readonly string[]>([]);

  const completo = username.trim() !== '' && password !== '';

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (!completo || enviando) {
      return;
    }
    setEnviando(true);
    setErrores([]);
    try {
      const sesion = await iniciarSesion(
        apiBaseUrl,
        { username: username.trim(), password },
        fetchImpl,
      );
      onSesion(sesion);
    } catch (error) {
      setErrores(mensajesDeError(error));
      setPassword('');
      setEnviando(false);
    }
  }

  return (
    <main className="pagina-login">
      <form className="tarjeta-login" onSubmit={(evento) => void enviar(evento)} noValidate>
        <h1>
          Medicamentos <span className="etiqueta-framework">React</span>
        </h1>
        <p className="subtitulo">Inicie sesión con su cuenta de PachocloSystem.</p>

        {aviso && errores.length === 0 && (
          <div className="aviso-info" role="status">
            {aviso}
          </div>
        )}
        {errores.length > 0 && (
          <div className="aviso-servicio" role="alert">
            <div className="aviso-texto">
              {errores.map((mensaje) => (
                <p key={mensaje}>{mensaje}</p>
              ))}
            </div>
          </div>
        )}

        <label className="campo">
          <span>Usuario</span>
          <input
            type="text"
            autoComplete="username"
            value={username}
            onChange={(evento) => setUsername(evento.target.value)}
            disabled={enviando}
            autoFocus
          />
        </label>
        <label className="campo">
          <span>Contraseña</span>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(evento) => setPassword(evento.target.value)}
            disabled={enviando}
          />
        </label>

        <button type="submit" className="boton primario ancho" disabled={!completo || enviando}>
          {enviando ? 'Ingresando…' : 'Iniciar sesión'}
        </button>
      </form>
    </main>
  );
}
