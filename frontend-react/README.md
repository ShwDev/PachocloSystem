# Frontend React de Medicamentos

Aplicación **independiente** en React 19 + TypeScript (Vite) que solo gestiona el inventario de
medicamentos de PachocloSystem. Es una alternativa a la pantalla **Medicamentos** de la SPA Angular
([`frontend/`](../frontend/)) y no depende de ella: tiene su propio `package.json`, su propio login
y su propio despliegue.

Como la SPA Angular, consume **solo** la API de PachocloSystem (`/api/auth/**` y
`/api/medicamentos/**`); nunca llama a MedicamentosService.

## Requisitos

- **Node** `^22.22.3`, `^24.15.0` o `>=26`, con npm.
- PachocloSystem en `http://localhost:8080` y MedicamentosService en el 8081 (ver el
  [README principal](../README.md#cómo-levantar-el-sistema)).

## Cómo arrancar

```bash
npm install
npm run dev        # http://localhost:5173
```

En desarrollo Vite reenvía `/api` a `http://localhost:8080` (ver `vite.config.ts`), así que hay un
único origen y el backend **no necesita CORS**. Para apuntar a otro backend local:
`API_PROXY_TARGET=http://localhost:9090 npm run dev`.

Se entra con las mismas cuentas que en la SPA Angular (por ejemplo, el `admin` inicial). Un usuario
que todavía debe cambiar su contraseña temporal tiene que hacerlo antes en la SPA Angular: el
backend le bloquea el resto de la API.

## Comandos

```bash
npm run dev             # servidor de desarrollo con recarga en caliente
npm test                # tests (Vitest + Testing Library, en jsdom)
npm run typecheck       # comprobación de tipos (tsc)
npm run format:check    # Prettier (npm run format para corregir)
npm run build           # tipos + build de producción en dist/
npm run preview         # sirve dist/ en http://localhost:4173
```

## Estructura

```
src/
├── main.tsx              punto de entrada: monta <App> en #root
├── App.tsx               sesión: login o inventario, cierre por expiración o 401
├── Login.tsx             formulario de inicio de sesión
├── auth.ts               POST /api/auth/login + GET /api/auth/me
├── sesion.ts             sesión en sessionStorage (token, expiración, usuario)
├── api.ts                cliente HTTP de /api/medicamentos y errores de la API
├── Medicamentos.tsx      inventario: pestañas, búsqueda, tabla y acciones por rol
├── Dialogo*.tsx, Modal   alta/edición, movimientos de stock y baja
├── permisos.ts, tipos.ts copia de los permisos por rol y de los DTO del backend
└── estilos.css           estilos de toda la app
```

## Sesión y permisos

- El token JWT se guarda en `sessionStorage` (`pachoclosystem.medicamentos-react.sesion`): sobrevive
  a una recarga y se pierde al cerrar la pestaña. No hay refresh token: al expirar, o si la API
  responde 401, la sesión se cierra y se vuelve al login explicando el motivo.
- Los permisos por rol (`medicamentos.escribir` para ADMIN y `medicamentos.salidas` para ADMIN y
  ENFERMERO) son una copia de los de la SPA Angular y solo sirven para ocultar botones; quien
  decide sigue siendo el backend.
- Las salidas de stock envían `Idempotency-Key`, igual que la SPA Angular.

## Docker

`Dockerfile` compila con Node y sirve `dist/` con nginx sin privilegios en el 8080. `nginx.conf`
reenvía `/api/` a `pachoclo-api:8080`, así que en el compose del repositorio la app se abre en
<http://localhost:4300> (`FRONTEND_REACT_PORT`) sin CORS. Su nginx tiene IP fija
(`NGINX_REACT_IP`, por defecto `172.28.10.11`) y está en `APP_LOGIN_PROXIES_CONFIABLES`, igual que
el de Angular.

## Despliegue en otro origen

Si la app se publica en un dominio distinto al de la API (p. ej. Vercel + Railway), compile con
`VITE_API_BASE_URL=https://<api>` (sin barra final). En ese caso `CORS_ORIGENES` de PachocloSystem
debe incluir el origen de esta app.
