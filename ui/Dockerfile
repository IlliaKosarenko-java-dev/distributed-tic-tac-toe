# The UI has no build step, so there is nothing to compile — just static files on a web
# server that is good at serving them (etags, gzip, cache headers).
FROM nginx:alpine

COPY index.html app.js styles.css /usr/share/nginx/html/

EXPOSE 80
