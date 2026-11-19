import { useEffect, useRef, useState, type ChangeEvent } from 'react';

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
const DIGITOS_FECHA = 8;

interface Props {
  id?: string;
  ariaLabel?: string;
  valor: string;
  invalido?: boolean;
  deshabilitado?: boolean;
  onChange: (valor: string) => void;
  onBlur?: () => void;
}

/** Reagrupa los dígitos tecleados en `dd/mm/aaaa` (igual que `appFechaDdMmAaaa` en Angular). */
export function enmascararFecha(texto: string): string {
  const digitos = texto.replace(/\D/g, '').slice(0, DIGITOS_FECHA);
  return [digitos.slice(0, 2), digitos.slice(2, 4), digitos.slice(4)]
    .filter((parte) => parte !== '')
    .join('/');
}

/**
 * Indica si una fecha parcial o completa en `dd/mm/aaaa` ya es imposible
 * (día o mes inexistente, p. ej. `32`, `12/32`, `31/04` o `29/02/2026`).
 * Devuelve `false` mientras el prefijo tecleado siga pudiendo completarse en una fecha válida.
 */
export function esFechaParcialImposible(valor: string): boolean {
  const limpio = valor.trim();
  if (!limpio || parsearIso(limpio)) {
    return false;
  }
  const digitos = limpio.replace(/\D/g, '');
  if (digitos.length === 0) {
    return true;
  }
  if (Number(digitos[0]) > 3) {
    return true;
  }
  if (digitos.length >= 2) {
    const dia = Number(digitos.slice(0, 2));
    if (dia < 1 || dia > 31) {
      return true;
    }
    if (digitos.length >= 3 && Number(digitos[2]) > 1) {
      return true;
    }
    if (digitos.length >= 4) {
      const mes = Number(digitos.slice(2, 4));
      if (mes < 1 || mes > 12) {
        return true;
      }
      // Año bisiesto de referencia para permitir 29/02 hasta completar el año.
      const maxDiaMes = new Date(2024, mes, 0).getDate();
      if (dia > maxDiaMes) {
        return true;
      }
    }
  }
  if (digitos.length >= DIGITOS_FECHA) {
    return ddMmAaaaAIso(enmascararFecha(digitos)) === null;
  }
  return false;
}

/** Calcula la posición del cursor contando solo dígitos para que no salte al insertar `/`. */
function posicionDelCursor(escrito: string, cursor: number, enmascarado: string): number {
  const digitosDelante = escrito.slice(0, cursor).replace(/\D/g, '').length;
  let indice = 0;
  for (let vistos = 0; indice < enmascarado.length && vistos < digitosDelante; indice++) {
    if (/\d/.test(enmascarado[indice])) {
      vistos++;
    }
  }
  return indice;
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

function isoADdMmAaaa(valor: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor.trim());
  if (!m) {
    return valor;
  }
  return `${m[3]}/${m[2]}/${m[1]}`;
}

function ddMmAaaaAIso(texto: string): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto.trim());
  if (!m) {
    return null;
  }
  const dia = Number(m[1]);
  const mes = Number(m[2]) - 1;
  const anio = Number(m[3]);
  const fecha = new Date(anio, mes, dia);
  if (fecha.getFullYear() !== anio || fecha.getMonth() !== mes || fecha.getDate() !== dia) {
    return null;
  }
  return formatearIso(anio, mes, dia);
}

/**
 * Selector de fecha con máscara `dd/mm/aaaa` (igual que la interfaz Angular) y
 * calendario desplegable hacia arriba para no salirse del diálogo.
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
  const [textoVisible, setTextoVisible] = useState(() => isoADdMmAaaa(valor));
  const [vistaAnio, setVistaAnio] = useState(inicial.getFullYear());
  const [vistaMes, setVistaMes] = useState(inicial.getMonth());
  const contenedorRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const parsed = parsearIso(valor);
    if (parsed) {
      setTextoVisible(isoADdMmAaaa(valor));
      setVistaAnio(parsed.getFullYear());
      setVistaMes(parsed.getMonth());
    } else if (valor === '') {
      setTextoVisible('');
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
    const iso = formatearIso(vistaAnio, vistaMes, dia);
    setTextoVisible(isoADdMmAaaa(iso));
    onChange(iso);
    setAbierto(false);
    onBlur?.();
  }

  function alCambiarTexto(evento: ChangeEvent<HTMLInputElement>) {
    const crudo = evento.target.value;

    // Si se pega o escribe directamente una fecha ISO válida (`AAAA-MM-DD`), la convertimos.
    if (parsearIso(crudo)) {
      const iso = crudo.trim();
      setTextoVisible(isoADdMmAaaa(iso));
      onChange(iso);
      return;
    }

    const cursor = evento.target.selectionStart ?? crudo.length;
    const enmascarado = enmascararFecha(crudo);
    setTextoVisible(enmascarado);

    if (enmascarado !== crudo) {
      const nuevaPos = posicionDelCursor(crudo, cursor, enmascarado);
      requestAnimationFrame(() => {
        inputRef.current?.setSelectionRange(nuevaPos, nuevaPos);
      });
    }

    if (enmascarado === '') {
      onChange('');
      return;
    }

    const iso = ddMmAaaaAIso(enmascarado);
    onChange(iso ?? enmascarado);
  }

  return (
    <div className="selector-fecha" ref={contenedorRef}>
      <div className="selector-fecha-entrada">
        <input
          ref={inputRef}
          id={id}
          type="text"
          inputMode="numeric"
          placeholder="dd/mm/aaaa"
          maxLength={10}
          aria-label={ariaLabel}
          value={textoVisible}
          aria-invalid={invalido ? true : undefined}
          disabled={deshabilitado}
          onChange={alCambiarTexto}
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
            <div className="calendario-controles">
              <button
                type="button"
                className="calendario-nav"
                aria-label="Año anterior"
                onClick={() => setVistaAnio((a) => a - 1)}
              >
                «
              </button>
              <button
                type="button"
                className="calendario-nav"
                aria-label="Mes anterior"
                onClick={() => cambiarMes(-1)}
              >
                ‹
              </button>
            </div>
            <span className="calendario-titulo">
              {MESES[vistaMes]} {vistaAnio}
            </span>
            <div className="calendario-controles">
              <button
                type="button"
                className="calendario-nav"
                aria-label="Mes siguiente"
                onClick={() => cambiarMes(1)}
              >
                ›
              </button>
              <button
                type="button"
                className="calendario-nav"
                aria-label="Año siguiente"
                onClick={() => setVistaAnio((a) => a + 1)}
              >
                »
              </button>
            </div>
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
                setTextoVisible(isoADdMmAaaa(hoyIso));
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
