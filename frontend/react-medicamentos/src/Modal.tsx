import { useEffect, useId, type ReactNode } from 'react';

interface Props {
  titulo: string;
  onCerrar: () => void;
  /** Mientras se guarda no se cierra con Esc ni con clic fuera. */
  bloqueado?: boolean;
  children: ReactNode;
}

/** Diálogo sencillo con `role="dialog"` y cierre con Esc o clic en el fondo. */
export function Modal({ titulo, onCerrar, bloqueado = false, children }: Props) {
  const idTitulo = useId();

  useEffect(() => {
    const alPulsar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape' && !bloqueado) {
        onCerrar();
      }
    };
    document.addEventListener('keydown', alPulsar);
    return () => document.removeEventListener('keydown', alPulsar);
  }, [bloqueado, onCerrar]);

  return (
    <div
      className="fondo-modal"
      onMouseDown={(evento) => {
        if (evento.target === evento.currentTarget && !bloqueado) {
          onCerrar();
        }
      }}
    >
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby={idTitulo}>
        <header className="modal-cabecera">
          <h2 id={idTitulo}>{titulo}</h2>
          <button
            type="button"
            className="boton-icono"
            aria-label="Cerrar"
            onClick={onCerrar}
            disabled={bloqueado}
          >
            ×
          </button>
        </header>
        <div className="modal-cuerpo">{children}</div>
      </div>
    </div>
  );
}
