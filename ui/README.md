# UI

Three static files. No build step, no npm, no framework.

Served by nginx in `docker compose`; the gateway routes `/` here and `/api/**` to the services,
so the browser only ever talks to one origin and there is no CORS configuration anywhere.

## Running without Docker

    python3 -m http.server 3000 --directory ui

then point the gateway at it:

    UI_URL=http://localhost:3000 mvn -pl api-gateway spring-boot:run
