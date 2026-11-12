import { useState, type FormEvent, type ReactNode } from 'react';
import { mensajesDeError, type ClienteMedicamentos } from './api';
import { Modal } from './Modal';
import {
  ETIQUETAS_PRESENTACION,
  PRESENTACIONES,
  type DatosMedicamento,
  type Medicamento,
  type Presentacion,
} from './tipos';

interface Props {
  cliente: ClienteMedicamentos;
  /** Sin medicamento: alta. Con medicamento: edición (el stock no se toca). */
  medicamento?: Medicamento;
  onCerrar: () => void;
  onHecho: (mensaje: string) => void;
}

interface Campos {
  nombre: string;
  principioActivo: string;
  presentacion: Presentacion | '';
  concentracion: string;
  laboratorio: string;
  lote: string;
  cantidadStock: string;
  stockMinimo: string;
  fechaVencimiento: string;
  ubicacion: string;
}

type Errores = Partial<Record<keyof Campos, string>>;

function camposIniciales(medicamento?: Medicamento): Campos {
  return {
    nombre: medicamento?.nombre ?? '',
    principioActivo: medicamento?.principioActivo ?? '',
    presentacion: medicamento?.presentacion ?? '',
    concentracion: medicamento?.concentracion ?? '',
    laboratorio: medicamento?.laboratorio ?? '',
    lote: medicamento?.lote ?? '',
    cantidadStock: '',
    stockMinimo: medicamento ? String(medicamento.stockMinimo) : '',
    fechaVencimiento: medicamento?.fechaVencimiento ?? '',
    ubicacion: medicamento?.ubicacion ?? '',
  };
}

/** Mismas reglas que `CrearMedicamentoRequest` del backend. */
function validar(campos: Campos, esAlta: boolean): Errores {
  const errores: Errores = {};
  const texto = (campo: keyof Campos, maximo: number, obligatorio: string, largo: string) => {
    const valor = campos[campo].trim();
    if (!valor) {
      errores[campo] = obligatorio;
    } else if (valor.length > maximo) {
      errores[campo] = largo;
    }
  };

  texto('nombre', 80, 'El nombre es obligatorio.', 'El nombre no puede superar 80 caracteres.');
  texto(
    'principioActivo',
    80,
    'El principio activo es obligatorio.',
    'El principio activo no puede superar 80 caracteres.',
  );
  if (!campos.presentacion) {
    errores.presentacion = 'La presentación es obligatoria.';
  } else if (!PRESENTACIONES.includes(campos.presentacion)) {
    errores.presentacion = 'La presentación no es válida.';
  }
  texto(
    'concentracion',
    40,
    'La concentración es obligatoria.',
    'La concentración no puede superar 40 caracteres.',
  );
  texto(
    'laboratorio',
    80,
    'El laboratorio es obligatorio.',
    'El laboratorio no puede superar 80 caracteres.',
  );
  texto('lote', 40, 'El lote es obligatorio.', 'El lote no puede superar 40 caracteres.');
  texto(
    'ubicacion',
    80,
    'La ubicación de almacenamiento es obligatoria.',
    'La ubicación no puede superar 80 caracteres.',
  );

  const entero = (campo: 'stockMinimo' | 'cantidadStock', mensaje: string) => {
    const valor = campos[campo].trim();
    if (!valor) {
      errores[campo] = mensaje;
    } else if (!/^\d+$/.test(valor) || Number(valor) > 1_000_000) {
      errores[campo] = `${mensaje} (entre 0 y 1000000)`;
    }
  };
  entero('stockMinimo', 'El stock mínimo debe ser un entero.');
  if (esAlta) {
    entero('cantidadStock', 'La cantidad en stock debe ser un entero.');
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(campos.fechaVencimiento)) {
    errores.fechaVencimiento = 'La fecha de vencimiento es obligatoria.';
  }

  return errores;
}

