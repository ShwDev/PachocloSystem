import { createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { crearCliente } from './api';
import estilos from './estilos.css?inline';
import { Medicamentos } from './Medicamentos';
import { leerRol } from './sesion';

export const ETIQUETA = 'pachoclo-medicamentos-react';

/**
 * Web Component que monta la interfaz React dentro de la SPA Angular.
 *
 * Angular le pasa el origen de la API con el atributo `api-base` (vacío si la
 * API está en el mismo origen). El token se lee de la sesión compartida en cada
 * petición. El Shadow DOM aísla los estilos de Angular Material.
 */
export class PachocloMedicamentosReact extends HTMLElement {
  private root: Root | null = null;

  static get observedAttributes(): string[] {
    return ['api-base'];
  }

  connectedCallback(): void {
    this.montar();
    this.renderizar();
  }

  disconnectedCallback(): void {
    this.root?.unmount();
    this.root = null;
    this.shadowRoot?.replaceChildren();
  }

  attributeChangedCallback(): void {
    if (this.isConnected) {
      this.renderizar();
    }
  }

  private montar(): void {
    if (this.root) {
      return;
    }
    const shadow = this.shadowRoot ?? this.attachShadow({ mode: 'open' });
    shadow.replaceChildren();

    const hoja = document.createElement('style');
    hoja.textContent = estilos;
    shadow.appendChild(hoja);

    const contenedor = document.createElement('div');
    contenedor.className = 'raiz-react';
    shadow.appendChild(contenedor);

    this.root = createRoot(contenedor);
  }

  private renderizar(): void {
    const apiBaseUrl = this.getAttribute('api-base') ?? '';
    this.root?.render(
      createElement(Medicamentos, {
        cliente: crearCliente({ apiBaseUrl }),
        rol: leerRol(),
      }),
    );
  }
}

if (!customElements.get(ETIQUETA)) {
  customElements.define(ETIQUETA, PachocloMedicamentosReact);
}
