import { leerToken } from './sesion';
import type { DatosMedicamento, Medicamento, MedicamentoRequest, TipoMovimiento } from './tipos';

/** Cabecera con la que el backend reconoce la repetición de una salida. */
export const CABECERA_IDEMPOTENCIA = 'Idempotency-Key';

const RUTA = '/api/medicamentos';

const MENSAJE_SIN_CONEXION =
  'No se pudo conectar con el servidor. Compruebe su conexión y vuelva a intentarlo.';
const MENSAJE_INESPERADO = 'Se produjo un error inesperado. Vuelva a intentarlo más tarde.';

/** Error HTTP de la API ya normalizado (equivalente a `ApiError` de Angular). */
export class ErrorApi extends Error {
  constructor(
    readonly status: number,
    readonly mensajes: readonly string[],
  ) {
    super(mensajes.join(' '));
    this.name = 'ErrorApi';
  }
}

/** Mensajes para mostrar de cualquier error; vacío si la petición se canceló. */
export function mensajesDeError(error: unknown): readonly string[] {
  if (error instanceof ErrorApi) {
    return error.mensajes;
  }
  if (error instanceof DOMException && error.name === 'AbortError') {
    return [];
  }
  return [MENSAJE_INESPERADO];
}

export function esCancelacion(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

/** Operaciones de `/api/medicamentos` que usa la interfaz React. */
export interface ClienteMedicamentos {
  listar(texto: string, signal?: AbortSignal): Promise<Medicamento[]>;
  listarStockBajo(signal?: AbortSignal): Promise<Medicamento[]>;
  listarPorVencer(dias: number, signal?: AbortSignal): Promise<Medicamento[]>;
  listarVencidos(signal?: AbortSignal): Promise<Medicamento[]>;
  registrar(datos: MedicamentoRequest): Promise<Medicamento>;
  editar(id: string, datos: DatosMedicamento): Promise<Medicamento>;
  eliminar(id: string): Promise<void>;
  moverStock(
    id: string,
    tipo: TipoMovimiento,
    cantidad: number,
    claveIdempotencia?: string,
  ): Promise<Medicamento>;
}

export interface OpcionesCliente {
  /** Origen de la API sin barra final, o vacío si está en el mismo origen. */
  apiBaseUrl: string;
  /** De dónde se lee el token en cada petición (por defecto, la sesión de la SPA). */
  obtenerToken?: () => string | null;
  /** Inyectable en los tests; en el navegador es `fetch`. */
  fetchImpl?: typeof fetch;
}

/**
 * Construye el cliente HTTP. La base de la API la pasa Angular por el atributo
 * `api-base` del custom element; el token se relee en cada petición para que una
 * sesión renovada en la SPA se use sin volver a montar la interfaz.
 */
export function crearCliente({
  apiBaseUrl,
  obtenerToken = leerToken,
  fetchImpl = fetch,
}: OpcionesCliente): ClienteMedicamentos {
  const base = apiBaseUrl.replace(/\/+$/, '');

  async function pedir<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
    const cabeceras = new Headers(opciones.headers);
    cabeceras.set('Accept', 'application/json');
    if (opciones.body !== undefined) {
      cabeceras.set('Content-Type', 'application/json');
    }
    const token = obtenerToken();
    if (token) {
      cabeceras.set('Authorization', `Bearer ${token}`);
    }

    let respuesta: Response;
    try {
      respuesta = await fetchImpl(`${base}${RUTA}${ruta}`, { ...opciones, headers: cabeceras });
    } catch (error) {
      if (esCancelacion(error)) {
        throw error;
      }
      throw new ErrorApi(0, [MENSAJE_SIN_CONEXION]);
    }

    if (respuesta.status === 204) {
      return undefined as T;
    }

    const cuerpo = await leerCuerpo(respuesta);
    if (!respuesta.ok) {
      throw new ErrorApi(respuesta.status, mensajesDeCuerpo(cuerpo) ?? [MENSAJE_SIN_CONEXION]);
    }
    return cuerpo as T;
  }

  return {
    listar(texto, signal) {
      const consulta = texto.trim();
      return pedir<Medicamento[]>(consulta ? `?q=${encodeURIComponent(consulta)}` : '', {
        signal,
      });
    },
    listarStockBajo(signal) {
      return pedir<Medicamento[]>('/stock-bajo', { signal });
    },
    listarPorVencer(dias, signal) {
      return pedir<Medicamento[]>(`/por-vencer?dias=${encodeURIComponent(String(dias))}`, {
        signal,
      });
    },
    listarVencidos(signal) {
      return pedir<Medicamento[]>('/vencidos', { signal });
    },
    registrar(datos) {
      return pedir<Medicamento>('', { method: 'POST', body: JSON.stringify(datos) });
    },
    editar(id, datos) {
      return pedir<Medicamento>(`/${encodeURIComponent(id)}`, {
        method: 'PUT',
        body: JSON.stringify(datos),
      });
    },
    eliminar(id) {
      return pedir<void>(`/${encodeURIComponent(id)}`, { method: 'DELETE' });
    },
    moverStock(id, tipo, cantidad, claveIdempotencia) {
      const ruta = tipo === 'entrada' ? 'entradas' : 'salidas';
      const cabeceras = new Headers();
      if (ruta === 'salidas' && claveIdempotencia) {
        cabeceras.set(CABECERA_IDEMPOTENCIA, claveIdempotencia);
      }
      return pedir<Medicamento>(`/${encodeURIComponent(id)}/${ruta}`, {
        method: 'POST',
        body: JSON.stringify({ cantidad }),
        headers: cabeceras,
      });
    },
  };
}

async function leerCuerpo(respuesta: Response): Promise<unknown> {
  const texto = await respuesta.text();
  if (!texto) {
    return null;
  }
  try {
    return JSON.parse(texto) as unknown;
  } catch {
    return null;
  }
}

function mensajesDeCuerpo(cuerpo: unknown): readonly string[] | null {
  const mensajes = (cuerpo as { mensajes?: unknown } | null)?.mensajes;
  if (Array.isArray(mensajes) && mensajes.every((m) => typeof m === 'string')) {
    return mensajes as string[];
  }
  return null;
}
