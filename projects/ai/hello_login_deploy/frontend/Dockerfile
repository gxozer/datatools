# Stage 1: build & test
FROM node:20-slim AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# Run tests before building — a failing test breaks the image build
RUN npm test

RUN npx vite build

# Stage 2: serve with nginx
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html

# SPA routing + /api proxy to the backend container (service name: backend)
# BACKEND_HOST can be overridden at runtime via envsubst; defaults to "backend"
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV BACKEND_HOST=backend

EXPOSE 80
