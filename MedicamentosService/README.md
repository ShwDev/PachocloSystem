# MedicamentosService

Microservicio REST (Spring Boot 4.1.1) para gestionar el inventario de medicamentos del hospital:
registro, entradas y salidas de stock, alertas de stock bajo y control de vencimientos.

Es independiente del servicio principal `PachocloSystem`: tiene su propio `pom.xml`, su propio
puerto y no comparte código Java con él.

## Requisitos

- **Java 25** (`JAVA_HOME` debe apuntar a un JDK 25; el `pom.xml` fija `java.version=25`).
- **MongoDB 8** con un **replica set** (aunque sea de un solo nodo): las transacciones de las salidas
  idempotentes lo necesitan (ver [Base de datos](#base-de-datos)).
- No hace falta instalar Maven: se usa el wrapper incluido.

## Cómo arrancar

Desde la carpeta `MedicamentosService/`, con la URI de MongoDB en el entorno:

```bash
SPRING_MONGODB_URI='mongodb://localhost:27017/medicamentos?replicaSet=rs0' ./mvnw spring-boot:run
```

```powershell
$env:SPRING_MONGODB_URI = "mongodb://localhost:27017/medicamentos?replicaSet=rs0"
.\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8081` (el servicio principal usa el 8080, así que pueden correr a la vez).
Otros comandos útiles:

```bash
./mvnw clean compile     # compilar
./mvnw test              # pruebas: Testcontainers arranca su propio MongoDB
```

Los datos se guardan en **MongoDB** y sobreviven a los reinicios. Si la base no está disponible, el
servicio no arranca: crea los índices al arrancar.

## Producción

Los valores por defecto son de **desarrollo**. En producción se arranca con el perfil `prod`
(`SPRING_PROFILES_ACTIVE=prod`, archivo `application-prod.properties`), que:

- **No arranca sin su configuración** (`ValidacionProduccion`, antes de conectar con la base). El
  error enumera lo que falta, con el nombre de cada variable y nunca su valor:
  - `MEDICAMENTOS_API_KEY` de al menos 32 caracteres (la misma en PachocloSystem): en producción la
    API nunca queda sin clave de servicio;
  - `SPRING_MONGODB_URI` definida y que no apunte a localhost (admite listas de hosts y
    `mongodb+srv://`).
- Escribe los **logs en JSON** (formato ECS) por la salida estándar.

Sin el perfil, todo funciona como siempre (desarrollo y tests). El servicio no debe tener dirección
pública: solo PachocloSystem lo llama, por la red privada de la plataforma.

| Variable | Por defecto | Para qué |
|---|---|---|
| `PORT` | `8081` | Puerto HTTP (lo asigna la plataforma) |
| `APP_ZONA_HORARIA` | `America/Bogota` | Zona horaria oficial: "hoy" para vencidos y por vencer sale de ella, no de la del servidor. Una zona inválida impide arrancar |
| `SPRING_PROFILES_ACTIVE` | *(ninguno)* | `prod` en producción |

**Health checks** (sin clave de servicio y sin detalles; ningún otro endpoint de Actuator está
expuesto): `GET /actuator/health` (`503` si la base no está disponible),
`/actuator/health/liveness` (no depende de la base) y `/actuator/health/readiness` (`503` sin base).

## Estructura

```
src/main/java/com/pachoclosystem/medicamentos/
├── controller/   endpoints REST
├── service/      lógica de negocio (stock, vencimientos, duplicados, idempotencia de salidas)
├── repository/   repositorios de medicamentos, claves de idempotencia y secuencias (MongoDB)
├── model/        Medicamento, DatosMedicamento, Presentacion
├── dto/          requests y responses (records con Bean Validation)
├── config/       @Bean Clock, transacciones de MongoDB y validación de producción
└── exception/    errores y @RestControllerAdvice
```

## Base de datos

MongoDB con dos colecciones. Los índices los crea el servicio al arrancar (no hay migraciones
manuales):

| Colección | Contenido |
|---|---|
| `medicamentos` | Un documento por medicamento, con su stock. Reglas: stock entre 0 y 1 000 000 y `claveUnica` (nombre + concentración + presentación + lote normalizados) con **índice único**. Los ids `MED-0001` salen de la colección `secuencias` (`medicamentos_id_seq`) |
| `idempotencia_salidas` | Salidas hechas con `Idempotency-Key`: `_id` es la clave, medicamento, cantidad, respuesta guardada y `hechaEn` (indexado, para purgar las caducadas) |

| Variable de entorno | Propiedad | Por defecto |
|---|---|---|
| `SPRING_MONGODB_URI` | `spring.mongodb.uri` | `mongodb://localhost:27017/medicamentos` |

La base va **dentro de la URI**. Sin `replicaSet` las transacciones de MongoDB fallan al confirmar.
En producción use un usuario y contraseña en la cadena (`mongodb+srv://…` en Atlas).

Los tests no usan una base externa: **Testcontainers** arranca un MongoDB de un nodo con replica set
al abrir la sesión de JUnit, antes de crear los contextos de Spring. Solo hace falta Docker.

**Concurrencia.** Entradas y salidas son un único `findAndModify` atómico con las condiciones dentro
de la consulta (`cantidadStock <= 1 000 000 - cantidad` al sumar; `cantidadStock >= cantidad` y no
vencido al restar): comprobar y escribir ocurre en el mismo paso, así que dos salidas simultáneas
nunca dejan el stock en negativo ni se pierde una actualización, aunque haya varias instancias del
servicio. La salida y su clave de idempotencia se guardan en la misma transacción de MongoDB; si dos
peticiones compiten, el conflicto de escritura se reintenta y una responde con la salida de la otra.
Si el medicamento desaparece mientras otra petición lo edita o lo elimina, la operación responde
`404`.

## Modelo

| Campo | Tipo | Notas |
|---|---|---|
| `idMedicamento` | texto | Generado: `MED-0001`, `MED-0002`, ... |
| `nombre` | texto | Nombre comercial (máx. 80) |
| `principioActivo` | texto | Máx. 80 |
| `presentacion` | enum | `TABLETA`, `CAPSULA`, `JARABE`, `SUSPENSION`, `INYECTABLE`, `CREMA`, `GOTAS`, `OTRO` |
| `concentracion` | texto | Libre, p. ej. `500 mg`, `250 mg/5 ml` (máx. 40) |
| `laboratorio` | texto | Máx. 80 |
| `lote` | texto | Máx. 40 |
| `cantidadStock` | entero | ≥ 0. Se fija al registrar; después solo cambia con entradas y salidas |
| `stockMinimo` | entero | ≥ 0 |
| `fechaVencimiento` | fecha | `AAAA-MM-DD` |
| `ubicacion` | texto | Ubicación de almacenamiento (máx. 80) |
| `stockBajo` | booleano | Solo en respuestas: `cantidadStock <= stockMinimo` |
| `vencido` | booleano | Solo en respuestas: `fechaVencimiento` anterior a hoy |

No puede haber dos medicamentos con el mismo **nombre + concentración + presentación + lote**
(sin distinguir mayúsculas): el intento devuelve `409`.

## Endpoints — `/api/medicamentos`

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/api/medicamentos?q=` | Lista; `q` filtra por id, nombre o principio activo | 200 |
| GET | `/api/medicamentos/{id}` | Obtiene un medicamento | 200 / 404 |
| POST | `/api/medicamentos` | Registra un medicamento (incluye `cantidadStock` inicial) | 201 / 400 / 409 |
| PUT | `/api/medicamentos/{id}` | Edita los datos (todo menos el stock) | 200 / 400 / 404 / 409 |
| DELETE | `/api/medicamentos/{id}` | Elimina un medicamento | 204 / 404 |
| POST | `/api/medicamentos/{id}/entradas` | Suma `{cantidad}` al stock | 200 / 400 / 404 |
| POST | `/api/medicamentos/{id}/salidas` | Resta `{cantidad}` del stock. Cabecera opcional `Idempotency-Key` (ver abajo) | 200 / 400 / 404 / 409 |
| GET | `/api/medicamentos/stock-bajo` | Medicamentos con stock ≤ stock mínimo | 200 |
| GET | `/api/medicamentos/por-vencer?dias=30` | No vencidos que vencen entre hoy y hoy + `dias` (1–365, por defecto 30), del más próximo al más lejano | 200 / 400 |
| GET | `/api/medicamentos/vencidos` | Medicamentos ya vencidos | 200 |

Reglas:

- Se puede **registrar** y **editar** un medicamento con fecha de vencimiento pasada (queda con
  `vencido: true` y aparece en `/vencidos`).
- Una **salida** devuelve `400` si la cantidad supera el stock disponible o si el medicamento está
  vencido; en ambos casos el stock no cambia.
- `cantidad` en entradas y salidas debe ser un entero entre 1 y 1 000 000.

### Salidas idempotentes (`Idempotency-Key`)

Si una salida llega a hacerse pero la respuesta se pierde (por ejemplo, PachocloSystem deja de
esperar), reintentarla descontaría dos veces. Con la cabecera opcional `Idempotency-Key`, el
reintento es seguro:

```bash
curl -X POST localhost:8081/api/medicamentos/MED-0001/salidas \
  -H "Content-Type: application/json" -H "Idempotency-Key: 8f14e45f-ceea-4672-a5b1-7a0c3c9e2f01" \
  -d '{"cantidad":2}'
```

- **Misma clave y mismos datos** (medicamento y cantidad): se devuelve la misma respuesta que la
  primera vez, con la cabecera `Idempotency-Replayed: true`, **sin volver a descontar**. La respuesta
  es la de entonces aunque el stock haya cambiado después.
- **Misma clave con otro medicamento u otra cantidad:** `409`.
- **Clave mal formada:** `400`. Debe tener de 16 a 100 caracteres: letras sin tilde, dígitos, guion o
  guion bajo (un UUID sirve).
- **Peticiones simultáneas con la misma clave:** solo una descuenta. La primera inserta la clave en
  su transacción; las demás chocan con el índice único del `_id`, su transacción se aborta (sin
  descuento) y, al reintentar, reciben la misma respuesta. Funciona aunque haya varias instancias
  del servicio.
- **Solo se guardan las salidas que salen bien.** La salida y su clave se guardan en la misma
  transacción: una salida que falla (stock insuficiente, vencido, medicamento inexistente) no deja
  nada, así que su reintento se vuelve a evaluar: puede salir bien si entre tanto entró stock, o
  volver a fallar.
- **Sin la cabecera**, la salida funciona exactamente como siempre.

Las claves se guardan en **MongoDB** (colección `idempotencia_salidas`) y **sobreviven a un reinicio**
del servicio:

| Propiedad | Por defecto | Para qué |
|---|---|---|
| `medicamentos.idempotencia.caducidad` | `24h` | Cuánto tiempo se recuerda cada clave |

Una clave caducada se trata como nueva: un reintento después de ese plazo volvería a descontar.
Las caducadas se borran de la colección al registrar nuevas salidas con clave.

### Ejemplos

Registrar (`POST /api/medicamentos`):

```json
{
  "nombre": "Dolex",
  "principioActivo": "Paracetamol",
  "presentacion": "TABLETA",
  "concentracion": "500 mg",
  "laboratorio": "GSK",
  "lote": "L-2026-001",
  "cantidadStock": 200,
  "stockMinimo": 50,
  "fechaVencimiento": "2027-03-31",
  "ubicacion": "Farmacia - Estante A3"
}
```

Editar (`PUT /api/medicamentos/{id}`): el mismo cuerpo **sin** `cantidadStock`.

Entrada o salida (`POST /api/medicamentos/{id}/entradas` o `/salidas`):

```json
{ "cantidad": 20 }
```

Respuesta de un medicamento:

```json
{
  "idMedicamento": "MED-0001",
  "nombre": "Dolex",
  "principioActivo": "Paracetamol",
  "presentacion": "TABLETA",
  "concentracion": "500 mg",
  "laboratorio": "GSK",
  "lote": "L-2026-001",
  "cantidadStock": 180,
  "stockMinimo": 50,
  "fechaVencimiento": "2027-03-31",
  "ubicacion": "Farmacia - Estante A3",
  "stockBajo": false,
  "vencido": false
}
```

## Autenticación entre servicios

Este servicio no tiene login de usuarios: los usuarios entran por **PachocloSystem** (con JWT), que
reenvía las llamadas aquí. Para que nadie se salte esa puerta, se puede exigir una clave compartida:

| Propiedad | Variable de entorno | Por defecto |
|---|---|---|
| `medicamentos.api-key` | `MEDICAMENTOS_API_KEY` | vacía |

- Con la clave definida, toda petición a `/api/**` debe traer la cabecera `X-Api-Key` con ese valor;
  si falta o no coincide responde **401** ("Falta la clave de servicio o no es válida.").
  PachocloSystem debe tener el mismo `MEDICAMENTOS_API_KEY`.
- Con la clave vacía no se exige nada (solo para desarrollo); al arrancar se registra un WARN.
- Defensa adicional opcional: `server.address=127.0.0.1` para que solo acepte conexiones locales.

```bash
MEDICAMENTOS_API_KEY=<clave-del-servicio> ./mvnw spring-boot:run
curl localhost:8081/api/medicamentos -H "X-Api-Key: <clave-del-servicio>"
```

> `<clave-del-servicio>` es un marcador: sustitúyalo por su propia clave. Este ejemplo es solo para
> desarrollo; en producción no use valores de ejemplo, genere una clave propia y no la guarde en el
> repositorio.

## Errores

Todas las respuestas de error tienen el mismo formato, sin trazas ni detalles internos:

```json
{
  "status": 400,
  "error": "Bad Request",
  "mensajes": ["Stock insuficiente: disponible 10, solicitado 11."]
}
```

| Código | Cuándo |
|---|---|
| 400 | Validación de campos, JSON malformado, stock insuficiente, salida de un vencido, `dias` fuera de rango, `Idempotency-Key` mal formada |
| 401 | Falta la cabecera `X-Api-Key` o no coincide (solo si `MEDICAMENTOS_API_KEY` está definida) |
| 404 | Medicamento o ruta inexistente |
| 405 | Método HTTP no permitido en la ruta (incluye cabecera `Allow`) |
| 409 | Ya existe un medicamento con el mismo nombre, concentración, presentación y lote; o una `Idempotency-Key` ya usada con otra salida |
| 415 | `Content-Type` no soportado |
| 500 | Error inesperado (el detalle solo va al log del servidor) |
| 503 | La base de datos no responde o una operación no pudo completarse a tiempo; nada ha cambiado y se puede reintentar |
