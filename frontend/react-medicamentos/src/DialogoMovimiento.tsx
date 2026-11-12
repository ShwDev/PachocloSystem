import { useState, type FormEvent } from 'react';
import { mensajesDeError, type ClienteMedicamentos } from './api';
import { Modal } from './Modal';
import type { Medicamento, TipoMovimiento } from './tipos';

interface Props {
  cliente: ClienteMedicamentos;
  medicamento: Medicamento;
  tipo: TipoMovimiento;
  claveIdempotencia: string;
  onCerrar: () => void;
  onHecho: (mensaje: string) => void;
}

/** Entrada o salida de stock con la validación local del formulario Angular. */
export function DialogoMovimiento({
  cliente,
  medicamento,
  tipo,
  claveIdempotencia,
  onCerrar,
  onHecho,
}: Props) {
  const [cantidad, setCantidad] = useState('1');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const esEntrada = tipo === 'entrada';
  const descripcion = `${medicamento.nombre} ${medicamento.concentracion}`;

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const valor = Number(cantidad);
    if (!/^\d+$/.test(cantidad.trim()) || !Number.isSafeInteger(valor) || valor < 1) {
      setError('La cantidad debe ser un entero mayor o igual que 1.');
      return;
    }

    setEnviando(true);
    setError(null);
    try {
      const actualizado = await cliente.moverStock(
        medicamento.idMedicamento,
        tipo,
        valor,
        esEntrada ? undefined : claveIdempotencia,
      );
      onHecho(
        `${esEntrada ? 'Entrada' : 'Salida'} registrada: ${descripcion} queda con ` +
          `${actualizado.cantidadStock} unidades.`,
      );
    } catch (fallo) {
      setError(mensajesDeError(fallo).join(' '));
      setEnviando(false);
    }
  }

  return (
    <Modal
      titulo={esEntrada ? 'Entrada de stock' : 'Salida de stock'}
      onCerrar={onCerrar}
      bloqueado={enviando}
    >
      <p className="modal-descripcion">
        <strong>{descripcion}</strong>
        <span>
          {medicamento.idMedicamento} · lote {medicamento.lote}
        </span>
      </p>
      <p className="modal-datos">
        Stock actual: <strong>{medicamento.cantidadStock}</strong> · mínimo{' '}
        {medicamento.stockMinimo}
      </p>

      <form className="formulario" onSubmit={(evento) => void enviar(evento)}>
        <label htmlFor="cantidad-movimiento">Cantidad de unidades</label>
        <input
          id="cantidad-movimiento"
          type="number"
          min="1"
          step="1"
          inputMode="numeric"
          autoFocus
          value={cantidad}
          onChange={(evento) => setCantidad(evento.target.value)}
          disabled={enviando}
        />

        {error && (
          <p className="error-campo" role="alert">
            {error}
          </p>
        )}

        <div className="acciones-modal">
          <button type="button" className="boton secundario" onClick={onCerrar} disabled={enviando}>
            Cancelar
          </button>
          <button type="submit" className="boton primario" disabled={enviando}>
            {enviando ? 'Guardando…' : esEntrada ? 'Registrar entrada' : 'Registrar salida'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
