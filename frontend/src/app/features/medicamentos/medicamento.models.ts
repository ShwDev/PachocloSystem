/** Copia de `Presentacion.java` de MedicamentosService (mismo orden). */
export type Presentacion =
  'TABLETA' | 'CAPSULA' | 'JARABE' | 'SUSPENSION' | 'INYECTABLE' | 'CREMA' | 'GOTAS' | 'OTRO';

export const PRESENTACIONES: readonly Presentacion[] = [
  'CAPSULA',
  'CREMA',
  'GOTAS',
  'INYECTABLE',
  'JARABE',
  'SUSPENSION',
  'TABLETA',
  'OTRO',
];

export const ETIQUETAS_PRESENTACION: Readonly<Record<Presentacion, string>> = {
  TABLETA: 'Tableta',
  CAPSULA: 'Cápsula',
  JARABE: 'Jarabe',
  SUSPENSION: 'Suspensión',
  INYECTABLE: 'Inyectable',
  CREMA: 'Crema',
  GOTAS: 'Gotas',
  OTRO: 'Otro',
};

/**
 * `MedicamentoResponse`. `stockBajo` (stock ≤ mínimo) y `vencido` los calcula
 * el servicio con su fecha de hoy. `fechaVencimiento` es una fecha sin hora
 * ("2027-01-31"): se maneja como texto (ver shared/fechas.ts).
 */
export interface Medicamento {
  idMedicamento: string;
  nombre: string;
  principioActivo: string;
  presentacion: Presentacion;
  concentracion: string;
  laboratorio: string;
  lote: string;
  cantidadStock: number;
  stockMinimo: number;
  fechaVencimiento: string;
  ubicacion: string;
  stockBajo: boolean;
  vencido: boolean;
}

/** Edición (`ActualizarMedicamentoRequest`): todo menos el stock. */
export interface DatosMedicamento {
  nombre: string;
  principioActivo: string;
  presentacion: Presentacion;
  concentracion: string;
  laboratorio: string;
  lote: string;
  stockMinimo: number;
  fechaVencimiento: string;
  ubicacion: string;
}

/** Alta (`CrearMedicamentoRequest`): los datos más el stock inicial. */
export interface MedicamentoRequest extends DatosMedicamento {
  cantidadStock: number;
}

export type TipoMovimiento = 'entrada' | 'salida';
