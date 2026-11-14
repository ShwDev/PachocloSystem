# PachocloSystem en Docker Compose

Despliegue multicontenedor del sistema: 6 servicios, 2 redes y 2 volúmenes definidos en
[`docker-compose.yml`](../docker-compose.yml). Las imágenes se construyen desde los Dockerfiles de
cada carpeta (`frontend/`, `frontend-react/`, `PachocloSystem/`, `MedicamentosService/`) y la base usa la imagen
oficial `mongo:8`.

## Arquitectura

```
                      host (Windows/Linux)
                              │  único puerto publicado: 4200 → 8080
 ┌────────────────────────────┼──────────────────────────────────────────────┐
 │  red "publica"  172.28.10.0/24                                            │
 │                            ▼                                              │
 │                   ┌──────────────────┐                                    │
 │                   │ frontend (nginx) │  172.28.10.10 (IP fija)            │
 │                   │ SPA Angular      │                                    │
 │                   └────────┬─────────┘                                    │
 │                            │ /api/*  (X-Forwarded-For)                    │
 │                            ▼                                              │
 │                   ┌──────────────────┐                                    │
 │                   │   pachoclo-api   │  Spring Boot :8080                 │
 │                   │ JWT, roles, API  │  volumen pachoclo-logs             │
 │                   └───┬──────────┬───┘                                    │
 └───────────────────────┼──────────┼────────────────────────────────────────┘
 ┌───────────────────────┼──────────┼────────────────────────────────────────┐
 │  red "interna"  (internal: true, sin salida a internet ni al host)        │
 │                       │          │ X-Api-Key                              │
 │                       │          ▼                                        │
 │                       │   ┌──────────────────────┐                        │
 │                       │   │ medicamentos-service │                        │
 │                       │   │ Spring Boot :8081    │                        │
 │                       │   └──────────┬───────────┘                        │
 │                       │              │                                    │
 │                       ▼              ▼                                    │
 │                  ┌──────────────────────────┐                             │
 │                  │           mongo          │  MongoDB 8 (replica rs0)    │
 │                  └────────────┬─────────────┘                             │
 │                               │ vol. mongo-datos                          │
 │          mongo-init (one-shot: rs.initiate y espera a primario)            │
 └───────────────────────────────┼───────────────────────────────────────────┘
```

`mongo-init` es un contenedor de un solo uso: crea el replica set de un nodo (`rs0`) la primera vez
y espera a que el nodo sea primario. En los siguientes arranques detecta que `rs0` ya existe y sale
con `0`. `pachoclo-api` y `medicamentos-service` no arrancan hasta que termina.

| Servicio | Imagen | Red(es) | Puerto al host | Volumen |
|---|---|---|---|---|
| `frontend` | build `./frontend` (nginx sin privilegios) | publica | **4200** → 8080 | — |
| `frontend-react` | build `./frontend-react` (nginx sin privilegios) | publica | **4300** → 8080 | — |
| `pachoclo-api` | build `./PachocloSystem` (Java 25) | publica + interna | no | `pachoclo-logs` |
| `medicamentos-service` | build `./MedicamentosService` (Java 25) | interna | no | — |
| `mongo` | `mongo:8` | interna | no | `mongo-datos` |
| `mongo-init` | `mongo:8` | interna | no | — |

