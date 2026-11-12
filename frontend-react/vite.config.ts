import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

/**
 * Aplicación React independiente de Medicamentos.
 *
 * En desarrollo (`npm run dev`) Vite reenvía `/api` a PachocloSystem en el 8080,
 * igual que `proxy.conf.json` del frontend Angular: un solo origen, sin CORS.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env['API_PROXY_TARGET'] ?? 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  preview: {
    port: 4173,
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    css: true,
    restoreMocks: true,
  },
});