/** Alta y edición de medicamentos desde la interfaz React. */
export function DialogoMedicamento({ cliente, medicamento, onCerrar, onHecho }: Props) {
  const esAlta = medicamento === undefined;
  const [campos, setCampos] = useState<Campos>(() => camposIniciales(medicamento));
  const [errores, setErrores] = useState<Errores>({});
  const [errorServidor, setErrorServidor] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function cambiar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((actual) => ({ ...actual, [campo]: valor }));
  }

  async function guardar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const encontrados = validar(campos, esAlta);
    setErrores(encontrados);
    setErrorServidor(null);
    if (Object.keys(encontrados).length > 0) {
      return;
    }

    const datos: DatosMedicamento = {
      nombre: campos.nombre.trim(),
      principioActivo: campos.principioActivo.trim(),
      presentacion: campos.presentacion as Presentacion,
      concentracion: campos.concentracion.trim(),
      laboratorio: campos.laboratorio.trim(),
      lote: campos.lote.trim(),
      stockMinimo: Number(campos.stockMinimo),
      fechaVencimiento: campos.fechaVencimiento,
      ubicacion: campos.ubicacion.trim(),
    };

    setEnviando(true);
    try {
      const guardado = medicamento
        ? await cliente.editar(medicamento.idMedicamento, datos)
        : await cliente.registrar({ ...datos, cantidadStock: Number(campos.cantidadStock) });
      onHecho(
        `Medicamento ${guardado.idMedicamento} ${medicamento ? 'actualizado' : 'registrado'}.`,
      );
    } catch (fallo) {
      setErrorServidor(mensajesDeError(fallo).join(' '));
      setEnviando(false);
    }
  }

  const titulo = medicamento
    ? `Editar medicamento ${medicamento.idMedicamento}`
    : 'Nuevo medicamento';

  return (
    <Modal titulo={titulo} onCerrar={onCerrar} bloqueado={enviando}>
      <form className="formulario" onSubmit={(evento) => void guardar(evento)} noValidate>
        {errorServidor && (
          <p className="error-servidor" role="alert">
            {errorServidor}
          </p>
        )}

        <div className="fila-campos">
          <Campo etiqueta="Nombre comercial" error={errores.nombre}>
            <input
              autoFocus
              maxLength={80}
              value={campos.nombre}
              onChange={(e) => cambiar('nombre', e.target.value)}
              disabled={enviando}
            />
          </Campo>
          <Campo etiqueta="Principio activo" error={errores.principioActivo}>
            <input
              maxLength={80}
              value={campos.principioActivo}
              onChange={(e) => cambiar('principioActivo', e.target.value)}
              disabled={enviando}
            />
          </Campo>
        </div>

        <div className="fila-campos">
          <Campo etiqueta="Presentación" error={errores.presentacion}>
            <select
              value={campos.presentacion}
              onChange={(e) => cambiar('presentacion', e.target.value as Presentacion)}
              disabled={enviando}
            >
              <option value="" disabled>
                Seleccione…
              </option>
              {PRESENTACIONES.map((presentacion) => (
                <option key={presentacion} value={presentacion}>
                  {ETIQUETAS_PRESENTACION[presentacion]}
                </option>
              ))}
            </select>
          </Campo>
          <Campo etiqueta="Concentración" error={errores.concentracion}>
            <input
              maxLength={40}
              placeholder="p. ej. 500 mg"
              value={campos.concentracion}
              onChange={(e) => cambiar('concentracion', e.target.value)}
              disabled={enviando}
            />
          </Campo>
        </div>

        <div className="fila-campos">
          <Campo etiqueta="Laboratorio" error={errores.laboratorio}>
            <input
              maxLength={80}
              value={campos.laboratorio}
              onChange={(e) => cambiar('laboratorio', e.target.value)}
              disabled={enviando}
            />
          </Campo>
          <Campo etiqueta="Lote" error={errores.lote}>
            <input
              maxLength={40}
              value={campos.lote}
              onChange={(e) => cambiar('lote', e.target.value)}
              disabled={enviando}
            />
          </Campo>
        </div>

        <div className="fila-campos">
          {medicamento ? (
            <Campo etiqueta="Stock actual">
              <input value={medicamento.cantidadStock} disabled readOnly />
              <span className="ayuda">Solo cambia con entradas y salidas.</span>
            </Campo>
          ) : (
            <Campo etiqueta="Stock inicial" error={errores.cantidadStock}>
              <input
                type="number"
                min="0"
                max="1000000"
                step="1"
                value={campos.cantidadStock}
                onChange={(e) => cambiar('cantidadStock', e.target.value)}
                disabled={enviando}
              />
            </Campo>
          )}
          <Campo etiqueta="Stock mínimo" error={errores.stockMinimo}>
            <input
              type="number"
              min="0"
              max="1000000"
              step="1"
              value={campos.stockMinimo}
              onChange={(e) => cambiar('stockMinimo', e.target.value)}
              disabled={enviando}
            />
          </Campo>
        </div>

        <div className="fila-campos">
          <Campo etiqueta="Fecha de vencimiento" error={errores.fechaVencimiento}>
            <input
              type="date"
              value={campos.fechaVencimiento}
              onChange={(e) => cambiar('fechaVencimiento', e.target.value)}
              disabled={enviando}
            />
          </Campo>
          <Campo etiqueta="Ubicación" error={errores.ubicacion}>
            <input
              maxLength={80}
              placeholder="p. ej. Farmacia, estante A"
              value={campos.ubicacion}
              onChange={(e) => cambiar('ubicacion', e.target.value)}
              disabled={enviando}
            />
          </Campo>
        </div>

        <div className="acciones-modal">
          <button type="button" className="boton secundario" onClick={onCerrar} disabled={enviando}>
            Cancelar
          </button>
          <button type="submit" className="boton primario" disabled={enviando}>
            {enviando ? 'Guardando…' : medicamento ? 'Guardar cambios' : 'Registrar'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function Campo({
  etiqueta,
  error,
  children,
}: {
  etiqueta: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="campo">
      <span>{etiqueta}</span>
      {children}
      {error && (
        <span className="error-campo" role="alert">
          {error}
        </span>
      )}
    </label>
  );
}
