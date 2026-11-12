import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const raiz = fileURLToPath(new URL('.', import.meta.url));

/**
 * Compila la segunda interfaz como librería IIFE que registra el custom element
 * `<pachoclo-medicamentos-react>`. La salida va a `public/react/`, que Angular
 * copia como asset y sirve junto a la SPA.
 */
export default defineConfig(({ command }) => {
  const produccion = command === 'build' && !process.env['VITEST'];

  return {
    root: raiz,
    plugins: [react()],
    publicDir: false,
    // Sin esto se empaqueta la versión de desarrollo de React (~670 kB).
    define: produccion ? { 'process.env.NODE_ENV': JSON.stringify('production') } : {},
    build: {
      outDir: fileURLToPath(new URL('../public/react', import.meta.url)),
      emptyOutDir: true,
      lib: {
        entry: fileURLToPath(new URL('./src/elemento.tsx', import.meta.url)),
        formats: ['iife'],
        name: 'PachocloMedicamentos',
        fileName: () => 'pachoclo-medicamentos.js',
      },
      sourcemap: false,
    },
    test: {
      environment: 'jsdom',
      include: ['src/**/*.test.{ts,tsx}'],
      css: true,
      restoreMocks: true,
    },
  };
});
