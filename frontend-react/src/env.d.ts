/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origen de la API (p. ej. `https://api.ejemplo.com`); vacío si es el mismo origen. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
