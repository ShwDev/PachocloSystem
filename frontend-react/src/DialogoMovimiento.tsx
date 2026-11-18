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

/** Devuelve el mensaje de error, o `null` si la cantidad es un entero válido. */
function validarCantidad(valor: string): string | null {
  const limpio = valor.trim();
  if (!limpio) {
    return 'La cantidad es obligatoria.';
  }
  if (!/^\d+$/.test(limpio)) {
    return 'La cantidad solo admite números enteros.';
  }
  const numero = Number(limpio);
  if (!Number.isSafeInteger(numero) || numero < 1) {
    return 'La cantidad debe ser un entero mayor o igual que 1.';
  }
  return null;
}

/** Entrada o salida de stock con validación en vivo de la cantidad. */
export function DialogoMovimiento({
  cliente,
  medicamento,
  tipo,
  claveIdempotencia,
  onCerrar,
  onHecho,
}: Props) {
  const [cantidad, setCantidad] = useState('1');
  const [errorServidor, setErrorServidor] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const esEntrada = tipo === 'entrada';
  const descripcion = `${medicamento.nombre} ${medicamento.concentracion}`;
  const errorCantidad = validarCantidad(cantidad);
  const valido = errorCantidad === null;

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (!valido) {
      return;
    }

    setEnviando(true);
    setErrorServidor(null);
    try {
      const actualizado = await cliente.moverStock(
        medicamento.idMedicamento,
        tipo,
        Number(cantidad.trim()),
        esEntrada ? undefined : claveIdempotencia,
      );
      onHecho(
        `${esEntrada ? 'Entrada' : 'Salida'} registrada: ${descripcion} queda con ` +
          `${actualizado.cantidadStock} unidades.`,
      );
    } catch (fallo) {
      setErrorServidor(mensajesDeError(fallo).join(' '));
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

      <form className="formulario" onSubmit={(evento) => void enviar(evento)} noValidate>
        <label htmlFor="cantidad-movimiento">Cantidad de unidades</label>
        <input
          id="cantidad-movimiento"
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          value={cantidad}
          aria-invalid={!valido ? true : undefined}
          onChange={(evento) => {
            setCantidad(evento.target.value);
            setErrorServidor(null);
          }}
          disabled={enviando}
        />

        {errorCantidad && (
          <p className="error-campo" role="alert">
            {errorCantidad}
          </p>
        )}
        {errorServidor && (
          <p className="error-servidor" role="alert">
            {errorServidor}
          </p>
        )}

        <div className="acciones-modal">
          <button type="button" className="boton secundario" onClick={onCerrar} disabled={enviando}>
            Cancelar
          </button>
          <button type="submit" className="boton primario" disabled={enviando || !valido}>
            {enviando ? 'Guardando…' : esEntrada ? 'Registrar entrada' : 'Registrar salida'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
