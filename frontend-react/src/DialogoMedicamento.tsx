import { useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { mensajesDeError, type ClienteMedicamentos } from './api';
import { Modal } from './Modal';
import { SelectorDesplegable } from './SelectorDesplegable';
import { SelectorFecha } from './SelectorFecha';
import {
  ETIQUETAS_PRESENTACION,
  ETIQUETAS_UNIDAD_CONCENTRACION,
  PRESENTACIONES,
  UNIDADES_CONCENTRACION,
  type DatosMedicamento,
  type Medicamento,
  type Presentacion,
  type UnidadConcentracion,
} from './tipos';

const OPCIONES_PRESENTACION = PRESENTACIONES.map((p) => ({
  valor: p,
  etiqueta: ETIQUETAS_PRESENTACION[p],
}));

const OPCIONES_UNIDAD = UNIDADES_CONCENTRACION.map((u) => ({
  valor: u,
  etiqueta: ETIQUETAS_UNIDAD_CONCENTRACION[u],
}));

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
  concentracionValor: string;
  concentracionUnidad: UnidadConcentracion | '';
  laboratorio: string;
  lote: string;
  cantidadStock: string;
  stockMinimo: string;
  fechaVencimiento: string;
  ubicacion: string;
}

type Errores = Partial<Record<keyof Campos, string>>;
type Tocados = Partial<Record<keyof Campos, boolean>>;

/** Lote alfanumérico: letras, números, espacios y separadores habituales (L-2026-01). */
const PATRON_LOTE = /^[0-9A-Za-zÁÉÍÓÚÜÑáéíóúüñ][0-9A-Za-zÁÉÍÓÚÜÑáéíóúüñ ._/-]*$/;
/** Texto legible: al menos una letra (evita nombres o ubicaciones solo numéricos). */
const PATRON_TEXTO = /[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]/;
/** Valor de la concentración: dígitos con un separador decimal opcional (500, 0,5). */
const PATRON_VALOR_CONCENTRACION = /^\d+(?:[.,]\d+)?$/;

/** Quita los caracteres especiales del valor y deja un único separador decimal. */
function limpiarValorConcentracion(texto: string): string {
  const limpio = texto.replace(/[^\d.,]/g, '');
  const separador = limpio.search(/[.,]/);
  if (separador === -1) {
    return limpio;
  }
  return limpio.slice(0, separador + 1) + limpio.slice(separador + 1).replace(/[.,]/g, '');
}

/** Separa una concentración guardada («500 mg») en valor y unidad conocida. */
function descomponerConcentracion(texto: string): {
  valor: string;
  unidad: UnidadConcentracion | '';
} {
  const coincidencia = /^\s*(\d+(?:[.,]\d+)?)\s*(\S.*?)\s*$/.exec(texto);
  if (!coincidencia) {
    return { valor: '', unidad: '' };
  }
  const unidad = UNIDADES_CONCENTRACION.find(
    (candidata) => candidata.toLowerCase() === coincidencia[2].toLowerCase(),
  );
  return { valor: coincidencia[1], unidad: unidad ?? '' };
}

/** Indica si una concentración guardada se puede editar con valor y unidad de la lista. */
function esConcentracionValida(texto: string): boolean {
  const { valor, unidad } = descomponerConcentracion(texto);
  return valor !== '' && unidad !== '';
}

function componerConcentracion(valor: string, unidad: UnidadConcentracion | ''): string {
  return `${valor.trim()} ${unidad}`.trim();
}

function camposIniciales(medicamento?: Medicamento): Campos {
  const concentracion = descomponerConcentracion(medicamento?.concentracion ?? '');
  return {
    nombre: medicamento?.nombre ?? '',
    principioActivo: medicamento?.principioActivo ?? '',
    presentacion: medicamento?.presentacion ?? '',
    concentracionValor: concentracion.valor,
    concentracionUnidad: concentracion.unidad,
    laboratorio: medicamento?.laboratorio ?? '',
    lote: medicamento?.lote ?? '',
    cantidadStock: '',
    stockMinimo: medicamento ? String(medicamento.stockMinimo) : '',
    fechaVencimiento: medicamento?.fechaVencimiento ?? '',
    ubicacion: medicamento?.ubicacion ?? '',
  };
}

