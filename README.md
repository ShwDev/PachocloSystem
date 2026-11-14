# PachocloSystem

Sistema de gestión de un hospital: pacientes, trabajadores (doctores y enfermeros), historial
clínico, inventario de medicamentos y usuarios con roles. Nació como una aplicación Swing con
arquitectura MVC y hoy son tres procesos que se hablan por HTTP.

> Proyecto de desarrollo y aprendizaje. Los dos servicios guardan sus datos en **MongoDB**
> (cada uno en su propia base), así que sobreviven a los reinicios (ver
> [Limitaciones conocidas](#limitaciones-conocidas)).

URL: [PachocloSystem](https://pachoclo-system-frontend-liard.vercel.app/) · Medicamentos en React: [pachoclo-medicamentos-react](https://pachoclo-medicamentos-react.vercel.app/)

[![Logo](frontend/public/logos/logo-sistema-160.png)](https://pachoclo-system-frontend-liard.vercel.app/)

## Arquitectura

```
 Navegador ──► Frontend Angular ──/api──► PachocloSystem ──X-Api-Key──► MedicamentosService
               (localhost:4200)      ┌──► (localhost:8080)              (localhost:8081)
                                     │     JWT, roles, pacientes,        inventario y stock
 Navegador ──► Frontend React ──/api─┘     trabajadores, historial       de medicamentos
               (localhost:5173)                   │                            │
                                                  ▼                            ▼
                                      MongoDB: base pachoclosystem   MongoDB: base medicamentos
                                      (una instancia mongo:8, replica set rs0, volumen mongo-datos)
```

| Parte | Carpeta | Puerto | Qué hace |
|---|---|---|---|
| Frontend | [`frontend/`](frontend/) | 4200 | SPA Angular con todos los módulos. Solo habla con PachocloSystem. |
| Frontend React | [`frontend-react/`](frontend-react/) | 5173 (Docker: 4300) | Aplicación independiente en React solo para el inventario de Medicamentos, con su propio login. Solo habla con PachocloSystem. |
| PachocloSystem | [`PachocloSystem/`](PachocloSystem/) | 8080 | API principal (Spring Boot): autenticación JWT, permisos por rol, pacientes, trabajadores, usuarios, historial clínico. Es la única puerta de entrada a los medicamentos. |
| MedicamentosService | [`MedicamentosService/`](MedicamentosService/) | 8081 | Microservicio de inventario (Spring Boot): stock, entradas y salidas, vencimientos. |
| MongoDB | (imagen `mongo:8`) | 27017 | Una base por servicio (`pachoclosystem` y `medicamentos`). Ningún servicio lee la base del otro. |

Cómo se comunican:

- **Navegador → PachocloSystem:** el frontend llama a rutas relativas `/api/**`. En desarrollo el
  proxy de Angular las reenvía al 8080, así que hay un único origen y no hace falta CORS. El
  usuario se autentica con un token JWT (`Authorization: Bearer`).
- **PachocloSystem → MedicamentosService:** HTTP directo con una clave compartida en la cabecera
  `X-Api-Key`. MedicamentosService no conoce a los usuarios, solo comprueba esa clave. Los dos
  servicios no comparten código.
- **El frontend nunca llama a MedicamentosService.** Si este está caído, PachocloSystem responde
  503 y solo el módulo de medicamentos muestra el aviso.

## Inicio rápido con Docker

Es la forma más corta de levantar todo (2 frontends, 2 backends y MongoDB). Solo hace falta Docker
Desktop (o Docker Engine) con Compose v2; no hacen falta Java ni Node.

```bash
cp .env.example .env              # valores de demo; cámbielos fuera de un entorno de pruebas
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
docker compose ps                 # esperar a que todo esté "healthy" (1-2 minutos)
```

[`docker-compose.demo.yml`](docker-compose.demo.yml) es un override para la demo: publica además los
backends y la base en el host para poder abrir los dos Swagger. Sin él (`docker compose up -d
--build`) solo se publican los frontends, que es la configuración normal.

| Qué | URL |
|---|---|
| Frontend Angular (todos los módulos) | <http://localhost:4200> |
| Frontend React (Medicamentos) | <http://localhost:4300> |
| Swagger PachocloSystem | <http://localhost:8080/swagger-ui.html> (solo con el override de demo) |
| Swagger MedicamentosService | <http://localhost:8081/swagger-ui.html> (solo con el override de demo) |
| MongoDB | `docker compose exec mongo mongosh`, o `mongodb://127.0.0.1:27018/?directConnection=true` con el override |

Para pararlo: `docker compose -f docker-compose.yml -f docker-compose.demo.yml down`. **Sin
`-v`** los datos se conservan; con `-v` se borra el volumen de MongoDB. Detalle de servicios,
redes, volúmenes y publicación en Docker Hub en [`docker/README.md`](docker/README.md).

### Credenciales de demo

Son los valores de [`.env.example`](.env.example). **Solo para desarrollo y demos**; en producción
cada secreto es distinto y no está en el repositorio.

| Qué | Valor |
|---|---|
| Usuario administrador | `admin` |
| Contraseña del administrador | `admin-ejemplo-2026` |
| Clave entre servicios (`X-Api-Key`, Authorize del Swagger de MedicamentosService) | `clave-servicio-ejemplo-solo-desarrollo-0123456789` |

El administrador se crea en el primer arranque, con la contraseña que tenga `ADMIN_PASSWORD` en ese
momento. Si la base ya tenía el usuario, cambiar la variable no cambia su contraseña.

## Requisitos (desarrollo sin Docker)

- **Java 25** (`JAVA_HOME` apuntando a un JDK 25). No hace falta instalar Maven: cada servicio
  incluye su wrapper (`mvnw`).
- **MongoDB 8** con un **replica set** (aunque sea de un solo nodo) y una base por servicio:
  `medicamentos` y `pachoclosystem`. Las transacciones de MongoDB no funcionan contra un `mongod`
  suelto. Los tests no lo necesitan: Testcontainers arranca el suyo.
- **Node** `^22.22.3`, `^24.15.0` o `>=26` (lo exige Angular 22), con npm.
- Git Bash o PowerShell en Windows; también vale cualquier shell POSIX.

La forma más rápida de tener MongoDB es un contenedor con replica set:

```bash
docker run -d --name pachoclo-mongo -p 27017:27017 mongo:8 --replSet rs0 --bind_ip_all
docker exec pachoclo-mongo mongosh --quiet --eval \
  'rs.initiate({_id: "rs0", members: [{_id: 0, host: "localhost:27017"}]})'
```

También puede levantar todo el sistema con Docker (ver [Inicio rápido con Docker](#inicio-rápido-con-docker));
entonces no hacen falta Java ni Node.

## Cómo levantar el sistema sin Docker

Hacen falta **tres terminales**, en este orden. Los valores de abajo son **solo de ejemplo para
desarrollo**: use los suyos y no los guarde en el repositorio.

| Variable | Dónde | Ejemplo | Notas |
|---|---|---|---|
| `MEDICAMENTOS_API_KEY` | los dos servicios | `clave-servicio-ejemplo` | Debe ser **igual** en ambos. |
| `SPRING_MONGODB_URI` | cada servicio | `mongodb://localhost:27017/pachoclosystem?replicaSet=rs0` | URI de su base (incluye el nombre). Sin `replicaSet` las transacciones fallan. |
| `ADMIN_PASSWORD` | PachocloSystem | `admin-ejemplo-2026` | Mínimo 10 caracteres. Solo se usa la primera vez, al crear el administrador en la base. Si se omite, se genera una aleatoria y se imprime una vez en el log (WARN). |
| `JWT_SECRET` | PachocloSystem | `secreto-jwt-ejemplo-solo-desarrollo` | Mínimo 32 bytes. Si se omite, los tokens no sobreviven a un reinicio. |

Opcionales de PachocloSystem: `CORS_ORIGENES` (orígenes permitidos si el frontend se sirve desde
otro origen; vacío por defecto, sin CORS), `APP_LOGIN_PROXIES_CONFIABLES` (IPs o rangos CIDR de los
proxies inversos propios, cuya `X-Forwarded-For` se acepta para el límite de intentos de login) y los
umbrales de ese límite. Detalle en el [README de PachocloSystem](PachocloSystem/README.md).
Los dos servicios aceptan además `PORT` y `APP_ZONA_HORARIA` (por defecto `America/Bogota`).

### Producción

En producción los dos servicios arrancan con `SPRING_PROFILES_ACTIVE=prod`, que **impide arrancar**
si falta un secreto o una configuración obligatoria (las claves, las URLs que no sean localhost,
`CORS_ORIGENES` en https, `ADMIN_PASSWORD` al crear el administrador) y escribe los logs en JSON.
Exponen health checks en `/actuator/health` (`/liveness` y `/readiness`). El frontend se compila
para Vercel con `npm run build:vercel` y `API_BASE_URL`. Detalle en la sección "Producción" de cada
README y en [Despliegue en Vercel](frontend/README.md#despliegue-en-vercel).

El despliegue actual (backends en Railway, MongoDB gestionado y frontend en Vercel), con sus
variables y verificaciones, está en [`docs/despliegue.md`](docs/despliegue.md).

### Git Bash

```bash
# Terminal 1: MedicamentosService (8081)
cd MedicamentosService
MEDICAMENTOS_API_KEY=clave-servicio-ejemplo \
  SPRING_MONGODB_URI='mongodb://localhost:27017/medicamentos?replicaSet=rs0' \
  ./mvnw spring-boot:run

# Terminal 2: PachocloSystem (8080)
cd PachocloSystem
MEDICAMENTOS_API_KEY=clave-servicio-ejemplo ADMIN_PASSWORD=admin-ejemplo-2026 \
  JWT_SECRET=secreto-jwt-ejemplo-solo-desarrollo \
  SPRING_MONGODB_URI='mongodb://localhost:27017/pachoclosystem?replicaSet=rs0' \
  ./mvnw spring-boot:run

# Terminal 3: frontend (4200)
cd frontend
npm install
npm start
```

### PowerShell

```powershell
# Terminal 1: MedicamentosService (8081)
cd MedicamentosService
$env:MEDICAMENTOS_API_KEY = "clave-servicio-ejemplo"
$env:SPRING_MONGODB_URI = "mongodb://localhost:27017/medicamentos?replicaSet=rs0"
.\mvnw.cmd spring-boot:run

# Terminal 2: PachocloSystem (8080)
cd PachocloSystem
$env:MEDICAMENTOS_API_KEY = "clave-servicio-ejemplo"
$env:ADMIN_PASSWORD = "admin-ejemplo-2026"
$env:JWT_SECRET = "secreto-jwt-ejemplo-solo-desarrollo"
$env:SPRING_MONGODB_URI = "mongodb://localhost:27017/pachoclosystem?replicaSet=rs0"
.\mvnw.cmd spring-boot:run

# Terminal 3: frontend (4200)
cd frontend
npm install
npm start
```

Abra <http://localhost:4200>.

### Usuario inicial

El primer arranque de PachocloSystem crea un administrador en la base (en los siguientes ya
existe y no se toca):

- **Usuario:** `admin` (o el valor de `ADMIN_USERNAME`).
- **Contraseña:** la de `ADMIN_PASSWORD`. Con los ejemplos de arriba, `admin-ejemplo-2026`.

El administrador no tiene trabajador vinculado, así que puede consultar el historial pero no
crear registros. Doctores y enfermeros inician sesión con usuarios que el admin crea desde la
pantalla **Usuarios**.

## Recorrido corto

1. Entre como `admin`.
2. **Trabajadores:** registre un doctor (p. ej. nombre "Eva Mora", especialidad "Cardiología").
3. **Usuarios:** cree un usuario con rol `DOCTOR` vinculado a ese doctor (contraseña de 10
   caracteres o más). Es una contraseña temporal: el doctor la cambiará al entrar.
4. **Pacientes:** registre un paciente.
5. **Medicamentos:** registre uno con stock inicial (esto habla con MedicamentosService).
6. Cierre sesión y entre con el usuario del doctor. La aplicación le pide cambiar la contraseña;
   después vuelve al login y entra con la nueva.
7. **Pacientes → Historial:** agregue un registro de tipo `MEDICACION` indicando el medicamento y
   una cantidad. El registro queda firmado por el doctor y el stock baja.
8. En **Medicamentos** compruebe el nuevo stock; en **Historial** vea el registro.
9. Opcional: detenga MedicamentosService y recargue Medicamentos para ver el aviso 503 sin que
   caiga el resto de la app.

## Endpoints por recurso

Todas las rutas son de PachocloSystem (`:8080`), la única puerta de entrada. Todas exigen
`Authorization: Bearer <token>` salvo el login. Roles: **A** = ADMIN, **D** = DOCTOR,
**E** = ENFERMERO. El contrato completo (parámetros, cuerpos, ejemplos y respuestas) está en Swagger.

| Recurso | Método y ruta | Qué hace | Roles |
|---|---|---|---|
| Autenticación | `POST /api/auth/login` | Inicia sesión y devuelve el token JWT | público |
| | `GET /api/auth/me` | Usuario autenticado | todos |
| | `POST /api/auth/password` | Cambia la contraseña propia | todos |
| Pacientes | `GET /api/pacientes?q=` | Lista los pacientes activos (filtro opcional) | A D E |
| | `GET /api/pacientes/{id}` | Obtiene un paciente activo | A D E |
| | `POST /api/pacientes` | Registra un paciente (201 + `Location`) | A D |
| | `PUT /api/pacientes/{id}` | Edita nombre, edad y habitación | A D |
| | `PATCH /api/pacientes/{id}/habitacion` | Cambia solo la habitación | A D E |
| | `DELETE /api/pacientes/{id}` | **Baja lógica** (`activo=false`), 204 | A |
| Historial clínico | `GET /api/pacientes/{id}/historial?q=` | Registros de un paciente activo | A D E |
| | `POST /api/pacientes/{id}/historial` | Añade un registro firmado (acepta `Idempotency-Key`) | D E* |
| | `GET /api/historial?filtro=&q=` | Historial de todos los pacientes activos | A D E |
| Trabajadores | `GET /api/trabajadores?q=` | Lista doctores y enfermeros | A D |
| | `GET /api/trabajadores/{id}` | Obtiene un trabajador | A D |
| | `POST /api/trabajadores` | Registra un doctor o enfermero (201) | A |
| | `PUT /api/trabajadores/{id}` | Edita un trabajador | A |
| | `DELETE /api/trabajadores/{id}` | **Borrado físico** y desactivación de su usuario, 204 | A |
| Usuarios | `GET /api/usuarios` · `GET /api/usuarios/{id}` | Lista y consulta usuarios (sin contraseñas) | A |
| | `POST /api/usuarios` | Crea un usuario con contraseña temporal (201) | A |
| | `PATCH /api/usuarios/{id}/rol` | Cambia el rol | A |
| | `PATCH /api/usuarios/{id}/desactivar` · `/activar` | Desactiva o reactiva (no hay DELETE) | A |
| | `PATCH /api/usuarios/{id}/password` | Restablece la contraseña | A |
| Medicamentos | `GET /api/medicamentos?q=` · `GET /api/medicamentos/{id}` | Lista y consulta el inventario | A D E |
| | `POST /api/medicamentos` | Registra un medicamento (201) | A |
| | `PUT /api/medicamentos/{id}` | Edita sus datos (el stock cambia con entradas y salidas) | A |
| | `DELETE /api/medicamentos/{id}` | **Borrado físico**, 204 | A |
| | `POST /api/medicamentos/{id}/entradas` | Suma stock | A |
| | `POST /api/medicamentos/{id}/salidas` | Resta stock | A E |
| | `GET /api/medicamentos/stock-bajo` · `/por-vencer?dias=` · `/vencidos` | Alertas de inventario | A D E |

\* El registro clínico lo firma el trabajador vinculado al usuario, así que el ADMIN (sin
trabajador) puede consultar el historial pero no añadir registros.

Las rutas `/api/medicamentos/**` se reenvían a MedicamentosService (`:8081`), que expone las mismas
rutas protegidas con `X-Api-Key` en lugar del token. El navegador nunca lo llama directamente.

## Decisiones de borrado

| Recurso | DELETE | Qué queda en MongoDB |
|---|---|---|
| Medicamentos | Borrado físico | Nada: el documento se elimina de `medicamentos.medicamentos`. |
| Trabajadores | Borrado físico | Nada del trabajador. Su usuario, si tenía, queda **desactivado**, y los registros que firmó conservan una copia del autor. |
| Pacientes | **Baja lógica** | El documento sigue en `pachoclosystem.pacientes` con `activo=false`, y sus registros clínicos siguen intactos. |
| Usuarios | No hay DELETE | Se desactivan y se reactivan con `PATCH`. |
| Historial clínico | No hay PUT ni DELETE | Los registros son inmutables; una corrección es un registro nuevo. |

**Por qué la baja de pacientes es lógica.** En un entorno hospitalario no se elimina información
clínica: la historia de un paciente tiene que seguir disponible para auditorías, consultas
posteriores y responsabilidad legal. Por eso el `DELETE /api/pacientes/{id}` no borra nada, ni
en cascada: solo inactiva al paciente (`activo=false`). Desde ese momento no aparece en las
listas, las búsquedas ni el historial general, y las rutas con su ID responden 404. Sus datos y
sus registros siguen en la base. En la interfaz la acción se llama **Desactivar paciente** y el
diálogo de confirmación lo explica. Medicamentos y trabajadores no son información clínica, así
que se borran de verdad.

## Persistencia en MongoDB

- Cada servicio se conecta con su propia URI, `SPRING_MONGODB_URI`. En Compose sale de
  `PACHOCLO_MONGO_URI` y `MEDICAMENTOS_MONGO_URI` del `.env`. No hay URIs fijas en el código.
- Los datos viven en el volumen nombrado `mongo-datos`, montado en `/data/db` del contenedor
  `mongo`. Se usa el mismo volumen en `docker-compose.yml` y en `docker-compose.hub.yml`.
- Los índices (únicos de `username`, `idTrabajador` y `claveUnica` del medicamento, y los de
  consulta) se crean al arrancar con `@Indexed` (`spring.data.mongodb.auto-index-creation=true`).
  No hay migraciones.

Cómo comprobar que los datos sobreviven a un reinicio:

1. Con el stack levantado, entre en <http://localhost:4200> como `admin` y registre un medicamento
   (o un trabajador).
2. Compruebe que está en la base:
   `docker compose exec mongo mongosh --quiet medicamentos --eval 'db.medicamentos.find({}, {"datos.nombre": 1})'`
3. Pare y borre los contenedores **sin** `-v`: `docker compose down` (con el override de demo,
   añada los mismos `-f`). Compruebe que el volumen sigue: `docker volume ls | grep mongo-datos`.
4. Vuelva a levantarlo: `docker compose up -d` y espere a que esté `healthy`.
5. Recargue Medicamentos: el registro sigue ahí, y el `mongosh` del paso 2 lo devuelve igual.

El smoke test (ver [Tests](#tests)) hace este mismo recorrido automáticamente.

## Tests

Cada parte se prueba por separado, desde su carpeta:

```bash
cd PachocloSystem && ./mvnw test          # API principal (en PowerShell: .\mvnw.cmd test)
cd MedicamentosService && ./mvnw test     # microservicio de medicamentos
cd frontend && npm test                   # unitarios de Angular (Vitest)
cd frontend-react && npm test              # unitarios de la app React (Vitest)
cd frontend && npm run build              # build de producción en frontend/dist
```

El frontend también tiene `npm run lint` y `npm run format:check`. No hace falta arrancar Mongo ni
los servicios, pero los tests de los dos backends necesitan **Docker** (Testcontainers arranca su
propio MongoDB).

### Smoke test de extremo a extremo

[`scripts/smoke-test.mjs`](scripts/smoke-test.mjs) (Node 22 o más) prueba el stack de Docker real.
Sobre Medicamentos y Trabajadores recorre POST → GET → PUT → GET → reinicio de contenedores
(`down` sin `-v` + `up`) → GET → DELETE → GET (404), y cuenta los documentos directamente en
MongoDB con `mongosh` en cada paso. Sobre Pacientes comprueba la baja lógica: el paciente deja de
salir en la API y su documento sigue con `activo=false`. Solo se ejecuta contra `localhost`.

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
node scripts/smoke-test.mjs        # usa .env (o .env.example si no hay .env)
```

## Documentación por módulo

- [`PachocloSystem/README.md`](PachocloSystem/README.md): endpoints, permisos por rol, JWT, usuarios, historial idempotente e integración con medicamentos.
- [`MedicamentosService/README.md`](MedicamentosService/README.md): modelo, endpoints, stock, vencimientos e idempotencia de las salidas.
- [`frontend/README.md`](frontend/README.md): estructura, patrón de los módulos, roles, sesión y errores.
- [`frontend-react/README.md`](frontend-react/README.md): app React de Medicamentos (login, desarrollo, Docker y despliegue).
- [`docs/plan-frontend.md`](docs/plan-frontend.md): plan y decisiones del frontend.
- [`docs/despliegue.md`](docs/despliegue.md): despliegue en Railway y Vercel.
- [`docs/plan-migracion-mongodb.md`](docs/plan-migracion-mongodb.md): registro de la migración de PostgreSQL a MongoDB.
- [`docker/README.md`](docker/README.md): Docker Compose, override de demo y publicación en Docker Hub.

## Documentación de la API (OpenAPI/Swagger)

Los dos backends publican su API con **springdoc-openapi**, con la interfaz interactiva y el JSON
OpenAPI en estas rutas (relativas al host del servicio, así que valen igual en local y en un
despliegue):

| Servicio | Swagger UI | OpenAPI JSON |
|---|---|---|
| PachocloSystem | <http://localhost:8080/swagger-ui.html> | <http://localhost:8080/v3/api-docs> |
| MedicamentosService | <http://localhost:8081/swagger-ui.html> | <http://localhost:8081/v3/api-docs> |
| PachocloSystem (producción) | <https://pachoclosystem-production-2dd64b51.up.railway.app/swagger-ui.html> | `…/v3/api-docs` |

En local sin Docker valen tal cual. Con Docker hace falta el override de demo
(`-f docker-compose.demo.yml`), porque el compose normal no publica los backends. En producción
MedicamentosService solo está en la red privada de Railway, así que su Swagger no es accesible;
sus rutas aparecen documentadas a través de PachocloSystem.

- **PachocloSystem:** el botón **Authorize** pide el token JWT que devuelve `POST /api/auth/login`;
  el resto de operaciones lo envían como `Authorization: Bearer <token>`.
- **MedicamentosService:** el botón **Authorize** pide la clave compartida (`MEDICAMENTOS_API_KEY`),
  que se envía en `X-Api-Key`.

Las rutas de documentación son **públicas** (no exigen token ni clave) y no aparecen en los
listados de la API. Se desactivan con `SWAGGER_ENABLED=false` (por defecto `true`); en producción
conviene dejarlas activas solo si el servicio no es accesible desde Internet.

## Limitaciones conocidas

Reúne lo que ya documentan los README de cada módulo (enlazados arriba). El detalle y el motivo
de cada punto están allí.

**Datos y sesión**

- **Todo se guarda en MongoDB** y sobrevive a los reinicios. `ADMIN_PASSWORD` solo se aplica al
  crear el administrador (primer arranque); sin ella la contraseña es aleatoria, queda en el log y
  hay que cambiarla en el primer acceso. Sin `JWT_SECRET` los tokens no sobreviven a un reinicio.
- **Sin MongoDB** los servicios no arrancan (los índices se crean al arrancar); si la base cae con
  ellos en marcha, responden `503` hasta que vuelva.
- **MongoDB necesita un replica set.** Las transacciones y el cerrojo administrativo de
  PachocloSystem no funcionan contra un `mongod` suelto. El compose y los tests usan uno de un nodo.
- **Una sola instancia de PachocloSystem.** El orden de las peticiones con la misma
  `Idempotency-Key` y el límite de intentos de login viven en la memoria del proceso.
- **Sesión de 30 minutos sin renovación.** No hay refresh token: el frontend avisa 5 minutos antes
  y, al expirar, vuelve al login. El token se guarda en `sessionStorage` y se pierde al cerrar la
  pestaña.
- **Sin paginación:** las listas llegan completas.
- **Límite de intentos de login en memoria.** 5 fallos de un usuario desde una IP bloquean ese
  par 15 minutos; 50 fallos desde una IP, esa IP; 100 de una cuenta desde cualquier IP, esa cuenta.
  Detrás de un proxy sin configurar `APP_LOGIN_PROXIES_CONFIABLES` (como el de desarrollo) todos
  comparten IP: 50 fallos entre todos bloquean el login de todos durante la ventana.

**Usuarios, trabajadores y pacientes**

- La **baja de un paciente es lógica**: deja de aparecer en listas e historial, pero sus datos se
  conservan en la base de datos y no se puede reactivar desde la API (ver
  [Decisiones de borrado](#decisiones-de-borrado)).
- Un trabajador solo puede tener **un usuario, aunque esté desactivado**: si su usuario se
  desactivó, no se le puede crear otro.
- Un doctor o enfermero desactivado solo se reactiva si su trabajador sigue existiendo, es de su
  tipo y sigue vinculado a él.

**Historial y stock de medicamentos**

- **Sin `Idempotency-Key`**, si la salida de stock se hace pero su respuesta no llega (timeout), el
  cliente recibe 503, el stock queda descontado sin registro y reintentar descontaría otra vez.
  Si el timeout salta con las cabeceras ya recibidas pero sin cuerpo, recibe **502** y no hay
  reintento. El frontend siempre envía la clave.
- **Las claves de idempotencia caducan a las 24 h.** "Reintentar sin riesgo de descontar dos
  veces" solo se cumple dentro de ese plazo. MedicamentosService las guarda en MongoDB y
  sobreviven a sus reinicios; las del historial de PachocloSystem, también (sin tope de claves).
- **Las claves del frontend viven en el diálogo.** Si se recarga la página tras un resultado
  incierto, el siguiente intento usa otra clave: hay que revisar el historial y el stock antes de
  repetirlo.
- Los **signos vitales** se guardan como texto en `contenido`, no como datos estructurados.

**Despliegue**

- En desarrollo el frontend y la API están en el **mismo origen** (proxy de `ng serve`). Si se
  sirven desde orígenes distintos (Vercel y Railway) hay que definir `CORS_ORIGENES` en
  PachocloSystem y `API_BASE_URL` en el build del frontend.
- En Railway los proxies no tienen IP fija: `APP_LOGIN_PROXIES_CONFIABLES` lleva rangos deducidos
  de los logs, y habría que ampliarlos si aparecen proxies nuevos (ver
  [`docs/despliegue.md`](docs/despliegue.md#proxies-confiables-límite-de-intentos-de-login)).
- Sin `MEDICAMENTOS_API_KEY`, MedicamentosService no exige autenticación (solo desarrollo; con el
  perfil `prod` no arranca sin ella).
- **Una réplica** de PachocloSystem (ver "Una sola instancia" arriba).

### Para una versión futura

- **Refresh token** para renovar la sesión sin volver a iniciar sesión.
- **Rol de farmacia** para gestionar el inventario sin ser ADMIN.
- **Tests E2E** con Playwright (previstos en el plan del frontend, fase F7).
- Paginación, y cambio de rol de un usuario desde la pantalla Usuarios (el backend ya lo admite).
