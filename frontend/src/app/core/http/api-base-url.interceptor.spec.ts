import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from '../auth/auth.interceptor';
import { AuthService, URL_LOGIN } from '../auth/auth.service';
import { API_BASE_URL, apiBaseUrlInterceptor } from './api-base-url.interceptor';
import { ApiError } from './api-error';
import { errorInterceptor } from './error.interceptor';

const API = 'https://pachoclosystem.up.railway.app';

describe('apiBaseUrlInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const token = signal<string | null>(null);
  const auth = {
    token,
    autenticado: () => token() !== null,
    cerrarSesion: vi.fn(),
  };

  function configurar(base: string): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([authInterceptor, errorInterceptor, apiBaseUrlInterceptor]),
        ),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: API_BASE_URL, useValue: base },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'url', 'get').mockReturnValue('/pacientes');
  }

  beforeEach(() => {
    token.set(null);
    auth.cerrarSesion.mockReset();
  });

  afterEach(() => backend.verify());

  it('sin URL base, las peticiones a la API siguen siendo relativas (mismo origen)', () => {
    configurar('');

    http.get('/api/pacientes').subscribe();

    const req = backend.expectOne('/api/pacientes');
    expect(req.request.method).toBe('GET');
  });

  it('con URL base, envía la API a ese origen con el token', () => {
    configurar(API);
    token.set('tkn');

    http.get('/api/pacientes?q=ana').subscribe();

    const peticion = backend.expectOne(`${API}/api/pacientes?q=ana`).request;
    expect(peticion.headers.get('Authorization')).toBe('Bearer tkn');
  });

  it('el login va al origen de la API sin token', () => {
    configurar(API);
    token.set('tkn');

    http.post(URL_LOGIN, {}).subscribe();

    expect(backend.expectOne(`${API}${URL_LOGIN}`).request.headers.has('Authorization')).toBe(
      false,
    );
  });

  it('no toca lo que no es de la API ni las URL absolutas', () => {
    configurar(API);
    token.set('tkn');

    http.get('/assets/x.json').subscribe();
    http.get('https://otro.example.com/api/x').subscribe();

    expect(backend.expectOne('/assets/x.json').request.headers.has('Authorization')).toBe(false);
    expect(
      backend.expectOne('https://otro.example.com/api/x').request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('un 401 de la API en otro origen cierra la sesión como siempre', () => {
    configurar(API);
    token.set('tkn');
    let recibido: unknown;

    http.get('/api/pacientes').subscribe({ error: (e: unknown) => (recibido = e) });
    backend
      .expectOne(`${API}/api/pacientes`)
      .flush(
        { status: 401, error: 'Unauthorized', mensajes: ['Debe autenticarse.'] },
        { status: 401, statusText: 'x' },
      );

    expect(recibido).toBeInstanceOf(ApiError);
    expect(auth.cerrarSesion).toHaveBeenCalledWith({ motivo: 'expirada', returnUrl: '/pacientes' });
  });
});
