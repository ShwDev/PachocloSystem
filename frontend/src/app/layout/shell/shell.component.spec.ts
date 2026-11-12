import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Usuario } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  const doctora: Usuario = {
    idUsuario: 'USR-0002',
    username: 'ana.ruiz',
    rol: 'DOCTOR',
    idTrabajador: 'DOC-0001',
    activo: true,
    debeCambiarPassword: false,
  };
  const usuario = signal<Usuario | null>(doctora);
  const auth = { usuario, cerrarSesion: vi.fn() };

  beforeEach(() => {
    auth.cerrarSesion.mockReset();
    usuario.set(doctora);
    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  async function renderizar(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(ShellComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra un enlace por cada módulo', async () => {
    const elemento = await renderizar();

    const enlaces = Array.from(elemento.querySelectorAll('mat-nav-list a'));

    expect(enlaces.map((a) => a.getAttribute('href'))).toEqual([
      '/pacientes',
      '/trabajadores',
      '/medicamentos',
      '/historial',
    ]);
  });

  it('oculta las secciones que el rol no puede abrir (el enfermero no ve Trabajadores)', async () => {
    usuario.set({ ...doctora, rol: 'ENFERMERO', idTrabajador: 'ENF-0001' });

    const elemento = await renderizar();

    const enlaces = Array.from(elemento.querySelectorAll('mat-nav-list a'));
    expect(enlaces.map((a) => a.getAttribute('href'))).toEqual([
      '/pacientes',
      '/medicamentos',
      '/historial',
    ]);
  });

  it('el administrador ve además Usuarios', async () => {
    usuario.set({ ...doctora, rol: 'ADMIN', idTrabajador: null });

    const elemento = await renderizar();

    const enlaces = Array.from(elemento.querySelectorAll('mat-nav-list a'));
    expect(enlaces.map((a) => a.getAttribute('href'))).toContain('/usuarios');
  });

  it('muestra el usuario y su rol', async () => {
    const elemento = await renderizar();

    expect(elemento.querySelector('.usuario')?.textContent).toContain('ana.ruiz · Doctor');
  });

  it('cierra la sesión desde el menú de usuario', async () => {
    const fixture = TestBed.createComponent(ShellComponent);
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.usuario')!.click();
    await fixture.whenStable();
    // El menú se abre en un overlay fuera del componente.
    const opciones = Array.from(document.querySelectorAll<HTMLElement>('[mat-menu-item]'));
    expect(opciones[0].getAttribute('href')).toBe('/cambiar-password');
    opciones.find((o) => o.textContent?.includes('Cerrar sesión'))!.click();

    expect(auth.cerrarSesion).toHaveBeenCalled();
  });
});
