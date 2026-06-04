FROM node:22 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . ./
RUN npm run build

FROM nginx:1.27-alpine
ARG SSL=false
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-ssl.conf /tmp/nginx-ssl.conf
RUN if [ "$SSL" = "true" ]; then \
    apk add --no-cache openssl && \
    mkdir -p /etc/nginx/ssl && \
    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
        -keyout /etc/nginx/ssl/key.pem \
        -out /etc/nginx/ssl/cert.pem \
        -subj "/CN=log-analyzer" && \
    cp /tmp/nginx-ssl.conf /etc/nginx/conf.d/default.conf; \
fi
EXPOSE 80 443
