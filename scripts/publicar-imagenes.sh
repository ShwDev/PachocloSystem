#!/usr/bin/env bash
# Construye, etiqueta y (opcionalmente) publica en Docker Hub las 4 imágenes propias:
#   pachoclo-frontend, pachoclo-frontend-react, pachoclo-api y pachoclo-medicamentos.
#
# Uso (desde la raíz del repositorio, en Git Bash o cualquier shell POSIX):
#   scripts/publicar-imagenes.sh            # solo build + tags (no sube nada)
#   scripts/publicar-imagenes.sh --push     # además hace docker push de los dos tags
#
# Variables (opcionales):
#   DOCKERHUB_USER  usuario u organización de Docker Hub      (por defecto: twcfine)
#   IMAGE_TAG       tag de versión                              (por defecto: 1.0)
#
# Antes de --push: docker login con la cuenta de DOCKERHUB_USER (el script no pide credenciales).
set -euo pipefail

USUARIO="${DOCKERHUB_USER:-twcfine}"
TAG="${IMAGE_TAG:-1.0}"
PUBLICAR=false
[[ "${1:-}" == "--push" ]] && PUBLICAR=true

cd "$(dirname "$0")/.."

# carpeta -> nombre de la imagen (los mismos que usan docker-compose.yml y docker-compose.hub.yml)
declare -A IMAGENES=(
  [frontend]=pachoclo-frontend
  [frontend-react]=pachoclo-frontend-react
  [PachocloSystem]=pachoclo-api
  [MedicamentosService]=pachoclo-medicamentos
)

for carpeta in frontend frontend-react PachocloSystem MedicamentosService; do
  imagen="$USUARIO/${IMAGENES[$carpeta]}"
  echo "==> build $imagen:$TAG  (desde ./$carpeta)"
  docker build -t "$imagen:$TAG" "./$carpeta"
  docker tag "$imagen:$TAG" "$imagen:latest"
done

echo
echo "Imágenes listas:"
docker image ls --filter "reference=$USUARIO/pachoclo-*" --format '  {{.Repository}}:{{.Tag}}  {{.Size}}'

if $PUBLICAR; then
  for carpeta in frontend frontend-react PachocloSystem MedicamentosService; do
    imagen="$USUARIO/${IMAGENES[$carpeta]}"
    echo "==> push $imagen:$TAG y :latest"
    docker push "$imagen:$TAG"
    docker push "$imagen:latest"
  done
else
  echo
  echo "No se ha publicado nada. Para subirlas: docker login && $0 --push"
fi
