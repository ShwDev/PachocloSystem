# Despliegue: Railway + Vercel

Cómo está desplegado el sistema y cómo repetirlo. Los backends viven en **Railway**; el frontend, en
**Vercel**; y la base de datos, en un **MongoDB gestionado** (Atlas o cualquier proveedor que ofrezca
una cadena de conexión). Aquí solo aparecen nombres de variables y referencias: los valores secretos
se configuran en cada plataforma y nunca se guardan en el repositorio.

```
 Navegador ──► Vercel (frontend) ──https──► Railway: PachocloSystem ──red privada──► MedicamentosService
                                               │  dominio público              (sin dominio público)
                                               ▼                                      ▼
                                    MongoDB gestionado (replica set) ◄───────────────┘
                                  base "pachoclosystem" / base "medicamentos"
```

| Pieza | Plataforma | URL |
|---|---|---|
| Frontend | Vercel | <https://pachoclo-system-frontend-liard.vercel.app> |
| Frontend React (Medicamentos) | Vercel | <https://pachoclo-medicamentos-react.vercel.app> |
| PachocloSystem | Railway (público) | <https://pachoclosystem-production-2dd64b51.up.railway.app> |
| MedicamentosService | Railway (solo red privada) | `http://<RAILWAY_PRIVATE_DOMAIN>:8081` |

## Base de datos (MongoDB)

- **Replica set obligatorio.** Las transacciones y el cerrojo administrativo de PachocloSystem
  necesitan un `replicaSet`; un `mongod` suelto no sirve. Atlas lo es por defecto. Si usa el
  template de MongoDB de Railway o un servidor propio, compruebe que inicia con `--replSet` y que la
  URI lo incluye.
- **Una instancia, dos bases:** `pachoclosystem` y `medicamentos`, cada una apuntada por su servicio
  con `SPRING_MONGODB_URI`.
- **Índices al arrancar.** No hay migraciones manuales (Flyway era de PostgreSQL): MongoDB crea los
  índices declarados (`@Indexed` y los de `MongoConfig`) al arrancar cada servicio. Si la base no
  responde, el arranque falla en vez de quedarse a medias.
- **Sin autenticación no salga de la red privada.** En producción use un usuario y contraseña de
  base de datos en la URI; con `SPRING_PROFILES_ACTIVE=prod`, el servicio se niega a arrancar si
  `SPRING_MONGODB_URI` falta o apunta a localhost.

## Railway

Un proyecto con tres servicios: la base gestionada (externa al proyecto o un servicio MongoDB del
catálogo) y los dos backends, creados desde el repositorio de GitHub. Los backends se construyen con
**Railpack** (detecta el `pom.xml`); no hace falta Dockerfile.

| Ajuste | PachocloSystem | MedicamentosService |
|---|---|---|
| Root Directory | `/PachocloSystem` | `/MedicamentosService` |
| Dominio público | Sí, puerto `8080` | **No** |
| Healthcheck Path | `/actuator/health/readiness` | `/actuator/health/readiness` |
| Réplicas | **1** (límite de login e idempotencia en memoria) | 1 |

### Variables de MedicamentosService

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `RAILPACK_JDK_VERSION` | `25` (Railpack usa 21 por defecto y el proyecto exige Java 25) |
| `PORT` | `8081` (fijo, para que `MEDICAMENTOS_URL` sea estable) |
| `SPRING_MONGODB_URI` | 🔒 `mongodb+srv://<usuario>:<contraseña>@<cluster>/medicamentos?retryWrites=true&w=majority` |
| `MEDICAMENTOS_API_KEY` | 🔒 secreto, 32 caracteres o más, **igual** en los dos servicios |

### Variables de PachocloSystem

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `RAILPACK_JDK_VERSION` | `25` |
| `PORT` | `8080` |
| `SPRING_MONGODB_URI` | 🔒 `mongodb+srv://<usuario>:<contraseña>@<cluster>/pachoclosystem?retryWrites=true&w=majority` |
| `MEDICAMENTOS_URL` | `http://${{MedicamentosService.RAILWAY_PRIVATE_DOMAIN}}:8081` |
| `CORS_ORIGENES` | `https://pachoclo-system-frontend-liard.vercel.app,https://pachoclo-medicamentos-react.vercel.app,https://pachoclosystem-production-2dd64b51.up.railway.app` |
| `APP_LOGIN_PROXIES_CONFIABLES` | `100.64.0.0/10,152.233.23.0/24` (ver abajo) |
| `MEDICAMENTOS_API_KEY` | 🔒 secreto, la misma que en MedicamentosService |
| `JWT_SECRET` | 🔒 secreto, 32 bytes o más |
| `ADMIN_PASSWORD` | 🔒 secreto, solo hace falta hasta que se crea el administrador |

Notas:

- **Nombre de la propiedad:** con Spring Boot 4 la URI de MongoDB se lee de
  `spring.mongodb.uri`, y la variable de entorno equivalente es `SPRING_MONGODB_URI`. La antigua
  `spring.data.mongodb.uri` se ignora.
- La base va **en la propia URI** (la ruta después del host). No hay variables `*_DB_URL`, `*_DB_USER`
  ni `*_DB_PASSWORD`: todas se sustituyen por esa única cadena.
