import { describe, expect, it, vi } from 'vitest';
import { CABECERA_IDEMPOTENCIA, crearCliente } from './api';
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

function respuesta(cuerpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function clienteCon(fetchImpl: typeof fetch) {
  return crearCliente({
    apiBaseUrl: 'https://api.test/',
    obtenerToken: () => 'token-123',
    fetchImpl,
  });
}

describe('cliente de medicamentos', () => {
  it('lista con búsqueda y envía el token de la sesión', async () => {
    const fetchFalso = vi.fn(async () => respuesta([MEDICAMENTO])) as unknown as typeof fetch;
    const cliente = clienteCon(fetchFalso);

    await expect(cliente.listar('ibu prof')).resolves.toEqual([MEDICAMENTO]);

    const [url, opciones] = (fetchFalso as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('https://api.test/api/medicamentos?q=ibu%20prof');
    expect(new Headers((opciones as RequestInit).headers).get('Authorization')).toBe(
      'Bearer token-123',
    );
  });

  it('usa la ruta de por vencer con los días', async () => {
    const fetchFalso = vi.fn(async () => respuesta([])) as unknown as typeof fetch;
    const cliente = clienteCon(fetchFalso);

    await cliente.listarPorVencer(30);

    expect((fetchFalso as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0]).toBe(
      'https://api.test/api/medicamentos/por-vencer?dias=30',
    );
  });

  it('manda la clave de idempotencia solo en las salidas', async () => {
    const fetchFalso = vi.fn(async () => respuesta(MEDICAMENTO)) as unknown as typeof fetch;
    const cliente = clienteCon(fetchFalso);

    await cliente.moverStock('MED-1', 'salida', 3, 'clave-de-prueba-123');
    let opciones = (fetchFalso as unknown as ReturnType<typeof vi.fn>).mock
      .calls[0][1] as RequestInit;
    expect(new Headers(opciones.headers).get(CABECERA_IDEMPOTENCIA)).toBe('clave-de-prueba-123');
    expect(opciones.body).toBe(JSON.stringify({ cantidad: 3 }));

    await cliente.moverStock('MED-1', 'entrada', 3, 'clave-de-prueba-123');
    opciones = (fetchFalso as unknown as ReturnType<typeof vi.fn>).mock.calls[1][1] as RequestInit;
    expect(new Headers(opciones.headers).get(CABECERA_IDEMPOTENCIA)).toBeNull();
  });

  it('convierte el ErrorResponse del backend en ErrorApi', async () => {
    const fetchFalso = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            status: 409,
            error: 'Conflict',
            mensajes: ['Ya existe un medicamento con el mismo nombre.'],
          }),
          { status: 409, headers: { 'Content-Type': 'application/json' } },
        ),
    ) as unknown as typeof fetch;
    const cliente = clienteCon(fetchFalso);

    await expect(cliente.registrar(MEDICAMENTO)).rejects.toMatchObject({
      status: 409,
      mensajes: ['Ya existe un medicamento con el mismo nombre.'],
    });
  });

  it('sin conexión devuelve un ErrorApi de estado 0', async () => {
    const fetchFalso = vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }) as unknown as typeof fetch;
    const cliente = clienteCon(fetchFalso);

    await expect(cliente.listarVencidos()).rejects.toMatchObject({
      status: 0,
      mensajes: [
        'No se pudo conectar con el servidor. Compruebe su conexión y vuelva a intentarlo.',
      ],
    });
  });

  it('acepta respuestas sin cuerpo (204)', async () => {
    const fetchFalso = vi.fn(
      async () => new Response(null, { status: 204 }),
    ) as unknown as typeof fetch;
    const cliente = clienteCon(fetchFalso);

    await expect(cliente.eliminar('MED-1')).resolves.toBeUndefined();
  });
});
