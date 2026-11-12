import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './estilos.css';

/**
 * Origen de la API. Vacío (por defecto) cuando la app y la API comparten origen:
 * en desarrollo Vite reenvía `/api` y en Docker lo hace nginx. Para un despliegue
 * en otro dominio se fija `VITE_API_BASE_URL` al compilar.
 */
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').trim();

const raiz = document.getElementById('root');
if (!raiz) {
  throw new Error('Falta el elemento #root en index.html');
}

createRoot(raiz).render(
  <StrictMode>
    <App apiBaseUrl={apiBaseUrl} />
  </StrictMode>,
);