/** Comprueba que una fecha AAAA-MM-DD exista realmente (rechaza 2026-02-31). */
function esFechaValida(valor: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(valor)) {
    return false;
  }
  const [anio, mes, dia] = valor.split('-').map(Number);
  const fecha = new Date(Date.UTC(anio, mes - 1, dia));
  const opcionesPresentacion = useMemo(
    () => PRESENTACIONES.map((p) => ({ valor: p, etiqueta: ETIQUETAS_PRESENTACION[p] })),
    [],
  );
  const opcionesUnidad = useMemo(
    () =>
      UNIDADES_CONCENTRACION.map((u) => ({
        valor: u,
        etiqueta: ETIQUETAS_UNIDAD_CONCENTRACION[u],
      })),
    [],
  );

  return (
    fecha.getUTCFullYear() === anio && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === dia
  );
}

/** Mismas reglas que `CrearMedicamentoRequest` del backend, con tipos estrictos. */
function validar(campos: Campos, esAlta: boolean): Errores {
  const errores: Errores = {};

  const texto = (
    campo: 'nombre' | 'principioActivo' | 'laboratorio' | 'ubicacion',
    maximo: number,
    obligatorio: string,
    largo: string,
  ) => {
    const valor = campos[campo].trim();
    if (!valor) {
      errores[campo] = obligatorio;
    } else if (valor.length > maximo) {
      errores[campo] = largo;
    } else if (!PATRON_TEXTO.test(valor)) {
      errores[campo] = 'Escriba un texto con al menos una letra.';
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

  const valorConcentracion = campos.concentracionValor.trim();
  if (!valorConcentracion) {
    errores.concentracionValor = 'La concentración es obligatoria.';
  } else if (!PATRON_VALOR_CONCENTRACION.test(valorConcentracion)) {
    errores.concentracionValor = 'La concentración solo admite números (p. ej. 500).';
  } else {
    const numero = Number(valorConcentracion.replace(',', '.'));
    if (numero <= 0 || numero > 1_000_000) {
      errores.concentracionValor = 'La concentración debe ser mayor que 0 y máximo 1000000.';
    }
  }
  if (!campos.concentracionUnidad) {
    errores.concentracionUnidad = 'Seleccione una unidad de medida de la lista.';
  } else if (!UNIDADES_CONCENTRACION.includes(campos.concentracionUnidad)) {
    errores.concentracionUnidad = 'La unidad de medida no es válida.';
  }

  texto(
    'laboratorio',
    80,
    'El laboratorio es obligatorio.',
    'El laboratorio no puede superar 80 caracteres.',
  );

  const lote = campos.lote.trim();
  if (!lote) {
    errores.lote = 'El lote es obligatorio.';
  } else if (lote.length > 40) {
    errores.lote = 'El lote no puede superar 40 caracteres.';
  } else if (!PATRON_LOTE.test(lote)) {
    errores.lote = 'El lote solo admite letras, números, espacios y guiones (p. ej. L-2026-01).';
  }

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
      errores[campo] = `${mensaje} Debe ser un número entero entre 0 y 1000000.`;
    }
  };
  entero('stockMinimo', 'El stock mínimo debe ser un entero.');
  if (esAlta) {
    entero('cantidadStock', 'La cantidad en stock debe ser un entero.');
  }

  if (!campos.fechaVencimiento) {
    errores.fechaVencimiento = 'La fecha de vencimiento es obligatoria.';
  } else if (!esFechaValida(campos.fechaVencimiento)) {
    errores.fechaVencimiento = 'La fecha de vencimiento no es válida.';
  }

  return errores;
}

