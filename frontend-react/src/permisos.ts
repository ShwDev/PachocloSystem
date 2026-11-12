import type { Permiso, Rol } from './tipos';

/*
 * Copia de la tabla "Permisos por rol" del backend (la misma que usa
 * `frontend/src/app/core/permisos.ts`). Solo sirve para ocultar acciones; quien
 * decide sigue siendo el backend y un 403 se muestra como cualquier error.
 */
const PERMISOS: Readonly<Record<Rol, ReadonlySet<Permiso>>> = {
  ADMIN: new Set<Permiso>([
    'pacientes.escribir',
    'pacientes.habitacion',
    'pacientes.eliminar',
    'trabajadores.leer',
    'trabajadores.escribir',
    'medicamentos.escribir',
    'medicamentos.salidas',
    'usuarios.gestionar',
  ]),
  DOCTOR: new Set<Permiso>([
    'pacientes.escribir',
    'pacientes.habitacion',
    'historial.crear',
    'historial.diagnosticar',
    'trabajadores.leer',
  ]),
  ENFERMERO: new Set<Permiso>(['pacientes.habitacion', 'historial.crear', 'medicamentos.salidas']),
};

export function tienePermiso(rol: Rol | null, permiso: Permiso): boolean {
  return rol ? PERMISOS[rol].has(permiso) : false;
}
