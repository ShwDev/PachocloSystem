# Registro de la migración de PostgreSQL a MongoDB

Migración terminada en noviembre de 2026 (commits del 11 de noviembre, de `e262885` a `bc018b0`).
Este documento resume lo que se hizo; el plan original por fases se sustituyó por este registro.

## Alcance

Se cambió la persistencia de **PachocloSystem** (`:8080`) y de **MedicamentosService** (`:8081`) de
PostgreSQL (JDBC + Flyway) a MongoDB. Los contratos REST y los dos frontends no cambiaron.

El punto de corte fueron las interfaces `I*Repository`, que ya aislaban el acceso a datos: se
sustituyeron las implementaciones `*RepositoryJdbc` por `*RepositoryMongo` (con `MongoTemplate`) y
los servicios, controladores y DTO quedaron igual.

## Qué se hizo

| Área | Antes (PostgreSQL) | Después (MongoDB) |
|---|---|---|
| Bases | `db-pachoclo` y `db-medicamentos`, dos contenedores | Una instancia `mongo:8` con dos bases: `pachoclosystem` y `medicamentos` |
| Esquema | Migraciones Flyway | Sin migraciones: índices `@Indexed` creados al arrancar (`spring.data.mongodb.auto-index-creation=true`) |
| Unicidad | `UNIQUE` (username, trabajador del usuario, clave de medicamento) | Índices únicos (`username`, `idTrabajador` *sparse*, `claveUnica`) |
| IDs legibles (`PAC-0001`, `DOC-0001`, `MED-0001`) | Secuencias (`nextval`) | Colección `secuencias` con incremento atómico (`SecuenciaGeneratorService`) |
| Transacciones | Transacciones JDBC, `SELECT … FOR UPDATE` | `MongoTransactionManager` sobre un **replica set** (`rs0`) y reintento de conflictos transitorios (`TransaccionConReintentos`) |
| Cerrojo de administración | *Advisory lock* | Documento `cerrojos/administracion` incrementado dentro de la transacción |
| `CHECK` (edad, habitación, stock) | En la base | Validaciones en DTO y servicios (Mongo no tiene `CHECK` ni claves foráneas) |
| Idempotencia | Tablas con `JSONB` | Colecciones `historial_idempotencia` e `idempotencia_salidas`, con índice por fecha para purgar caducadas |
| Fechas | Microsegundos | Milisegundos en los registros clínicos (`truncatedTo(MILLIS)`) |
| Configuración | `SPRING_DATASOURCE_*` | `SPRING_MONGODB_URI` (Spring Boot 4: `spring.mongodb.uri`) |
| Salud | Indicador JDBC | Grupo `readiness` con `mongo` |

## Infraestructura y pruebas

- **Docker Compose:** se quitaron los dos PostgreSQL y sus volúmenes. Ahora hay un servicio `mongo`
  con el volumen `mongo-datos` en `/data/db` y un `mongo-init` de un solo uso que crea el replica
  set y espera a que el nodo sea primario. Los backends no arrancan hasta que termina.
- **Tests:** Testcontainers levanta un MongoDB real con replica set. Se portaron los tests de
  repositorios y de concurrencia, y la limpieza entre tests borra documentos sin tirar colecciones,
  para conservar los índices.
- **Producción:** el perfil `prod` valida que `SPRING_MONGODB_URI` exista y no apunte a localhost.
  El despliegue usa un MongoDB gestionado con replica set (ver [`despliegue.md`](despliegue.md)).

## Decisiones que se mantuvieron

- **Una base por servicio:** se conservó el aislamiento entre servicios. Cada uno solo conoce su
  base, y PachocloSystem accede a los medicamentos únicamente por HTTP.
- **Baja lógica de pacientes:** el `DELETE` sigue marcando `activo=false` y conservando el
  historial. Trabajadores y medicamentos siguen con borrado físico.
- **Datos:** no se migraron datos de PostgreSQL. Las bases de MongoDB arrancaron vacías y el
  administrador inicial lo crea `AdminInicial` en el primer arranque.

## Diferencias aceptadas

- Los listados sin criterio propio (pacientes, trabajadores) salen ordenados por `_id`, no por
  orden de alta.
- Las reglas que antes garantizaban `CHECK` y las claves foráneas viven ahora solo en los
  servicios.
- MongoDB exige replica set incluso en desarrollo; un `mongod` suelto no admite las transacciones.
