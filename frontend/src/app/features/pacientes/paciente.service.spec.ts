import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Paciente } from './paciente.models';
import { PacienteService } from './paciente.service';

describe('PacienteService', () => {
  let servicio: PacienteService;
  let http: HttpTestingController;
  const ana: Paciente = { idPaciente: 'PAC-0001', nombre: 'Ana Ruiz', edad: 40, habitacion: 12 };
  const datos = { nombre: 'Ana Ruiz', edad: 40, habitacion: 12 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(PacienteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista sin filtro', () => {
    let recibido: Paciente[] = [];
    servicio.listar().subscribe((l) => (recibido = l));

    const peticion = http.expectOne('/api/pacientes');
    expect(peticion.request.params.has('q')).toBe(false);
    peticion.flush([ana]);

    expect(recibido).toEqual([ana]);
  });

  it('lista con filtro recortado y omite el filtro en blanco', () => {
    servicio.listar('  ana ').subscribe();
    servicio.listar('   ').subscribe();

    const reqFiltro = http.expectOne('/api/pacientes?q=ana');
    expect(reqFiltro.request.method).toBe('GET');
    reqFiltro.flush([]);

    const reqTodos = http.expectOne('/api/pacientes');
    expect(reqTodos.request.method).toBe('GET');
    reqTodos.flush([]);
  });

  it('obtiene un paciente', () => {
    let recibido: Paciente | undefined;
    servicio.obtener('PAC-0001').subscribe((p) => (recibido = p));

    http.expectOne('/api/pacientes/PAC-0001').flush(ana);

    expect(recibido).toEqual(ana);
  });

  it('registra con POST', () => {
    servicio.registrar(datos).subscribe();

    const peticion = http.expectOne('/api/pacientes');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(datos);
    peticion.flush(ana);
  });

  it('edita con PUT', () => {
    servicio.editar('PAC-0001', datos).subscribe();

    const peticion = http.expectOne('/api/pacientes/PAC-0001');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual(datos);
    peticion.flush(ana);
  });

  it('cambia la habitación con PATCH', () => {
    servicio.cambiarHabitacion('PAC-0001', 30).subscribe();

    const peticion = http.expectOne('/api/pacientes/PAC-0001/habitacion');
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body).toEqual({ habitacion: 30 });
    peticion.flush(ana);
  });

  it('elimina con DELETE', () => {
    servicio.eliminar('PAC-0001').subscribe();

    const peticion = http.expectOne('/api/pacientes/PAC-0001');
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null, { status: 204, statusText: 'No Content' });
  });
});
