import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { API_BASE_URL } from '../../core/http/api-base-url.interceptor';

const ETIQUETA = 'pachoclo-medicamentos-react';
const RUTA_SCRIPT = 'react/pachoclo-medicamentos.js';

/** Carga única del bundle: la comparten todas las visitas a la ruta. */
let cargaDelBundle: Promise<void> | null = null;

function cargarBundle(): Promise<void> {
  if (customElements.get(ETIQUETA)) {
    return Promise.resolve();
  }
  if (cargaDelBundle) {
    return cargaDelBundle;
  }

  cargaDelBundle = new Promise<void>((resolver, rechazar) => {
    const script = document.createElement('script');
    script.src = new URL(RUTA_SCRIPT, document.baseURI).href;
    script.async = true;
    script.onload = () => {
      if (customElements.get(ETIQUETA)) {
        resolver();
      } else {
        rechazar(new Error(`El bundle no registró <${ETIQUETA}>`));
      }
    };
    script.onerror = () => rechazar(new Error(`No se pudo cargar ${script.src}`));
    document.head.appendChild(script);
  }).catch((error: unknown) => {
    cargaDelBundle = null;
    throw error;
  });

  return cargaDelBundle;
}

/**
 * Ruta Angular que monta la segunda interfaz de Medicamentos, escrita en React
 * y compilada como Web Component. Le pasa el origen de la API por el atributo
 * `api-base`; la sesión la lee el propio componente de `sessionStorage`.
 */
@Component({
  selector: 'app-medicamentos-react',
  template: `
    <div #contenedor class="host"></div>
    @if (cargando()) {
      <p class="estado-carga" role="status">Cargando la interfaz de React…</p>
    }
    @if (error(); as mensaje) {
      <div class="error-carga" role="alert">
        <p>{{ mensaje }}</p>
        <p>Recargue la página para volver a intentarlo.</p>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .estado-carga {
      padding: 24px 0;
      color: var(--mat-sys-on-surface-variant);
    }

    .error-carga {
      padding: 16px;
      border-radius: 8px;
      background-color: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }

    .error-carga p {
      margin: 4px 0;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MedicamentosReactComponent implements AfterViewInit, OnDestroy {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly contenedor = viewChild.required<ElementRef<HTMLElement>>('contenedor');
  private elemento: HTMLElement | null = null;
  private vivo = true;

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  async ngAfterViewInit(): Promise<void> {
    try {
      await cargarBundle();
      if (!this.vivo) {
        return;
      }
      const elemento = document.createElement(ETIQUETA);
      elemento.setAttribute('api-base', this.apiBaseUrl);
      this.contenedor().nativeElement.appendChild(elemento);
      this.elemento = elemento;
      this.cargando.set(false);
    } catch {
      if (this.vivo) {
        this.error.set('No se pudo cargar la interfaz de medicamentos en React.');
        this.cargando.set(false);
      }
    }
  }

  ngOnDestroy(): void {
    this.vivo = false;
    this.elemento?.remove();
    this.elemento = null;
  }
}
