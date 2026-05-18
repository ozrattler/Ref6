# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

# Install dependencies first (layer-cached unless package files change)
COPY package.json package-lock.json ./
RUN npm ci --prefer-offline

# Copy source and build with root base path (no /Ref6/ prefix — served at /)
COPY . .
RUN npm run build:docker

# ── Stage 2: serve ────────────────────────────────────────────────────────────
FROM nginx:alpine

# Remove default nginx static content and config
RUN rm -rf /usr/share/nginx/html/* \
           /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/ || exit 1
