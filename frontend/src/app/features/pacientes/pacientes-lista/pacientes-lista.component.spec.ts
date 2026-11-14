import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { Permiso, PermisosService, tienePermiso } from '../../../core/permisos';
import { Rol } from '../../../core/roles';
import { ApiError } from '../../../core/http/api-error';
import { NotificacionService } from '../../../core/notificacion.service';
import { ConfirmacionDialogoComponent } from '../../../shared/confirmacion-dialogo/confirmacion-dialogo.component';
import { PacienteDialogoComponent } from '../paciente-dialogo/paciente-dialogo.component';
import { Paciente } from '../paciente.models';
import { PacienteService } from '../paciente.service';
import { ESPERA_BUSQUEDA_MS } from '../../../shared/listas';
import { PacientesListaComponent } from './pacientes-lista.component';

describe('PacientesListaComponent', () => {
  /** Rol de los permisos simulados; ADMIN (todo) salvo en los tests de rol. */
  let rol: Rol = 'ADMIN';
  const ana: Paciente = { idPaciente: 'PAC-0001', nombre: 'Ana Ruiz', edad: 40, habitacion: 12 };
  const luis: Paciente = { idPaciente: 'PAC-0002', nombre: 'Luis Gil', edad: 7, habitacion: 3 };

  const servicio = {
    listar: vi.fn<(q?: string) => Observable<Paciente[]>>(),
    eliminar: vi.fn<(id: string) => Observable<void>>(),
  };
  const dialogo = { open: vi.fn() };
  const notificaciones = { exito: vi.fn(), error: vi.fn() };

  /** Simula que el próximo diálogo se cierra con `resultado`. */
  const alCerrarDialogo = (resultado: unknown) =>
    dialogo.open.mockReturnValueOnce({ afterClosed: () => of(resultado) });

  async function renderizar() {
    TestBed.configureTestingModule({
      imports: [PacientesListaComponent],
      providers: [
        provideRouter([]),
        { provide: PacienteService, useValue: servicio },
        { provide: MatDialog, useValue: dialogo },
        { provide: NotificacionService, useValue: notificaciones },
        { provide: PermisosService, useValue: { puede: (p: Permiso) => tienePermiso(rol, p) } },
      ],
    });
    const fixture = TestBed.createComponent(PacientesListaComponent);
    await fixture.whenStable();
    const elemento = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      elemento,
      filas: () =>
        Array.from(elemento.querySelectorAll('tr[mat-row]')).map((f) =>
          Array.from(f.querySelectorAll('td'))
            .slice(0, 4)
            .map((c) => c.textContent?.trim()),
        ),
      boton: (etiqueta: string) =>
        elemento.querySelector<HTMLButtonElement>(`button[aria-label="${etiqueta}"]`)!,
      textoEstado: () => elemento.querySelector('.lista-estado')?.textContent?.trim(),
    };
  }

  beforeEach(() => {
    vi.resetAllMocks();
    rol = 'ADMIN';
    servicio.listar.mockReturnValue(of([ana, luis]));
  });

  it('carga y muestra los pacientes', async () => {
    const { filas } = await renderizar();

    expect(servicio.listar).toHaveBeenCalledWith('');
    expect(filas()).toEqual([
      ['PAC-0001', 'Ana Ruiz', '40', '12'],
      ['PAC-0002', 'Luis Gil', '7', '3'],
    ]);
  });

  it('enlaza al historial clínico de cada paciente', async () => {
    const { elemento } = await renderizar();

    const enlace = elemento.querySelector('a[aria-label="Historial clínico de Ana Ruiz"]');
    expect(enlace?.getAttribute('href')).toBe('/pacientes/PAC-0001/historial');
  });

  it('el enfermero solo ve el historial y el cambio de habitación', async () => {
    rol = 'ENFERMERO';
    const { elemento, boton } = await renderizar();

    expect(elemento.querySelector('.lista-encabezado button')).toBeNull();
    expect(boton('Editar a Ana Ruiz')).toBeNull();
    expect(boton('Desactivar a Ana Ruiz')).toBeNull();
    expect(boton('Cambiar habitación de Ana Ruiz')).not.toBeNull();
    expect(elemento.querySelector('a[aria-label="Historial clínico de Ana Ruiz"]')).not.toBeNull();
  });

  it('el doctor registra y edita, pero no elimina', async () => {
    rol = 'DOCTOR';
    const { elemento, boton } = await renderizar();

    expect(elemento.querySelector('.lista-encabezado button')).not.toBeNull();
    expect(boton('Editar a Ana Ruiz')).not.toBeNull();
    expect(boton('Desactivar a Ana Ruiz')).toBeNull();
  });

  it('indica que no hay pacientes', async () => {
    servicio.listar.mockReturnValue(of([]));

    const { textoEstado } = await renderizar();

    expect(textoEstado()).toBe('Todavía no hay pacientes registrados.');
  });

  it('busca en el servidor tras dejar de escribir', async () => {
    const { fixture, elemento, textoEstado } = await renderizar();
    servicio.listar.mockReturnValue(of([]));

    const input = elemento.querySelector<HTMLInputElement>('input[type="search"]')!;
    input.value = ' ana ';
    input.dispatchEvent(new Event('input'));
    await new Promise((r) => setTimeout(r, ESPERA_BUSQUEDA_MS + 50));
    await fixture.whenStable();

    expect(servicio.listar).toHaveBeenLastCalledWith('ana');
    expect(textoEstado()).toBe('Ningún paciente coincide con «ana».');
  });

  it('muestra el error de carga y permite reintentar', async () => {
    servicio.listar.mockReturnValueOnce(
      throwError(() => new ApiError(0, ['No se pudo conectar con el servidor.'])),
    );
    const { fixture, elemento, filas } = await renderizar();

    const error = elemento.querySelector('[role="alert"]')!;
    expect(error.textContent).toContain('No se pudo conectar con el servidor.');
    expect(filas()).toEqual([]);

    error.querySelector('button')!.click();
    await fixture.whenStable();

    expect(servicio.listar).toHaveBeenCalledTimes(2);
    expect(elemento.querySelector('[role="alert"]')).toBeNull();
    expect(filas()).toHaveLength(2);
  });

  it('desactiva (baja lógica) tras confirmar, avisa y recarga', async () => {
    servicio.eliminar.mockReturnValue(of(undefined));
    alCerrarDialogo(true);
    const { fixture, boton } = await renderizar();

    boton('Desactivar a Ana Ruiz').click();
    await fixture.whenStable();

    const [componente, config] = dialogo.open.mock.calls[0];
    expect(componente).toBe(ConfirmacionDialogoComponent);
    expect(config.data.titulo).toBe('Desactivar paciente');
    expect(config.data.mensaje).toContain('baja lógica');
    expect(config.data.mensaje).toContain('su historial clínico se conservan');
    expect(servicio.eliminar).toHaveBeenCalledWith('PAC-0001');
    expect(notificaciones.exito).toHaveBeenCalledWith(
      'Paciente PAC-0001 desactivado. Su historial clínico se conserva.',
    );
    expect(servicio.listar).toHaveBeenCalledTimes(2);
  });

  it('no elimina si se cancela la confirmación', async () => {
    alCerrarDialogo(false);
    const { fixture, boton } = await renderizar();

    boton('Desactivar a Ana Ruiz').click();
    await fixture.whenStable();

    expect(servicio.eliminar).not.toHaveBeenCalled();
  });

  it('avisa si falla la eliminación', async () => {
    servicio.eliminar.mockReturnValue(
      throwError(() => new ApiError(404, ['No se encontró el paciente PAC-0001.'])),
    );
    alCerrarDialogo(true);
    const { fixture, boton } = await renderizar();

    boton('Desactivar a Ana Ruiz').click();
    await fixture.whenStable();

    expect(notificaciones.error).toHaveBeenCalledWith(['No se encontró el paciente PAC-0001.']);
    expect(servicio.listar).toHaveBeenCalledTimes(2);
  });

  it('tras registrar en el diálogo, avisa y recarga', async () => {
    const nuevo: Paciente = { ...ana, idPaciente: 'PAC-0003' };
    alCerrarDialogo(nuevo);
    const { fixture, elemento } = await renderizar();

    elemento.querySelector<HTMLButtonElement>('.lista-encabezado button')!.click();
    await fixture.whenStable();

    expect(dialogo.open).toHaveBeenCalledWith(PacienteDialogoComponent, expect.anything());
    expect(notificaciones.exito).toHaveBeenCalledWith('Paciente PAC-0003 registrado.');
    expect(servicio.listar).toHaveBeenCalledTimes(2);
  });

  it('si el diálogo se cancela, no recarga', async () => {
    alCerrarDialogo(undefined);
    const { fixture, boton } = await renderizar();

    boton('Editar a Ana Ruiz').click();
    await fixture.whenStable();

    expect(dialogo.open.mock.calls[0][1].data).toEqual({ paciente: ana });
    expect(notificaciones.exito).not.toHaveBeenCalled();
    expect(servicio.listar).toHaveBeenCalledTimes(1);
  });
});
