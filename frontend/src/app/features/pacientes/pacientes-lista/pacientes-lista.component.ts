import { ComponentType } from '@angular/cdk/portal';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { mensajesDeError } from '../../../core/http/api-error';
import { NotificacionService } from '../../../core/notificacion.service';
import { PermisosService } from '../../../core/permisos';
import { confirmar } from '../../../shared/confirmacion-dialogo/confirmacion-dialogo.component';
import { crearListaRemota, textoBuscado } from '../../../shared/listas';
import { HabitacionDialogoComponent } from '../habitacion-dialogo/habitacion-dialogo.component';
import { PacienteDialogoComponent } from '../paciente-dialogo/paciente-dialogo.component';
import { Paciente } from '../paciente.models';
import { PacienteService } from '../paciente.service';

/**
 * Lista de pacientes con búsqueda en el servidor. Las altas y ediciones se
 * hacen en diálogos que guardan ellos mismos; al cerrarse con éxito, la lista
 * se recarga.
 */
@Component({
  selector: 'app-pacientes-lista',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './pacientes-lista.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacientesListaComponent {
  private readonly servicio = inject(PacienteService);
  private readonly dialogo = inject(MatDialog);
  private readonly notificaciones = inject(NotificacionService);
  /** Qué acciones mostrar según el rol (el backend sigue siendo quien decide). */
  protected readonly permisos = inject(PermisosService);

  protected readonly columnas = ['idPaciente', 'nombre', 'edad', 'habitacion', 'acciones'];
  protected readonly busqueda = new FormControl('', { nonNullable: true });
  private readonly lista = crearListaRemota({
    inicial: '',
    cambios: textoBuscado(this.busqueda),
    cargar: (texto: string) => this.servicio.listar(texto),
  });
  protected readonly pacientes = this.lista.elementos;
  protected readonly cargando = this.lista.cargando;
  protected readonly errores = this.lista.errores;
  /** Filtro de la última carga, para el mensaje de lista vacía. */
  protected readonly filtro = this.lista.consulta;

  protected recargar(): void {
    this.lista.recargar();
  }

  protected nuevo(): void {
    this.abrirDialogo(PacienteDialogoComponent, {}, (p) => `Paciente ${p.idPaciente} registrado.`);
  }

  protected editar(paciente: Paciente): void {
    this.abrirDialogo(
      PacienteDialogoComponent,
      { paciente },
      (p) => `Paciente ${p.idPaciente} actualizado.`,
    );
  }

  protected cambiarHabitacion(paciente: Paciente): void {
    this.abrirDialogo(
      HabitacionDialogoComponent,
      { paciente },
      (p) => `${p.nombre} pasa a la habitación ${p.habitacion}.`,
    );
  }

  protected eliminar(paciente: Paciente): void {
    confirmar(this.dialogo, {
      titulo: 'Desactivar paciente',
      mensaje:
        `Se desactivará a ${paciente.nombre} (${paciente.idPaciente}). No se borra nada: es ` +
        'una baja lógica, así que sus datos y su historial clínico se conservan en la base de ' +
        'datos. Dejará de aparecer en la lista, en las búsquedas y en el historial general, y no ' +
        'se puede reactivar desde la aplicación.',
      accion: 'Desactivar',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.servicio.eliminar(paciente.idPaciente)),
      )
      .subscribe({
        next: () => {
          this.notificaciones.exito(
            `Paciente ${paciente.idPaciente} desactivado. Su historial clínico se conserva.`,
          );
          this.recargar();
        },
        error: (error: unknown) => {
          this.notificaciones.error(mensajesDeError(error));
          this.recargar();
        },
      });
  }

  /** Abre un diálogo que guarda por su cuenta; si se cierra con un paciente, avisa y recarga. */
  private abrirDialogo<C, D>(
    componente: ComponentType<C>,
    datos: D,
    mensajeExito: (guardado: Paciente) => string,
  ): void {
    this.dialogo
      .open<C, D, Paciente>(componente, { data: datos, width: '480px', maxWidth: '95vw' })
      .afterClosed()
      // Solo un paciente cuenta: cerrar sin guardar no avisa nada.
      .pipe(
        filter(
          (guardado): guardado is Paciente => typeof guardado === 'object' && guardado !== null,
        ),
      )
      .subscribe((guardado) => {
        this.notificaciones.exito(mensajeExito(guardado));
        this.recargar();
      });
  }
}
