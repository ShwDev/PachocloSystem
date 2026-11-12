const FECHA_ISO = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * "2027-01-31" → "31/01/2027". Igual que en la SPA Angular, la fecha se trata
 * como texto: nunca se pasa por `Date`, que la interpretaría como medianoche UTC
 * y en zonas como UTC-5 mostraría el día anterior.
 */
export function formatearFecha(fecha: string): string {
  const partes = FECHA_ISO.exec(fecha);
  return partes ? `${partes[3]}/${partes[2]}/${partes[1]}` : fecha;
}
