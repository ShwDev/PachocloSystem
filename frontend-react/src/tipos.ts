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

/** Unidades de medida admitidas en la concentración de un medicamento. */
export type UnidadConcentracion =
  | 'mcg'
  | 'mg'
  | 'g'
  | 'kg'
  | 'ml'
  | 'cl'
  | 'l'
  | '%'
  | 'UI'
  | 'mEq'
  | 'mmol'
  | 'mg/ml'
  | 'mg/5 ml'
  | 'UI/ml';

export const UNIDADES_CONCENTRACION: readonly UnidadConcentracion[] = [
  'cl',
  'g',
  'kg',
  'l',
  'mcg',
  'mEq',
  'mg',
  'mg/5 ml',
  'mg/ml',
  'ml',
  'mmol',
  '%',
  'UI',
  'UI/ml',
];

export const ETIQUETAS_UNIDAD_CONCENTRACION: Readonly<Record<UnidadConcentracion, string>> = {
  mcg: 'Microgramo (mcg)',
  mg: 'Miligramo (mg)',
  g: 'Gramo (g)',
  kg: 'Kilogramo (kg)',
  ml: 'Mililitro (ml)',
  cl: 'Centilitro (cl)',
  l: 'Litro (l)',
  '%': 'Porcentaje (%)',
  UI: 'Unidad internacional (UI)',
  mEq: 'Miliequivalente (mEq)',
  mmol: 'Milimol (mmol)',
  'mg/ml': 'Miligramo por mililitro (mg/ml)',
  'mg/5 ml': 'Miligramo por 5 mililitros (mg/5 ml)',
  'UI/ml': 'Unidad internacional por mililitro (UI/ml)',
};

/** `MedicamentoResponse`. `stockBajo` y `vencido` los calcula el backend. */
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

/** Rol de un usuario de acceso (`/api/auth/*`). */
export type Rol = 'ADMIN' | 'DOCTOR' | 'ENFERMERO';

export type Permiso =
  | 'pacientes.escribir'
  | 'pacientes.habitacion'
  | 'pacientes.eliminar'
  | 'historial.crear'
  | 'historial.diagnosticar'
  | 'trabajadores.leer'
  | 'trabajadores.escribir'
  | 'medicamentos.escribir'
  | 'medicamentos.salidas'
  | 'usuarios.gestionar';
