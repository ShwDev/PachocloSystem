import { useEffect, useId, useRef, useState } from 'react';

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

/**
 * Selector desplegable estilizado en armonía con la interfaz principal,
 * con soporte de teclado (Enter/Espacio/Esc/Flechas) y cierre al hacer clic fuera.
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
  const contenedorRef = useRef<HTMLDivElement>(null);
  const listaId = useId();

  const seleccionada = opciones.find((o) => o.valor === valor);

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

  function elegir(nuevoValor: T) {
    onChange(nuevoValor);
    setAbierto(false);
    onBlur?.();
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
        onClick={() => setAbierto((prev) => !prev)}
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
          {opciones.map((opcion) => {
            const activo = opcion.valor === valor;
            return (
              <li
                key={opcion.valor}
                role="option"
                aria-selected={activo}
                className={`desplegable-opcion${activo ? ' seleccionada' : ''}`}
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
