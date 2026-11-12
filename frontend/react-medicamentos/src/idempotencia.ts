/**
 * Clave `Idempotency-Key` de un intento de salida de stock. El backend exige de
 * 16 a 100 caracteres: letras sin tilde, dígitos, guion o guion bajo. Es la
 * misma generación que `shared/uuid.ts` de la SPA Angular.
 */
export function nuevaClaveIdempotencia(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
