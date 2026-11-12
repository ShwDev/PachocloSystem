import { useEffect, useState } from 'react';
import { mensajesDeError, esCancelacion, type ClienteMedicamentos } from './api';
import { DialogoEliminar } from './DialogoEliminar';
import { DialogoMedicamento } from './DialogoMedicamento';
import { DialogoMovimiento } from './DialogoMovimiento';
import { formatearFecha } from './fechas';
import { nuevaClaveIdempotencia } from './idempotencia';
import { tienePermiso } from './permisos';
import { ETIQUETAS_PRESENTACION, type Medicamento, type Rol, type TipoMovimiento } from './tipos';

type Vista = 'todos' | 'stock-bajo' | 'por-vencer' | 'vencidos';

const PESTANAS: readonly { vista: Vista; etiqueta: string }[] = [
  { vista: 'todos', etiqueta: 'Todos' },
  { vista: 'stock-bajo', etiqueta: 'Stock bajo' },
  { vista: 'por-vencer', etiqueta: 'Por vencer' },
  { vista: 'vencidos', etiqueta: 'Vencidos' },
];

const DIAS_POR_VENCER_INICIAL = 30;
const ESPERA_BUSQUEDA_MS = 250;

type Dialogo =
  | {
      tipo: 'movimiento';
      medicamento: Medicamento;
      movimiento: TipoMovimiento;
      clave: string;
    }
  | { tipo: 'medicamento'; medicamento?: Medicamento }
  | { tipo: 'eliminar'; medicamento: Medicamento };

export interface PropsMedicamentos {
  cliente: ClienteMedicamentos;
  rol: Rol | null;
}

/**
 * Inventario de medicamentos en React: listado con filtros, altas, ediciones,
 * bajas y movimientos de stock. Consume la misma API que la SPA Angular
 * (`/api/medicamentos` de PachocloSystem).
 */
