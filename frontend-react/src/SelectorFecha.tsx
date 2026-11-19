import { useEffect, useRef, useState } from 'react';

const MESES = [
  'Enero',
  'Febrero',
  'Marzo',
  'Abril',
  'Mayo',
  'Junio',
  'Julio',
  'Agosto',
  'Septiembre',
  'Octubre',
  'Noviembre',
  'Diciembre',
];

const DIAS_SEMANA = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

interface Props {
  id?: string;
  ariaLabel?: string;
  valor: string;
  invalido?: boolean;
  deshabilitado?: boolean;
  onChange: (valor: string) => void;
  onBlur?: () => void;
}

function parsearIso(valor: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor.trim());
  if (!m) {
    return null;
  }
  const anio = Number(m[1]);
  const mes = Number(m[2]) - 1;
  const dia = Number(m[3]);
  const fecha = new Date(anio, mes, dia);
  if (fecha.getFullYear() !== anio || fecha.getMonth() !== mes || fecha.getDate() !== dia) {
    return null;
  }
  return fecha;
}

function formatearIso(anio: number, mesCero: number, dia: number): string {
  const mm = String(mesCero + 1).padStart(2, '0');
  const dd = String(dia).padStart(2, '0');
  return `${anio}-${mm}-${dd}`;
}

/**
 * Selector de fecha en formato `AAAA-MM-DD` con calendario desplegable
 * coherente con el diseño de la interfaz principal.
 */
export function SelectorFecha({
  id,
  ariaLabel,
  valor,
  invalido = false,
  deshabilitado = false,
  onChange,
  onBlur,
}: Props) {
  const hoy = new Date();
  const inicial = parsearIso(valor) ?? hoy;
  const [abierto, setAbierto] = useState(false);
  const [vistaAnio, setVistaAnio] = useState(inicial.getFullYear());
  const [vistaMes, setVistaMes] = useState(inicial.getMonth());
  const contenedorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const parsed = parsearIso(valor);
    if (parsed) {
      setVistaAnio(parsed.getFullYear());
      setVistaMes(parsed.getMonth());
    }
  }, [valor]);

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

  const primerDiaSemana = (new Date(vistaAnio, vistaMes, 1).getDay() + 6) % 7;
  const diasEnMes = new Date(vistaAnio, vistaMes + 1, 0).getDate();
  const hoyIso = formatearIso(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());

  function cambiarMes(delta: number) {
    const nueva = new Date(vistaAnio, vistaMes + delta, 1);
    setVistaAnio(nueva.getFullYear());
    setVistaMes(nueva.getMonth());
  }

  function elegirDia(dia: number) {
    onChange(formatearIso(vistaAnio, vistaMes, dia));
    setAbierto(false);
    onBlur?.();
  }

  return (
    <div className="selector-fecha" ref={contenedorRef}>
      <div className="selector-fecha-entrada">
        <input
          id={id}
          type="text"
          inputMode="numeric"
          placeholder="AAAA-MM-DD"
          maxLength={10}
          aria-label={ariaLabel}
          value={valor}
          aria-invalid={invalido ? true : undefined}
          disabled={deshabilitado}
          onChange={(e) => onChange(e.target.value)}
          onBlur={(e) => {
            if (
              !abierto &&
              contenedorRef.current &&
              !contenedorRef.current.contains(e.relatedTarget as Node | null)
            ) {
              onBlur?.();
            }
          }}
        />
        <button
          type="button"
          className="selector-fecha-boton"
          aria-label="Abrir calendario"
          aria-expanded={abierto}
          disabled={deshabilitado}
          onClick={() => setAbierto((prev) => !prev)}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect
              x="3"
              y="4"
              width="18"
              height="17"
              rx="3"
              stroke="currentColor"
              strokeWidth="1.8"
            />
            <path
              d="M3 9h18M8 2v4M16 2v4"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
            />
          </svg>
        </button>
      </div>

      {abierto && (
        <div className="calendario-popover" role="dialog" aria-label="Calendario">
          <div className="calendario-cabecera">
            <button
              type="button"
              className="calendario-nav"
              aria-label="Mes anterior"
              onClick={() => cambiarMes(-1)}
            >
              ‹
            </button>
            <span className="calendario-titulo">
              {MESES[vistaMes]} {vistaAnio}
            </span>
            <button
              type="button"
              className="calendario-nav"
              aria-label="Mes siguiente"
              onClick={() => cambiarMes(1)}
            >
              ›
            </button>
          </div>

          <div className="calendario-rejilla">
            {DIAS_SEMANA.map((d) => (
              <span key={d} className="calendario-dia-semana">
                {d}
              </span>
            ))}
            {Array.from({ length: primerDiaSemana }).map((_, idx) => (
              <span key={`vacio-${idx}`} />
            ))}
            {Array.from({ length: diasEnMes }).map((_, idx) => {
              const dia = idx + 1;
              const iso = formatearIso(vistaAnio, vistaMes, dia);
              const seleccionado = valor.trim() === iso;
              const esHoy = hoyIso === iso;
              return (
                <button
                  key={iso}
                  type="button"
                  className={`calendario-dia${seleccionado ? ' seleccionado' : ''}${
                    esHoy ? ' hoy' : ''
                  }`}
                  onClick={() => elegirDia(dia)}
                >
                  {dia}
                </button>
              );
            })}
          </div>

          <div className="calendario-pie">
            <button
              type="button"
              className="boton pequeno"
              onClick={() => {
                onChange(hoyIso);
                setAbierto(false);
                onBlur?.();
              }}
            >
              Hoy
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
