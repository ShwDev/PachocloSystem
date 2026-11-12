# syntax=docker/dockerfile:1
# Aplicación React de Medicamentos. Etapa Node para `vite build` y etapa nginx
# (sin privilegios) para servir. Sin VITE_API_BASE_URL el navegador llama a /api
# en el mismo origen y nginx lo reenvía a PachocloSystem (ver nginx.conf).

# ---- Etapa 1: compilar ----
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
# Sin cache mount, como los demás Dockerfile (Railway no los admite sin id propio).
RUN npm ci

COPY . .
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---- Etapa 2: servir ----
# nginx-unprivileged corre como usuario no root (uid 101) y escucha en el 8080.
FROM nginxinc/nginx-unprivileged:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -qO /dev/null http://127.0.0.1:8080/ || exit 1
