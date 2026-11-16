import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RegistroRequest } from './historial.models';
import { HistorialService } from './historial.service';

describe('HistorialService', () => {
  let servicio: HistorialService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(HistorialService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista el historial general con filtro y, si hay, texto recortado', () => {
    servicio.listar('autor', ' ana ').subscribe();
    servicio.listar('todos', '  ').subscribe();

    const reqAutor = http.expectOne('/api/historial?filtro=autor&q=ana');
    expect(reqAutor.request.method).toBe('GET');
    reqAutor.flush([]);

    const reqTodos = http.expectOne('/api/historial?filtro=todos');
    expect(reqTodos.request.method).toBe('GET');
    reqTodos.flush([]);
  });

  it('lista el historial de un paciente, opcionalmente filtrado por autor', () => {
    servicio.listarDePaciente('PAC-0001').subscribe();
    servicio.listarDePaciente('PAC-0001', 'DOC').subscribe();

    const reqPaciente = http.expectOne('/api/pacientes/PAC-0001/historial');
    expect(reqPaciente.request.method).toBe('GET');
    reqPaciente.flush([]);

    const reqFiltrado = http.expectOne('/api/pacientes/PAC-0001/historial?q=DOC');
    expect(reqFiltrado.request.method).toBe('GET');
    reqFiltrado.flush([]);
  });

  it('devuelve los registros del más reciente al más antiguo', () => {
    const registro = (idRegistro: string, fecha: string) => ({ idRegistro, fecha });
    let general: string[] = [];
    let dePaciente: string[] = [];
    servicio.listar('todos').subscribe((r) => (general = r.map((x) => x.idRegistro)));
    servicio
      .listarDePaciente('PAC-0001')
      .subscribe((r) => (dePaciente = r.map((x) => x.idRegistro)));

    // Orden del backend (ascendente), con fracciones de segundo de distinta longitud.
    const delBackend = [
      registro('a', '2026-10-06T20:58:34'),
      registro('b', '2026-10-06T20:58:34.6751896'),
      registro('c', '2026-10-06T20:58:34.71705'),
      registro('d', '2026-10-07T08:00:00.1'),
    ];
    http.expectOne('/api/historial?filtro=todos').flush(delBackend);
    http.expectOne('/api/pacientes/PAC-0001/historial').flush(delBackend);

    expect(general).toEqual(['d', 'c', 'b', 'a']);
    expect(dePaciente).toEqual(['d', 'c', 'b', 'a']);
  });

  const registro: RegistroRequest = {
    tipo: 'MEDICACION',
    contenido: 'Paracetamol 500 mg vía oral',
    signosVitales: null,
    idMedicamento: 'MED-0001',
    cantidad: 2,
  };
  const clave = '8f14e45f-ceea-4672-a5b1-7a0c3c9e2f01';

  it('crea un registro con POST y la clave en Idempotency-Key', () => {
    servicio.crear('PAC-0001', registro, clave).subscribe();

    const peticion = http.expectOne('/api/pacientes/PAC-0001/historial');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(registro);
    expect(peticion.request.headers.get('Idempotency-Key')).toBe(clave);
    peticion.flush({});
  });

  it('una repetición (201 con Idempotency-Replayed) se entrega como un alta normal', () => {
    let recibido: unknown;
    servicio.crear('PAC-0001', registro, clave).subscribe((r) => (recibido = r));

    http
      .expectOne('/api/pacientes/PAC-0001/historial')
      .flush(
        { idRegistro: 'r1' },
        { status: 201, statusText: 'Created', headers: { 'Idempotency-Replayed': 'true' } },
      );

    expect(recibido).toEqual({ idRegistro: 'r1' });
  });

  it('las consultas del historial no llevan Idempotency-Key', () => {
    servicio.listar('todos').subscribe();
    servicio.listarDePaciente('PAC-0001').subscribe();

    for (const url of ['/api/historial?filtro=todos', '/api/pacientes/PAC-0001/historial']) {
      const peticion = http.expectOne(url);
      expect(peticion.request.headers.has('Idempotency-Key')).toBe(false);
      peticion.flush([]);
    }
  });
});
