FROM node:24-alpine AS build

WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime

COPY deploy/gateway/default.conf /etc/nginx/conf.d/default.conf
COPY deploy/gateway/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY deploy/gateway/tls.conf /etc/nginx/snippets/tls.conf
COPY deploy/gateway/proxy-headers.conf /etc/nginx/snippets/proxy-headers.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

USER 101:101

EXPOSE 8080 8443
