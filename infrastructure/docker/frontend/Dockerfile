# -----------------------------------------------------------------------
# FINOVA — Vue 3 frontend
# Two stages: build the SPA, then serve the static bundle from nginx.
# nginx also reverse-proxies /api to the api-gateway so the browser only
# ever talks to one origin (no CORS in the production topology).
# -----------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

COPY frontend/ ./

# The bundle is built against the gateway origin, never a hardcoded host.
ARG VITE_API_BASE_URL=/api
ARG VITE_USE_MOCKS=false
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_USE_MOCKS=${VITE_USE_MOCKS}

RUN npm run build:only

FROM nginx:1.27-alpine
RUN apk add --no-cache curl \
    && rm -rf /usr/share/nginx/html/*

COPY infrastructure/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=4s --start-period=10s --retries=5 \
  CMD curl -fsS http://localhost/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]