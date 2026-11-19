import {
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react';

export interface OpcionDesplegable<T extends string> {
  valor: T;
  etiqueta: string;
}

interface Props<T extends string> {
  id?: string;
  ariaLabel?: string;
  valor: T | '';
  placeholder?: string;
  opciones: readonly OpcionDesplegable<T>[];
  invalido?: boolean;
  deshabilitado?: boolean;
  onChange: (valor: T) => void;
  onBlur?: () => void;
}

const TIEMPO_BUSQUEDA_MS = 650;

/** Normaliza a minúsculas sin tildes para coincidencia por prefijo (ej. `c` → `Cápsula`). */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

/**
 * Selector desplegable estilizado en armonía con la interfaz principal,
 * con navegación por teclado (flechas, Enter, Esc) y búsqueda por primera letra/prefijo
 * equivalente a `mat-select`.
 */
export function SelectorDesplegable<T extends string>({
  id,
  ariaLabel,
  valor,
  placeholder = 'Seleccione…',
  opciones,
  invalido = false,
  deshabilitado = false,
  onChange,
  onBlur,
}: Props<T>) {
  const [abierto, setAbierto] = useState(false);
  const [indiceActivo, setIndiceActivo] = useState(-1);
  const contenedorRef = useRef<HTMLDivElement>(null);
  const opcionesRef = useRef<Array<HTMLLIElement | null>>([]);
  const bufferRef = useRef('');
  const temporizadorRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const listaId = useId();

  const indiceSeleccionado = opciones.findIndex((o) => o.valor === valor);
  const seleccionada = indiceSeleccionado >= 0 ? opciones[indiceSeleccionado] : undefined;

  useEffect(() => {
    return () => {
      if (temporizadorRef.current !== null) {
        clearTimeout(temporizadorRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!abierto) {
      return;
    }
    const alClicFuera = (evento: MouseEvent) => {
      if (contenedorRef.current && !contenedorRef.current.contains(evento.target as Node)) {
        setAbierto(false);
        onBlur?.();
      }
    };
    const alTecla = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') {
        setAbierto(false);
        onBlur?.();
      }
    };
    document.addEventListener('mousedown', alClicFuera);
    document.addEventListener('keydown', alTecla);
    return () => {
      document.removeEventListener('mousedown', alClicFuera);
      document.removeEventListener('keydown', alTecla);
    };
  }, [abierto, onBlur]);

  useEffect(() => {
    if (abierto && indiceActivo >= 0) {
      opcionesRef.current[indiceActivo]?.scrollIntoView?.({ block: 'nearest' });
    }
  }, [abierto, indiceActivo]);

  function abrir() {
    setIndiceActivo(indiceSeleccionado >= 0 ? indiceSeleccionado : 0);
    setAbierto(true);
  }

  function elegir(nuevoValor: T) {
    onChange(nuevoValor);
    setAbierto(false);
    onBlur?.();
  }

  function buscarCoincidencia(tecla: string): number {
    if (opciones.length === 0) {
      return -1;
    }
    const normalizada = normalizar(tecla);
    if (!normalizada) {
      return -1;
    }

    if (temporizadorRef.current !== null) {
      clearTimeout(temporizadorRef.current);
    }
    const nuevoBuffer = bufferRef.current + normalizada;
    bufferRef.current = nuevoBuffer;
    temporizadorRef.current = setTimeout(() => {
      bufferRef.current = '';
      temporizadorRef.current = null;
    }, TIEMPO_BUSQUEDA_MS);

    const actual = abierto ? indiceActivo : indiceSeleccionado;
    const mismaLetraRepetida =
      nuevoBuffer.length > 1 && [...nuevoBuffer].every((c) => c === normalizada);

    // Si es una sola letra o se pulsa repetidamente la misma letra, cicla desde la siguiente opción.
    if (nuevoBuffer.length === 1 || mismaLetraRepetida) {
      const inicio = actual >= 0 ? (actual + 1) % opciones.length : 0;
      for (let i = 0; i < opciones.length; i++) {
        const idx = (inicio + i) % opciones.length;
        if (normalizar(opciones[idx].etiqueta).startsWith(normalizada)) {
          return idx;
        }
      }
      return -1;
    }

    // Si se escribe un prefijo de varias letras (p. ej. "cr" o "mil"), busca desde el principio.
    const idxPrefijo = opciones.findIndex((o) => normalizar(o.etiqueta).startsWith(nuevoBuffer));
    if (idxPrefijo >= 0) {
      return idxPrefijo;
    }

    // Si el prefijo compuesto no coincide, reinicia con la última tecla pulsada.
    bufferRef.current = normalizada;
    return opciones.findIndex((o) => normalizar(o.etiqueta).startsWith(normalizada));
  }

  function alTeclaDisparador(evento: ReactKeyboardEvent<HTMLButtonElement>) {
    if (deshabilitado || opciones.length === 0) {
      return;
    }

    switch (evento.key) {
      case 'ArrowDown': {
        evento.preventDefault();
        if (!abierto) {
          abrir();
        } else {
          setIndiceActivo((prev) => (prev + 1) % opciones.length);
        }
        return;
      }
      case 'ArrowUp': {
        evento.preventDefault();
        if (!abierto) {
          setIndiceActivo(indiceSeleccionado >= 0 ? indiceSeleccionado : opciones.length - 1);
          setAbierto(true);
        } else {
          setIndiceActivo((prev) => (prev - 1 + opciones.length) % opciones.length);
        }
        return;
      }
      case 'Home': {
        if (abierto) {
          evento.preventDefault();
          setIndiceActivo(0);
        }
        return;
      }
      case 'End': {
        if (abierto) {
          evento.preventDefault();
          setIndiceActivo(opciones.length - 1);
        }
        return;
      }
      case 'Enter': {
        if (abierto && indiceActivo >= 0) {
          evento.preventDefault();
          elegir(opciones[indiceActivo].valor);
        }
        return;
      }
      case ' ': {
        if (bufferRef.current === '') {
          evento.preventDefault();
          if (abierto && indiceActivo >= 0) {
            elegir(opciones[indiceActivo].valor);
          } else {
            abrir();
          }
          return;
        }
        break;
      }
      case 'Escape': {
        if (abierto) {
          evento.preventDefault();
          setAbierto(false);
          onBlur?.();
        }
        return;
      }
      default:
        break;
    }

    if (evento.key.length === 1 && !evento.ctrlKey && !evento.metaKey && !evento.altKey) {
      evento.preventDefault();
      const idx = buscarCoincidencia(evento.key);
      if (idx >= 0) {
        if (abierto) {
          setIndiceActivo(idx);
        } else {
          onChange(opciones[idx].valor);
        }
      }
    }
  }

  return (
    <div className="desplegable" ref={contenedorRef}>
      <button
        id={id}
        type="button"
        className={`desplegable-disparador${abierto ? ' abierto' : ''}${
          !seleccionada ? ' vacio' : ''
        }`}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={abierto}
        aria-controls={abierto ? listaId : undefined}
        aria-invalid={invalido ? true : undefined}
        disabled={deshabilitado}
        onClick={() => {
          if (abierto) {
            setAbierto(false);
          } else {
            abrir();
          }
        }}
        onKeyDown={alTeclaDisparador}
        onBlur={(evento) => {
          if (
            !abierto &&
            contenedorRef.current &&
            !contenedorRef.current.contains(evento.relatedTarget as Node | null)
          ) {
            onBlur?.();
          }
        }}
      >
        <span className="desplegable-texto">
          {seleccionada ? seleccionada.etiqueta : placeholder}
        </span>
        <svg
          className="desplegable-flecha"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M7 10l5 5 5-5"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>

      {abierto && (
        <ul id={listaId} className="desplegable-lista" role="listbox" aria-label={ariaLabel}>
          {opciones.map((opcion, idx) => {
            const seleccionadaActual = opcion.valor === valor;
            const activa = idx === indiceActivo;
            return (
              <li
                key={opcion.valor}
                ref={(el) => {
                  opcionesRef.current[idx] = el;
                }}
                role="option"
                aria-selected={seleccionadaActual}
                className={`desplegable-opcion${seleccionadaActual ? ' seleccionada' : ''}${
                  activa ? ' activa' : ''
                }`}
                onMouseEnter={() => setIndiceActivo(idx)}
                onMouseDown={(evento) => {
                  evento.preventDefault();
                  elegir(opcion.valor);
                }}
              >
                {opcion.etiqueta}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
