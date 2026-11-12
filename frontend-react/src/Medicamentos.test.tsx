import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, type ClienteMedicamentos } from './api';
import { Medicamentos } from './Medicamentos';
import type { Medicamento } from './tipos';

const VIGENTE: Medicamento = {
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
  stockBajo: true,
  vencido: false,
};

const VENCIDO: Medicamento = {
  ...VIGENTE,
  idMedicamento: 'MED-2',
  nombre: 'Jarabe',
  concentracion: '120 ml',
  lote: 'L-02',
  cantidadStock: 2,
  fechaVencimiento: '2025-12-31',
  stockBajo: false,
  vencido: true,
};

function crearClienteFalso(overrides: Partial<ClienteMedicamentos> = {}): ClienteMedicamentos {
  return {
    listar: vi.fn(async () => []),
    listarStockBajo: vi.fn(async () => []),
    listarPorVencer: vi.fn(async () => []),
    listarVencidos: vi.fn(async () => []),
    registrar: vi.fn(async () => VIGENTE),
    editar: vi.fn(async () => VIGENTE),
    eliminar: vi.fn(async () => undefined),
    moverStock: vi.fn(async () => VIGENTE),
    ...overrides,
  };
}

afterEach(cleanup);

describe('interfaz React de medicamentos', () => {
  it('muestra la lista con stock, vencimiento e insignias', async () => {
    const cliente = crearClienteFalso({
      listar: vi.fn(async () => [VIGENTE, VENCIDO]),
    });

    render(<Medicamentos cliente={cliente} rol="ADMIN" />);

    expect(await screen.findByText('Ibuprofeno 400 mg')).toBeTruthy();
    expect(screen.getByText('Jarabe 120 ml')).toBeTruthy();
    expect(screen.getByText('31/01/2027')).toBeTruthy();
    const tabla = screen.getByRole('table');
    expect(within(tabla).getByText('Stock bajo')).toBeTruthy();
    expect(within(tabla).getByText('Vencido')).toBeTruthy();
  });

  it('muestra el error del servicio y permite reintentar', async () => {
    const listar = vi
      .fn<ClienteMedicamentos['listar']>()
      .mockRejectedValueOnce(
        new ErrorApi(503, [
          'El servicio no puede acceder a sus datos en este momento. Vuelva a intentarlo más tarde.',
        ]),
      )
      .mockResolvedValueOnce([VIGENTE]);
    render(<Medicamentos cliente={crearClienteFalso({ listar })} rol="ADMIN" />);

    expect(
      await screen.findByText(
        'El servicio no puede acceder a sus datos en este momento. Vuelva a intentarlo más tarde.',
      ),
    ).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Ibuprofeno 400 mg')).toBeTruthy();
  });

  it('oculta las acciones que el rol no puede hacer', async () => {
    const cliente = crearClienteFalso({ listar: vi.fn(async () => [VIGENTE]) });

    const { unmount } = render(<Medicamentos cliente={cliente} rol="DOCTOR" />);
    expect(await screen.findByText('Ibuprofeno 400 mg')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^Salida de stock/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /^Editar/ })).toBeNull();
    unmount();

    render(
      <Medicamentos
        cliente={crearClienteFalso({ listar: vi.fn(async () => [VIGENTE]) })}
        rol="ENFERMERO"
      />,
    );
    expect(
      await screen.findByRole('button', { name: 'Salida de stock de Ibuprofeno 400 mg' }),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^Entrada de stock/ })).toBeNull();
  });

  it('registra una salida con clave de idempotencia', async () => {
    const moverStock = vi.fn(async () => ({ ...VIGENTE, cantidadStock: 7 }));
    const cliente = crearClienteFalso({
      listar: vi.fn(async () => [VIGENTE]),
      moverStock,
    });

    render(<Medicamentos cliente={cliente} rol="ADMIN" />);
    await screen.findByText('Ibuprofeno 400 mg');

    fireEvent.click(screen.getByRole('button', { name: 'Salida de stock de Ibuprofeno 400 mg' }));
    const dialogo = await screen.findByRole('dialog');
    fireEvent.change(within(dialogo).getByLabelText('Cantidad de unidades'), {
      target: { value: '3' },
    });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Registrar salida' }));

    await waitFor(() => expect(moverStock).toHaveBeenCalledTimes(1));
    expect(moverStock).toHaveBeenCalledWith(
      'MED-1',
      'salida',
      3,
      expect.stringMatching(/^[0-9a-f-]{16,}$/i),
    );
    expect(
      await screen.findByText('Salida registrada: Ibuprofeno 400 mg queda con 7 unidades.'),
    ).toBeTruthy();
  });

  it('filtra con el buscador tras la espera de escritura', async () => {
    const listar = vi.fn<ClienteMedicamentos['listar']>(async () => [VIGENTE]);
    render(<Medicamentos cliente={crearClienteFalso({ listar })} rol="ADMIN" />);
    await screen.findByText('Ibuprofeno 400 mg');

    fireEvent.change(screen.getByLabelText('Buscar por ID, nombre o principio activo'), {
      target: { value: 'ibu' },
    });

    await waitFor(() => expect(listar).toHaveBeenLastCalledWith('ibu', expect.any(AbortSignal)));
  });
});