- `MEDICAMENTOS_URL` usa `http`: el tráfico va por la red privada de Railway, que ya está cifrada.
- **Swagger:** `PachocloSystem` publica `/swagger-ui.html` y `/v3/api-docs` en su dominio público;
  `MedicamentosService` también las publica, pero solo son alcanzables por la red privada. Con
  `SWAGGER_ENABLED=false` se desactivan en cualquiera de los dos (por defecto `true`).
- `CORS_ORIGENES` no admite comodines: los despliegues de *preview* de Vercel compilan, pero el
  navegador bloquea sus peticiones a la API.
- Si el proveedor de MongoDB exige permitir IPs de origen, añada las de salida de Railway (o use
  *Private Endpoint* / *Peering*). Atlas también admite `mongodb+srv://`, que la validación de
  producción acepta como no localhost.

### Proxies confiables (límite de intentos de login)

Las peticiones llegan a PachocloSystem a través de dos saltos de Railway: un proxy de borde con IP
pública y uno interno de la red `100.64.0.0/10`. Ninguno tiene una IP fija. Sin
`APP_LOGIN_PROXIES_CONFIABLES`, el servicio veía como IP del cliente la de esos proxies (varias,
alternándose), y el límite de intentos por usuario e IP no bloqueaba de forma fiable.

Con los dos rangos configurados se lee `X-Forwarded-For` saltando ambos proxies y se obtiene la IP
real del cliente: 5 fallos seguidos de un usuario desde una IP dan `429` a partir del sexto, y el
log `Login bloqueado temporalmente … IP …` muestra la IP pública del cliente.

`152.233.23.0/24` se dedujo de los logs (proxies de borde `152.233.23.193` y `.194`, borde `mia1`).
Railway no publica una lista fija: si en ese log vuelven a aparecer IPs públicas que no son de
clientes, hay que añadir su rango.

## Vercel

Los dos proyectos (`pachoclo-system-frontend` y `pachoclo-medicamentos-react`) están en el equipo
**Pachoclo** de Vercel, enlazados al repositorio `ShwDev/PachocloSystem` (rama de producción
`main`). Cada push a `main` los despliega; cada push a otra rama genera un *preview*. Los dos usan
como *Ignored Build Step* `git diff HEAD^ HEAD --quiet -- .`, así que un proyecto solo se recompila
cuando cambia su carpeta (`frontend/` o `frontend-react/`).

Los servicios de Railway también están conectados a `ShwDev/PachocloSystem` (rama `main`).

`frontend/vercel.json` define la instalación, el build y la salida del proyecto Angular, y
reescribe cualquier ruta a `index.html` (SPA).

| Ajuste | Valor |
|---|---|
| Root Directory | `frontend` |
| Preset | Angular u Other (manda `vercel.json`, con `"framework": null`) |
| `API_BASE_URL` | `https://pachoclosystem-production-2dd64b51.up.railway.app` (Production y Preview) |

`npm run build:vercel` falla si falta `API_BASE_URL` o si no es un origen https sin barra final.
Si el dominio del frontend cambia, hay que actualizar `CORS_ORIGENES` en PachocloSystem y
redesplegarlo.

### Frontend React (`frontend-react/`)

Segundo proyecto de Vercel, importado del mismo repositorio.

| Ajuste | Valor |
|---|---|
| Root Directory | `frontend-react` |
| Preset | Vite (build `npm run build`, salida `dist`) |
| `VITE_API_BASE_URL` | `https://pachoclosystem-production-2dd64b51.up.railway.app` (Production y Preview) |

Su dominio de Vercel también tiene que estar en `CORS_ORIGENES` de PachocloSystem. Como
`CORS_ORIGENES` no admite comodines, los despliegues *preview* no pueden iniciar sesión.

## Orden para desplegar desde cero

1. MongoDB gestionado: crear el replica set y las bases `pachoclosystem` y `medicamentos`, con un
   usuario que tenga permisos de lectura y escritura sobre ambas (o un usuario por base).
2. MedicamentosService: Root Directory, sin dominio, healthcheck y variables. Desplegar y comprobar
   en el log que arranca y crea los índices.
3. PachocloSystem: Root Directory, **generar el dominio antes de las variables** (hace falta para
   `CORS_ORIGENES`), healthcheck y variables. Desplegar.
4. Vercel: importar el repositorio con Root Directory `frontend` y `API_BASE_URL`. Desplegar.
5. Añadir el dominio de Vercel a `CORS_ORIGENES` y redesplegar PachocloSystem.

## Verificación

| Comprobación | Esperado |
|---|---|
| `GET /actuator/health` de PachocloSystem | `200 {"status":"UP"}` |
| `POST /api/auth/login` con el admin | `200` con token |
| `GET /api/medicamentos` con el token | `200`: red privada y `X-Api-Key` correctas |
| Preflight `OPTIONS` desde el origen de Vercel | `200` con `Access-Control-Allow-Origin` |
| Preflight desde otro origen | `403` |
| 6 logins fallidos seguidos de un usuario | 5 × `401` y después `429` constante |
| Crear un dato, redesplegar y volver a leerlo | El dato sigue ahí |
| Recargar una ruta profunda del frontend (`/pacientes`) | Carga la aplicación, no 404 |
