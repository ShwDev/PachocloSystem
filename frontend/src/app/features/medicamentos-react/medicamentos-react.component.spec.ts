import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/http/api-base-url.interceptor';
import { MedicamentosReactComponent } from './medicamentos-react.component';

const ETIQUETA = 'pachoclo-medicamentos-react';

describe('MedicamentosReactComponent', () => {
  beforeAll(() => {
    if (!customElements.get(ETIQUETA)) {
      customElements.define(ETIQUETA, class extends HTMLElement {});
    }
  });

  it('monta el custom element con el origen de la API', async () => {
    TestBed.configureTestingModule({
      imports: [MedicamentosReactComponent],
      providers: [{ provide: API_BASE_URL, useValue: 'https://api.test' }],
    });
    const fixture = TestBed.createComponent(MedicamentosReactComponent);
    const elemento = fixture.nativeElement as HTMLElement;

    fixture.detectChanges();
    await fixture.whenStable();
    await Promise.resolve();
    fixture.detectChanges();

    const montado = elemento.querySelector(ETIQUETA);
    expect(montado).not.toBeNull();
    expect(montado?.getAttribute('api-base')).toBe('https://api.test');
    expect(elemento.querySelector('.estado-carga')).toBeNull();
  });

  it('elimina la interfaz montada al destruir la ruta', async () => {
    TestBed.configureTestingModule({
      imports: [MedicamentosReactComponent],
      providers: [{ provide: API_BASE_URL, useValue: '' }],
    });
    const fixture = TestBed.createComponent(MedicamentosReactComponent);
    const elemento = fixture.nativeElement as HTMLElement;

    fixture.detectChanges();
    await fixture.whenStable();
    await Promise.resolve();
    fixture.detectChanges();

    expect(elemento.querySelector(ETIQUETA)).not.toBeNull();
    fixture.destroy();
    expect(elemento.querySelector(ETIQUETA)).toBeNull();
  });
});
