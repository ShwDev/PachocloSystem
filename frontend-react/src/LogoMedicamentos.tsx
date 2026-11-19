/** Logotipo propio de la interfaz React de Medicamentos. */
export function LogoMedicamentos({ tamano = 32 }: { tamano?: number }) {
  return (
    <svg
      className="marca-logo"
      width={tamano}
      height={tamano}
      viewBox="0 0 64 64"
      fill="none"
      aria-hidden="true"
    >
      <rect width="64" height="64" rx="16" fill="#00639b" />
      <rect
        x="4"
        y="4"
        width="56"
        height="56"
        rx="13"
        stroke="#38bdf8"
        strokeOpacity="0.55"
        strokeWidth="2"
      />
      <g transform="rotate(-35 32 32)">
        <rect x="14" y="22" width="36" height="20" rx="10" fill="#ffffff" />
        <path d="M32 22h8a10 10 0 0 1 0 20h-8V22z" fill="#38bdf8" />
        <line x1="32" y1="22" x2="32" y2="42" stroke="#00639b" strokeWidth="2" />
      </g>
      <circle cx="49" cy="15" r="8" fill="#0b3d1c" stroke="#ffffff" strokeWidth="2" />
      <path d="M49 11v8M45 15h8" stroke="#ffffff" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
