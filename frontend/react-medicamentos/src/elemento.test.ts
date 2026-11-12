import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import './elemento';
import { CLAVE_SESION } from './sesion';
import type { Medicamento } from './tipos';

const ETIQUETA = 'pachoclo-medicamentos-react';

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

describe('custom element de medicamentos', () => {
  beforeEach(() => {
    sessionStorage.setItem(
      CLAVE_SESION,
      JSON.stringify({
        token: 'jwt-de-prueba',
        expiraEn: Date.now() + 60_000,
        usuario: { rol: 'ADMIN' },
      }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(JSON.stringify([MEDICAMENTO]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
      ),
    );
  });

  afterEach(() => {
    document.body.replaceChildren();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });

  it('se registra y monta la interfaz React en el Shadow DOM', async () => {
    const elemento = document.createElement(ETIQUETA);
    elemento.setAttribute('api-base', 'https://api.test');
    document.body.appendChild(elemento);

    await vi.waitFor(() => {
      expect(elemento.shadowRoot?.textContent).toContain('Ibuprofeno 400 mg');
    });

    expect(elemento.shadowRoot?.querySelector('style')?.textContent).toContain('.medicamentos');
    expect(fetch).toHaveBeenCalledTimes(1);
    const [url, opciones] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('https://api.test/api/medicamentos');
    expect(new Headers((opciones as RequestInit).headers).get('Authorization')).toBe(
      'Bearer jwt-de-prueba',
    );
  });
});
