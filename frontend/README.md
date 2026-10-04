# Panel web — Comercial Híbrido

Panel de los asesores: bandeja de conversaciones de WhatsApp, chat en vivo y alertas de leads escalados.
React 19 + TypeScript + Vite + Tailwind CSS 4, con TanStack Query para los datos y STOMP/SockJS para el tiempo real.

## Desarrollo

Con el backend corriendo en `:8084` (por ejemplo `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` en la raíz):

```bash
npm install
npm run dev     # http://localhost:5173, con recarga en caliente y proxy a /api y /ws
```

## Build

```bash
npm run build   # genera ../src/main/resources/static, que Spring Boot sirve en /
```

La carpeta generada no se versiona; el `Dockerfile` la compila en su propia etapa.

## Estructura

- `src/lib`: cliente del API, sesión, WebSocket, notificaciones y utilidades.
- `src/components`: componentes visuales reutilizables.
- `src/features/auth`: inicio de sesión.
- `src/features/inbox`: bandeja, chat, detalles del cliente y respuestas rápidas.