/** Alta y edición de medicamentos desde la interfaz React. */
export function DialogoMedicamento({ cliente, medicamento, onCerrar, onHecho }: Props) {
  const esAlta = medicamento === undefined;
  const [campos, setCampos] = useState<Campos>(() => camposIniciales(medicamento));
  const [tocados, setTocados] = useState<Tocados>({});
  const [errorServidor, setErrorServidor] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const errores = useMemo(() => validar(campos, esAlta), [campos, esAlta]);
  const valido = Object.keys(errores).length === 0;
  const errorDe = (campo: keyof Campos) => (tocados[campo] ? errores[campo] : undefined);
  const errorConcentracion = errorDe('concentracionValor') ?? errorDe('concentracionUnidad');
  const concentracionAnteriorInvalida =
    medicamento &&
    !esConcentracionValida(medicamento.concentracion) &&
    !(campos.concentracionValor.trim() && campos.concentracionUnidad)
      ? medicamento.concentracion
      : null;

  function cambiar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((actual) => ({ ...actual, [campo]: valor }));
    setTocados((actual) => (actual[campo] ? actual : { ...actual, [campo]: true }));
    setErrorServidor(null);
  }

  function tocar(campo: keyof Campos) {
    setTocados((actual) => (actual[campo] ? actual : { ...actual, [campo]: true }));
  }

  function marcarTodos() {
    setTocados(Object.fromEntries(Object.keys(campos).map((campo) => [campo, true])) as Tocados);
  }

  async function guardar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    marcarTodos();
    if (!valido) {
      return;
    }

    const datos: DatosMedicamento = {
      nombre: campos.nombre.trim(),
      principioActivo: campos.principioActivo.trim(),
      presentacion: campos.presentacion as Presentacion,
      concentracion: componerConcentracion(campos.concentracionValor, campos.concentracionUnidad),
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
          <Campo
            etiqueta="Nombre comercial"
            ayuda="Obligatorio · entre 1 y 80 caracteres."
            error={errorDe('nombre')}
          >
            <input
              maxLength={80}
              value={campos.nombre}
              aria-invalid={errorDe('nombre') ? true : undefined}
              onChange={(e) => cambiar('nombre', e.target.value)}
              onBlur={() => tocar('nombre')}
              disabled={enviando}
            />
          </Campo>
          <Campo
            etiqueta="Principio activo"
            ayuda="Obligatorio · entre 1 y 80 caracteres."
            error={errorDe('principioActivo')}
          >
            <input
              maxLength={80}
              value={campos.principioActivo}
              aria-invalid={errorDe('principioActivo') ? true : undefined}
              onChange={(e) => cambiar('principioActivo', e.target.value)}
              onBlur={() => tocar('principioActivo')}
              disabled={enviando}
            />
          </Campo>
        </div>

        <div className="fila-campos">
          <Campo
            etiqueta="Presentación"
            ayuda="Obligatoria · seleccione una forma farmacéutica."
            error={errorDe('presentacion')}
          >
            <SelectorDesplegable
              ariaLabel="Presentación"
              valor={campos.presentacion}
              opciones={OPCIONES_PRESENTACION}
              invalido={Boolean(errorDe('presentacion'))}
              deshabilitado={enviando}
              onChange={(valor) => cambiar('presentacion', valor)}
              onBlur={() => tocar('presentacion')}
            />
          </Campo>
          <fieldset className="campo grupo-concentracion">
            <legend>Concentración</legend>
            <div className="fila-concentracion">
              <input
                aria-label="Valor de la concentración"
                inputMode="decimal"
                maxLength={12}
                placeholder="p. ej. 500"
                value={campos.concentracionValor}
                aria-invalid={errorDe('concentracionValor') ? true : undefined}
                onChange={(e) =>
                  cambiar('concentracionValor', limpiarValorConcentracion(e.target.value))
                }
                onBlur={() => tocar('concentracionValor')}
                disabled={enviando}
              />
              <SelectorDesplegable
                ariaLabel="Unidad de la concentración"
                placeholder="Unidad…"
                valor={campos.concentracionUnidad}
                opciones={OPCIONES_UNIDAD}
                invalido={Boolean(errorDe('concentracionUnidad'))}
                deshabilitado={enviando}
                onChange={(valor) => cambiar('concentracionUnidad', valor)}
                onBlur={() => tocar('concentracionUnidad')}
              />
            </div>
            {concentracionAnteriorInvalida ? (
              <span className="aviso-campo" role="status">
                El valor actual «{concentracionAnteriorInvalida}» no es válido; indique el número y
                elija una unidad.
              </span>
            ) : errorConcentracion ? (
              <span className="error-campo" role="alert">
                {errorConcentracion}
              </span>
            ) : (
              <span className="ayuda">
                Obligatoria · número y unidad de la lista (p. ej. 500 mg).
              </span>
            )}
          </fieldset>
        </div>

        <div className="fila-campos">
          <Campo
            etiqueta="Laboratorio"
            ayuda="Obligatorio · entre 1 y 80 caracteres."
            error={errorDe('laboratorio')}
          >
            <input
              maxLength={80}
              value={campos.laboratorio}
              aria-invalid={errorDe('laboratorio') ? true : undefined}
              onChange={(e) => cambiar('laboratorio', e.target.value)}
              onBlur={() => tocar('laboratorio')}
              disabled={enviando}
            />
          </Campo>
          <Campo
            etiqueta="Lote"
            ayuda="Obligatorio · entre 1 y 40 caracteres, con letras, números, espacios y guiones (p. ej. L-2026-01)."
            error={errorDe('lote')}
          >
            <input
              maxLength={40}
              value={campos.lote}
              aria-invalid={errorDe('lote') ? true : undefined}
              onChange={(e) => cambiar('lote', e.target.value)}
              onBlur={() => tocar('lote')}
              disabled={enviando}
            />
          </Campo>
        </div>

        <div className="fila-campos">
          {medicamento ? (
            <Campo etiqueta="Stock actual" ayuda="Solo cambia con entradas y salidas.">
              <input value={medicamento.cantidadStock} disabled readOnly />
            </Campo>
          ) : (
            <Campo
              etiqueta="Stock inicial"
              ayuda="Número entero entre 0 y 1.000.000 unidades."
              error={errorDe('cantidadStock')}
            >
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                value={campos.cantidadStock}
                aria-invalid={errorDe('cantidadStock') ? true : undefined}
                onChange={(e) => cambiar('cantidadStock', e.target.value)}
                onBlur={() => tocar('cantidadStock')}
                disabled={enviando}
              />
            </Campo>
          )}
          <Campo
            etiqueta="Stock mínimo"
            ayuda="Número entero entre 0 y 1.000.000 unidades (umbral de stock bajo)."
            error={errorDe('stockMinimo')}
          >
            <input
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              value={campos.stockMinimo}
              aria-invalid={errorDe('stockMinimo') ? true : undefined}
              onChange={(e) => cambiar('stockMinimo', e.target.value)}
              onBlur={() => tocar('stockMinimo')}
              disabled={enviando}
            />
          </Campo>
        </div>

        <div className="fila-campos">
          <Campo
            etiqueta="Fecha de vencimiento"
            ayuda="Obligatoria · formato AAAA-MM-DD (p. ej. 2027-01-31)."
            error={errorDe('fechaVencimiento')}
          >
            <SelectorFecha
              ariaLabel="Fecha de vencimiento"
              valor={campos.fechaVencimiento}
              invalido={Boolean(errorDe('fechaVencimiento'))}
              deshabilitado={enviando}
              onChange={(valor) => cambiar('fechaVencimiento', valor)}
              onBlur={() => tocar('fechaVencimiento')}
            />
          </Campo>
          <Campo
            etiqueta="Ubicación"
            ayuda="Obligatoria · máximo 80 caracteres (p. ej. Farmacia, estante A)."
            error={errorDe('ubicacion')}
          >
            <input
              maxLength={80}
              placeholder="p. ej. Farmacia, estante A"
              value={campos.ubicacion}
              aria-invalid={errorDe('ubicacion') ? true : undefined}
              onChange={(e) => cambiar('ubicacion', e.target.value)}
              onBlur={() => tocar('ubicacion')}
              disabled={enviando}
            />
          </Campo>
        </div>

        {!valido && !enviando && (
          <p className="ayuda-formulario" role="status">
            Complete correctamente los campos obligatorios para continuar.
          </p>
        )}

        <div className="acciones-modal">
          <button type="button" className="boton secundario" onClick={onCerrar} disabled={enviando}>
            Cancelar
          </button>
          <button type="submit" className="boton primario" disabled={enviando || !valido}>
            {enviando ? 'Guardando…' : medicamento ? 'Guardar cambios' : 'Registrar'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function Campo({
  etiqueta,
  ayuda,
  error,
  children,
}: {
  etiqueta: string;
  ayuda: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="campo">
      <span>{etiqueta}</span>
      {children}
      {error ? (
        <span className="error-campo" role="alert">
          {error}
        </span>
      ) : (
        <span className="ayuda">{ayuda}</span>
      )}
    </label>
  );
}
