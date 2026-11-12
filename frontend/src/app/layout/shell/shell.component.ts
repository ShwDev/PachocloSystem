import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Permiso, PermisosService } from '../../core/permisos';
import { etiquetaRol } from '../../core/roles';

interface Seccion {
  readonly ruta: string;
  readonly etiqueta: string;
  readonly icono: string;
  /** Sin permiso, la sección no aparece en el menú. */
  readonly permiso?: Permiso;
}

const SECCIONES: readonly Seccion[] = [
  { ruta: '/pacientes', etiqueta: 'Pacientes', icono: 'personal_injury' },
  { ruta: '/trabajadores', etiqueta: 'Trabajadores', icono: 'badge', permiso: 'trabajadores.leer' },
  { ruta: '/medicamentos', etiqueta: 'Medicamentos', icono: 'medication' },
  { ruta: '/historial', etiqueta: 'Historial clínico', icono: 'history_edu' },
  {
    ruta: '/usuarios',
    etiqueta: 'Usuarios',
    icono: 'manage_accounts',
    permiso: 'usuarios.gestionar',
  },
];

/** Marco de la aplicación autenticada: barra superior, menú lateral y contenido. */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly permisos = inject(PermisosService);

  protected readonly usuario = this.auth.usuario;
  protected readonly etiquetaRol = etiquetaRol;

  protected readonly esMovil = toSignal(
    inject(BreakpointObserver)
      .observe(Breakpoints.Handset)
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );

  /** Secciones del menú que el rol del usuario puede abrir. */
  protected readonly secciones = computed(() =>
    SECCIONES.filter((s) => !s.permiso || this.permisos.puede(s.permiso)),
  );

  protected cerrarSesion(): void {
    this.auth.cerrarSesion();
  }
}