export function Medicamentos({ cliente, rol }: PropsMedicamentos) {
  const [vista, setVista] = useState<Vista>('todos');
  const [texto, setTexto] = useState('');
  const [dias, setDias] = useState(String(DIAS_POR_VENCER_INICIAL));
  const [medicamentos, setMedicamentos] = useState<Medicamento[]>([]);
  const [cargando, setCargando] = useState(true);
  const [errores, setErrores] = useState<readonly string[]>([]);
  const [recarga, setRecarga] = useState(0);
  const [aviso, setAviso] = useState<string | null>(null);
  const [dialogo, setDialogo] = useState<Dialogo | null>(null);

  const puedeEscribir = tienePermiso(rol, 'medicamentos.escribir');
  const puedeSalidas = tienePermiso(rol, 'medicamentos.salidas');
  const mostrarAcciones = puedeEscribir || puedeSalidas;

  const diasValidos = /^\d+$/.test(dias.trim()) && Number(dias) >= 1 && Number(dias) <= 365;

  useEffect(() => {
    let vivo = true;
    const control = new AbortController();
    setErrores([]);

    if (vista === 'por-vencer' && !diasValidos) {
      setMedicamentos([]);
      setCargando(false);
      return () => control.abort();
    }

    setCargando(true);
    const espera = vista === 'todos' || vista === 'por-vencer' ? ESPERA_BUSQUEDA_MS : 0;
    const temporizador = window.setTimeout(() => {
      cargarLista(cliente, vista, texto, Number(dias), control.signal)
        .then((lista) => {
          if (vivo) {
            setMedicamentos(lista);
          }
        })
        .catch((error: unknown) => {
          if (vivo && !esCancelacion(error)) {
            setErrores(mensajesDeError(error));
          }
        })
        .finally(() => {
          if (vivo) {
            setCargando(false);
          }
        });
    }, espera);

    return () => {
      vivo = false;
      window.clearTimeout(temporizador);
      control.abort();
    };
  }, [cliente, vista, texto, dias, diasValidos, recarga]);

  useEffect(() => {
    if (!aviso) {
      return;
    }
    const temporizador = window.setTimeout(() => setAviso(null), 6000);
    return () => window.clearTimeout(temporizador);
  }, [aviso]);

  function cambiarVista(nueva: Vista) {
    if (nueva === vista) {
      return;
    }
    if (nueva === 'por-vencer' && !diasValidos) {
      setDias(String(DIAS_POR_VENCER_INICIAL));
    }
    setVista(nueva);
    setMedicamentos([]);
    setErrores([]);
  }

  function recargar() {
    setRecarga((valor) => valor + 1);
  }

  function hecho(mensaje: string) {
    setDialogo(null);
    setAviso(mensaje);
    recargar();
  }

  function abrirMovimiento(medicamento: Medicamento, movimiento: TipoMovimiento) {
    setDialogo({
      tipo: 'movimiento',
      medicamento,
      movimiento,
      clave: nuevaClaveIdempotencia(),
    });
  }

  const mensajeVacio = calcularMensajeVacio(
    vista,
    texto,
    dias,
    medicamentos.length,
    cargando,
    errores.length,
  );

  return (
    <section className="medicamentos" aria-labelledby="titulo-medicamentos">
      <header className="encabezado">
        <div>
          <h1 id="titulo-medicamentos">
            Medicamentos <span className="etiqueta-framework">React</span>
          </h1>
          <p className="subtitulo">Inventario de medicamentos de PachocloSystem.</p>
        </div>
        {puedeEscribir && (
          <button
            type="button"
            className="boton primario"
            onClick={() => setDialogo({ tipo: 'medicamento' })}
          >
            Nuevo medicamento
          </button>
        )}
      </header>

      {aviso && (
        <div className="aviso-exito" role="status">
          <span>{aviso}</span>
          <button
            type="button"
            className="boton-icono"
            aria-label="Cerrar aviso"
            onClick={() => setAviso(null)}
          >
            ×
          </button>
        </div>
      )}

      <nav className="pestanas" role="tablist" aria-label="Vistas del inventario">
        {PESTANAS.map((pestana) => (
          <button
            key={pestana.vista}
            type="button"
            role="tab"
            aria-selected={vista === pestana.vista}
            className={vista === pestana.vista ? 'pestana activa' : 'pestana'}
            onClick={() => cambiarVista(pestana.vista)}
          >
            {pestana.etiqueta}
          </button>
        ))}
      </nav>

      <div className="filtros">
        {vista === 'todos' && (
          <label className="buscador">
            <span>Buscar por ID, nombre o principio activo</span>
            <input
              type="search"
              value={texto}
              onChange={(evento) => setTexto(evento.target.value)}
              placeholder="Escriba para filtrar"
            />
          </label>
        )}
        {vista === 'por-vencer' && (
          <label className="dias">
            <span>Vencen en los próximos (días)</span>
            <input
              type="number"
              min="1"
              max="365"
              step="1"
              value={dias}
              aria-invalid={!diasValidos}
              onChange={(evento) => setDias(evento.target.value)}
            />
            {!diasValidos && (
              <span className="error-campo" role="alert">
                Los días deben ser un entero entre 1 y 365.
              </span>
            )}
          </label>
        )}
      </div>

      <div className="progreso" aria-hidden={!cargando}>
        {cargando && (
          <div className="progreso-barra" role="progressbar" aria-label="Cargando medicamentos" />
        )}
      </div>

      {errores.length > 0 && (
        <div className="aviso-servicio" role="alert">
          <div className="aviso-texto">
            {errores.map((mensaje) => (
              <p key={mensaje}>{mensaje}</p>
            ))}
          </div>
          <button type="button" className="boton secundario" onClick={recargar}>
            Reintentar
          </button>
        </div>
      )}

      <div className="tabla-contenedor" role="tabpanel">
        {medicamentos.length > 0 ? (
          <table className="tabla">
            <caption className="solo-lectores">Inventario de medicamentos</caption>
            <thead>
              <tr>
                <th scope="col">ID</th>
                <th scope="col">Medicamento</th>
                <th scope="col">Lote</th>
                <th scope="col" className="numero">
                  Stock
                </th>
                <th scope="col">Vence</th>
                <th scope="col">Estado</th>
                {mostrarAcciones && (
                  <th scope="col">
                    <span className="solo-lectores">Acciones</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {medicamentos.map((medicamento) => (
                <tr key={medicamento.idMedicamento}>
                  <td className="id">{medicamento.idMedicamento}</td>
                  <td>
                    <span className="principal">
                      {medicamento.nombre} {medicamento.concentracion}
                    </span>
                    <span className="secundario">
                      {medicamento.principioActivo} ·{' '}
                      {ETIQUETAS_PRESENTACION[medicamento.presentacion]} · {medicamento.laboratorio}
                    </span>
                  </td>
                  <td>
                    <span className="principal">{medicamento.lote}</span>
                    <span className="secundario">{medicamento.ubicacion}</span>
                  </td>
                  <td className="numero">
                    <span className="principal">{medicamento.cantidadStock}</span>
                    <span className="secundario">mín. {medicamento.stockMinimo}</span>
                  </td>
                  <td>{formatearFecha(medicamento.fechaVencimiento)}</td>
                  <td>
                    {medicamento.vencido && (
                      <span className="insignia insignia-vencido">Vencido</span>
                    )}
                    {medicamento.stockBajo && (
                      <span className="insignia insignia-stock-bajo">Stock bajo</span>
                    )}
                  </td>
                  {mostrarAcciones && (
                    <td>
                      <div className="acciones-fila">
                        {puedeEscribir && (
                          <button
                            type="button"
                            className="boton pequeno"
                            aria-label={`Entrada de stock de ${medicamento.nombre} ${medicamento.concentracion}`}
                            onClick={() => abrirMovimiento(medicamento, 'entrada')}
                          >
                            Entrada
                          </button>
                        )}
                        {puedeSalidas && (
                          <button
                            type="button"
                            className="boton pequeno"
                            aria-label={`Salida de stock de ${medicamento.nombre} ${medicamento.concentracion}`}
                            onClick={() => abrirMovimiento(medicamento, 'salida')}
                          >
                            Salida
                          </button>
                        )}
                        {puedeEscribir && (
                          <>
                            <button
                              type="button"
                              className="boton pequeno"
                              aria-label={`Editar ${medicamento.nombre} ${medicamento.concentracion}`}
                              onClick={() => setDialogo({ tipo: 'medicamento', medicamento })}
                            >
                              Editar
                            </button>
                            <button
                              type="button"
                              className="boton pequeno peligro"
                              aria-label={`Eliminar ${medicamento.nombre} ${medicamento.concentracion}`}
                              onClick={() => setDialogo({ tipo: 'eliminar', medicamento })}
                            >
                              Eliminar
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          mensajeVacio && (
            <div className="estado-vacio">
              <p>{mensajeVacio}</p>
            </div>
          )
        )}
      </div>

      {dialogo?.tipo === 'movimiento' && (
        <DialogoMovimiento
          cliente={cliente}
          medicamento={dialogo.medicamento}
          tipo={dialogo.movimiento}
          claveIdempotencia={dialogo.clave}
          onCerrar={() => setDialogo(null)}
          onHecho={hecho}
        />
      )}
      {dialogo?.tipo === 'medicamento' && (
        <DialogoMedicamento
          cliente={cliente}
          medicamento={dialogo.medicamento}
          onCerrar={() => setDialogo(null)}
          onHecho={hecho}
        />
      )}
      {dialogo?.tipo === 'eliminar' && (
        <DialogoEliminar
          cliente={cliente}
          medicamento={dialogo.medicamento}
          onCerrar={() => setDialogo(null)}
          onHecho={hecho}
        />
      )}
    </section>
  );
}

function cargarLista(
  cliente: ClienteMedicamentos,
  vista: Vista,
  texto: string,
  dias: number,
  signal: AbortSignal,
): Promise<Medicamento[]> {
  switch (vista) {
    case 'todos':
      return cliente.listar(texto, signal);
    case 'stock-bajo':
      return cliente.listarStockBajo(signal);
    case 'por-vencer':
      return cliente.listarPorVencer(dias, signal);
    case 'vencidos':
      return cliente.listarVencidos(signal);
  }
}

function calcularMensajeVacio(
  vista: Vista,
  texto: string,
  dias: string,
  cuantos: number,
  cargando: boolean,
  errores: number,
): string | null {
  if (cargando || errores > 0 || cuantos > 0) {
    return null;
  }
  switch (vista) {
    case 'todos':
      return texto.trim()
        ? `Ningún medicamento coincide con «${texto.trim()}».`
        : 'Todavía no hay medicamentos registrados.';
    case 'stock-bajo':
      return 'Ningún medicamento tiene el stock en su mínimo o por debajo.';
    case 'por-vencer':
      return `Ningún medicamento vence en los próximos ${dias} días.`;
    case 'vencidos':
      return 'No hay medicamentos vencidos.';
  }
}
