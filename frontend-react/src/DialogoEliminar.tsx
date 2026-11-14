import { useState } from 'react';
import { mensajesDeError, type ClienteMedicamentos } from './api';
import { Modal } from './Modal';
import type { Medicamento } from './tipos';

interface Props {
  cliente: ClienteMedicamentos;
  medicamento: Medicamento;
  onCerrar: () => void;
  onHecho: (mensaje: string) => void;
}

/** Confirmación de baja, con el mismo texto que la SPA Angular. */
export function DialogoEliminar({ cliente, medicamento, onCerrar, onHecho }: Props) {
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function eliminar() {
    setEnviando(true);
    setError(null);
    try {
      await cliente.eliminar(medicamento.idMedicamento);
      onHecho(`Medicamento ${medicamento.idMedicamento} eliminado.`);
    } catch (fallo) {
      setError(mensajesDeError(fallo).join(' '));
      setEnviando(false);
    }
  }

  return (
    <Modal titulo="Eliminar medicamento" onCerrar={onCerrar} bloqueado={enviando}>
      <p className="modal-descripcion">
        Se eliminará <strong>{`${medicamento.nombre} ${medicamento.concentracion}`}</strong> (
        {medicamento.idMedicamento}, lote {medicamento.lote}) del inventario. Se borra
        definitivamente de la base de datos; esta acción no se puede deshacer.
      </p>

      {error && (
        <p className="error-servidor" role="alert">
          {error}
        </p>
      )}

      <div className="acciones-modal">
        <button type="button" className="boton secundario" onClick={onCerrar} disabled={enviando}>
          Cancelar
        </button>
        <button
          type="button"
          className="boton peligro"
          onClick={() => void eliminar()}
          disabled={enviando}
        >
          {enviando ? 'Eliminando…' : 'Eliminar'}
        </button>
      </div>
    </Modal>
  );
}