`frontend-react` (la app React de Medicamentos, en <http://localhost:4300>) funciona igual que
`frontend`: su propio nginx con IP fija `172.28.10.11` (`NGINX_REACT_IP`) sirve la app y reenvía
`/api/*` a `pachoclo-api`. También está en `docker-compose.hub.yml` (imagen
`pachoclo-frontend-react`). El diagrama de arriba muestra solo el frontend Angular.

Flujo: el navegador solo habla con nginx. nginx sirve la SPA y reenvía `/api/*` a `pachoclo-api`
(mismo origen, por eso no hace falta CORS). `pachoclo-api` es la única que habla con
`medicamentos-service`, usando la clave compartida en la cabecera `X-Api-Key`. Ambos backends
comparten la instancia de MongoDB, cada uno con su base (`pachoclosystem` y `medicamentos`).

## Cómo levantarlo

Requisitos: Docker Desktop (o Docker Engine) con Compose v2. No hace falta Java ni Node: todo se
compila dentro de las imágenes.

```bash
cp .env.example .env        # y cambie los valores (en PowerShell: Copy-Item .env.example .env)
docker compose build        # construye las 4 imágenes propias (la primera vez tarda unos minutos)
docker compose up -d        # arranca los servicios en el orden de depends_on
docker compose ps           # los 4 de larga duración deben quedar "healthy"
```

Abra <http://localhost:4200> e inicie sesión como `admin` con el valor de `ADMIN_PASSWORD`.
El administrador se crea solo en el primer arranque.

```bash
docker compose logs -f pachoclo-api     # logs de un servicio
docker compose logs mongo-init          # resultado de la inicialización del replica set
docker compose stop                     # detiene sin borrar nada
docker compose down                     # elimina contenedores y redes; los volúmenes (datos) se conservan
docker compose down -v                  # ¡borra también los volúmenes: se pierden todos los datos!
```

## Variables de `.env`

Plantilla en [`.env.example`](../.env.example). `.env` está en `.gitignore` y nunca se sube. Las
marcadas como obligatorias hacen fallar el compose con un mensaje claro si faltan.

| Variable | Obligatoria | Ejemplo | Para qué sirve |
|---|---|---|---|
| `DOCKERHUB_USER` | no | `tu-usuario-dockerhub` | Prefijo del nombre de las imágenes (para publicarlas). |
| `IMAGE_TAG` | no | `latest` | Etiqueta de las imágenes. |
| `FRONTEND_PORT` | no | `4200` | Puerto del host donde se abre la aplicación. |
| `SPRING_PROFILES_ACTIVE` | no | *(vacío)* | Vacío = desarrollo. Con `prod` exige `CORS_ORIGENES` con https (ver limitaciones). |
| `JWT_SECRET` | **sí** | 32 bytes o más | Clave de firma de los tokens JWT. |
| `MEDICAMENTOS_API_KEY` | **sí** | 32 caracteres o más | Clave compartida entre las dos APIs (`X-Api-Key`). |
| `ADMIN_USERNAME` | no | `admin` | Usuario del administrador inicial. |
| `ADMIN_PASSWORD` | **sí** | 10 caracteres o más | Contraseña del admin; solo se aplica al crearlo. |
| `PACHOCLO_MONGO_URI` | **sí** | `mongodb://mongo:27017/pachoclosystem?replicaSet=rs0` | URI de la base de PachocloSystem (incluye la base). |
| `MEDICAMENTOS_MONGO_URI` | **sí** | `mongodb://mongo:27017/medicamentos?replicaSet=rs0` | URI de la base de MedicamentosService. |
| `CORS_ORIGENES` | no | *(vacío)* | Orígenes permitidos. No se necesita: nginx hace de proxy en el mismo origen. |
| `NGINX_IP` | no | `172.28.10.10` | IP fija de nginx; es el único proxy de confianza para el límite de login. |

`API_BASE_URL` no se usa aquí: solo la necesita el build de Vercel. En Docker el frontend llama a
`/api` en su propio origen.

Las dos URIs apuntan al servicio `mongo` (nombre de host dentro de la red) e incluyen
`replicaSet=rs0`; sin él, las transacciones de MongoDB fallan. Para arrancar los backends fuera de
Docker, cambie el host por `localhost:27017` y use un `mongod` con replica set.

## Criterios de la rúbrica → fragmento del compose

| Criterio | Dónde se cumple |
|---|---|
| **1. Archivo `docker-compose.yml`** con extensión `.yml` | [`docker-compose.yml`](../docker-compose.yml) en la raíz; `docker compose config` lo valida. |
| **2. Servicios** (mínimo 3) | 5 servicios bajo `services:` (ver abajo). |
| **3. Enlaces entre servicios** | URIs y URLs con el nombre del servicio + `depends_on` con `service_healthy` / `service_completed_successfully` (ver abajo). |
| **4. Volúmenes** | Bloque `volumes:` y su uso en 2 servicios (ver abajo). |
| **5. Puertos** | `ports:` solo en `frontend` (ver abajo). |
| **6. Redes** | Bloque `networks:` con `publica` e `interna` (ver abajo). |
| 7. Publicación en Docker Hub y GitHub | Imágenes en `twcfine/pachoclo-*` ([`docker-compose.hub.yml`](../docker-compose.hub.yml)) y código en GitHub; ver la sección [Publicación](#publicación). |

### Servicios

```yaml
services:
  frontend:             # build: ./frontend              (nginx + SPA Angular)
  pachoclo-api:         # build: ./PachocloSystem        (Spring Boot, JWT, roles)
  medicamentos-service: # build: ./MedicamentosService   (Spring Boot, inventario)
  mongo:                # image: mongo:8                 (réplica de un nodo, rs0)
  mongo-init:           # image: mongo:8                 (rs.initiate; termina y sale)
```

Los de larga duración tienen `healthcheck` y `restart: unless-stopped`; `mongo-init` usa
`restart: "no"` porque es de un solo uso.

### Enlaces entre servicios

El nombre del servicio es su nombre de host dentro de la red (DNS interno de Docker):

```yaml
  pachoclo-api:
    environment:
      MEDICAMENTOS_URL: http://medicamentos-service:8081          # API → microservicio
      SPRING_MONGODB_URI: ${PACHOCLO_MONGO_URI}                   # API → MongoDB
    depends_on:
      mongo-init:
        condition: service_completed_successfully
      medicamentos-service:
        condition: service_healthy

  medicamentos-service:
    environment:
      SPRING_MONGODB_URI: ${MEDICAMENTOS_MONGO_URI}
    depends_on:
      mongo-init:
        condition: service_completed_successfully

  mongo-init:
    depends_on:
      mongo:
        condition: service_healthy

  frontend:
    depends_on:
      pachoclo-api:
        condition: service_healthy
```

Además, [`frontend/nginx.conf`](../frontend/nginx.conf) reenvía `/api/` a `http://pachoclo-api:8080`.

### Volúmenes

```yaml
  mongo:
    volumes:
      - mongo-datos:/data/db                      # datos de las dos bases
  pachoclo-api:
    volumes:
      - pachoclo-logs:/var/log/pachoclo           # log de la aplicación (LOGGING_FILE_NAME)

volumes:
  mongo-datos:
  pachoclo-logs:
```

### Puertos

```yaml
  frontend:
    ports:
      - "${FRONTEND_PORT:-4200}:8080"
  frontend-react:
    ports:
      - "${FRONTEND_REACT_PORT:-4300}:8080"
```

Los demás servicios no tienen `ports:`; sus puertos (8080, 8081, 27017) solo existen dentro de las
redes de Docker. Para la demo, `docker-compose.demo.yml` los publica (ver
[Override de demo](#override-de-demo-swagger-y-mongodb-en-el-host)).

### Redes

```yaml
networks:
  publica:                         # frontend y pachoclo-api
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.10.0/24
  interna:                         # MongoDB y medicamentos-service
    driver: bridge
    internal: true                 # sin salida a internet ni al host
```

`pachoclo-api` está en las dos redes (`publica:` e `interna:`); `frontend` solo en `publica`;
`medicamentos-service`, `mongo` y `mongo-init` solo en `interna`. Así el frontend no puede alcanzar
la base ni el microservicio.

## Publicación

Las 4 imágenes propias se publican (públicas) en Docker Hub con los tags `1.0` y `latest`. La base
usa la imagen oficial `mongo:8`, que no se republica.

| Carpeta | Imagen | Docker Hub |
|---|---|---|
| `frontend/` | `twcfine/pachoclo-frontend` | <https://hub.docker.com/r/twcfine/pachoclo-frontend> |
| `frontend-react/` | `twcfine/pachoclo-frontend-react` | <https://hub.docker.com/r/twcfine/pachoclo-frontend-react> |
| `PachocloSystem/` | `twcfine/pachoclo-api` | <https://hub.docker.com/r/twcfine/pachoclo-api> |
| `MedicamentosService/` | `twcfine/pachoclo-medicamentos` | <https://hub.docker.com/r/twcfine/pachoclo-medicamentos> |

Perfil: <https://hub.docker.com/u/twcfine>.

> Las imágenes publicadas deben corresponder a la versión con **MongoDB** (leen
> `SPRING_MONGODB_URI`). Si los tags de Docker Hub son anteriores a la migración, vuelva a
> publicarlos con el script de abajo antes de usar `docker-compose.hub.yml`.
> `pachoclo-frontend-react` es nueva: hay que crear su repositorio público la primera vez.

### Construir y publicar

[`scripts/publicar-imagenes.sh`](../scripts/publicar-imagenes.sh) construye las 4 imágenes desde
sus Dockerfiles, les pone el tag de versión y `latest`, y solo las sube si se le pasa `--push`:

```bash
# 1. Solo construir y etiquetar (no sube nada); sirve para probarlas en local
DOCKERHUB_USER=twcfine IMAGE_TAG=1.0 scripts/publicar-imagenes.sh

# 2. Publicar: iniciar sesión con la cuenta dueña de los repositorios y subir los dos tags
docker login
DOCKERHUB_USER=twcfine IMAGE_TAG=1.0 scripts/publicar-imagenes.sh --push
```

Equivale a estos comandos, por si se prefiere hacerlo a mano:

```bash
U=twcfine T=1.0
docker build -t $U/pachoclo-frontend:$T       ./frontend
docker build -t $U/pachoclo-frontend-react:$T ./frontend-react
docker build -t $U/pachoclo-api:$T            ./PachocloSystem
docker build -t $U/pachoclo-medicamentos:$T   ./MedicamentosService
for i in frontend frontend-react api medicamentos; do
  docker tag  $U/pachoclo-$i:$T $U/pachoclo-$i:latest
  docker push $U/pachoclo-$i:$T
  docker push $U/pachoclo-$i:latest
done
```

Antes de publicar, compruebe que las imágenes no contienen `.env`, claves privadas ni secretos
(solo el bundle de certificados raíz de Alpine) y que los 4 repositorios son públicos.

### Ejecutarlo solo con Docker Hub

[`docker-compose.hub.yml`](../docker-compose.hub.yml) es igual que `docker-compose.yml` salvo que
las 4 imágenes propias se **descargan** (`image:`) en vez de construirse (`build:`). Solo hacen falta
ese archivo y un `.env` copiado de `.env.example`; no hace falta el código fuente. MongoDB se
descarga igualmente de la imagen oficial.

```bash
cp .env.example .env
docker compose -f docker-compose.hub.yml pull
docker compose -f docker-compose.hub.yml up -d      # Angular: http://localhost:4200 · React: http://localhost:4300
docker compose -f docker-compose.hub.yml down       # (con -v también borra los datos)
```

### Override de demo: Swagger y MongoDB en el host

El compose principal no publica los backends ni la base. Para la demo,
[`docker-compose.demo.yml`](../docker-compose.demo.yml) añade esos puertos sin tocar nada más:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

| Qué | URL |
|---|---|
| Swagger PachocloSystem | <http://localhost:8080/swagger-ui.html> |
| Swagger MedicamentosService | <http://localhost:8081/swagger-ui.html> (Authorize → `X-Api-Key` = `MEDICAMENTOS_API_KEY`) |
| MongoDB | `mongodb://127.0.0.1:27018/?directConnection=true` (solo en 127.0.0.1) |

`medicamentos-service` y `mongo` se unen además a una red `demo` normal, porque Docker no publica
puertos de una red `internal: true`.

## Seguridad aplicada

- **Contenedores sin root:** los backends corren como `app` (uid 10001) y nginx como `nginx` (uid 101).
- **Sin secretos en las imágenes:** `.dockerignore` excluye `.env`; todo llega por variables en ejecución.
- **Aislamiento de red:** la base y `medicamentos-service` están en una red `internal`, sin puertos al host
  (salvo con el override de demo).
- **MongoDB sin autenticación, pero inalcanzable desde fuera:** no publica `27017` (el override de demo
  lo abre solo en `127.0.0.1:27018`) y solo está en la
  red interna. Para un despliegue real, añada usuario y contraseña a la URI (Atlas ya lo exige).
- **Límite de intentos de login con la IP real:** `APP_LOGIN_PROXIES_CONFIABLES` es **solo las IP de los dos nginx**
  (`172.28.10.10` y `172.28.10.11`), no toda la subred. Si fuera `172.28.10.0/24`, la puerta de enlace
  (`172.28.10.1`, que es la IP con la que nginx ve al cliente) quedaría como "proxy" y un cliente
  podría falsificar `X-Forwarded-For` para esquivar el bloqueo.
- **Cabeceras en nginx:** `X-Content-Type-Options`, `X-Frame-Options` y `Referrer-Policy`.

## Limitaciones conocidas

- **Sin HTTPS.** Se sirve por `http://localhost:4200`. Con `SPRING_PROFILES_ACTIVE=prod`,
  PachocloSystem exige `CORS_ORIGENES` con al menos un origen `https`, así que este despliegue
  corre en modo desarrollo (perfil vacío). Añadir TLS en nginx es la mejora prevista.
- **La IP del cliente depende de la plataforma.** Desde el mismo equipo (Docker Desktop) todos los
  clientes llegan con la IP de la puerta de enlace de la red, así que comparten límite de intentos.
  Solo se probó desde el mismo equipo: no se verificó cómo se ve un cliente remoto real.
- **Una sola instancia de `pachoclo-api`:** el límite de login y la idempotencia del historial viven
  en su memoria.
- **MongoDB de un solo nodo.** `rs0` da transacciones, no alta disponibilidad: si el contenedor
  `mongo` cae, el sistema responde `503` hasta que vuelva. Para producción, use un replica set
  gestionado (Atlas o similar) con autenticación.
- **No se incluyen** contenedores de Python, Node (servidor) ni Redis: el sistema no los usa.
