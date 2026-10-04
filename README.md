# Comercial Híbrido

Backend Spring Boot 3.3.4 con Java 21 para atención comercial híbrida por WhatsApp Cloud API, con panel web para los comerciales. El bot utiliza un modelo compatible con OpenAI para responder con salida estructurada, calcula un lead score y escala al equipo humano cuando no puede responder con confianza o detecta una oportunidad que requiere intervención.

## Qué hace

El webhook de WhatsApp verifica la firma HMAC (`X-Hub-Signature-256`) de cada petición, persiste los mensajes entrantes con idempotencia y devuelve HTTP 200 sin esperar al modelo ni a la API de WhatsApp. Si falla la base de datos responde 500 para que Meta reintente. Un worker procesa los trabajos persistentes, los reclama de forma atómica (seguro con varias instancias), recupera trabajos bloqueados después de reinicios y aplica reintentos con backoff.

Las respuestas del bot y los mensajes del comercial se entregan mediante un outbox saliente, usando el número y token de WhatsApp de la empresa dueña de la conversación. Los eventos `sent`, `delivered`, `read` y `failed` de Meta actualizan el estado de entrega. Si el modelo falla, el cliente recibe un mensaje de cortesía y la conversación se escala.

Es multiempresa: cada empresa (`companies`) tiene su número de WhatsApp, su catálogo (`products`), su base de conocimiento y sus usuarios. El catálogo y la base de conocimiento que usa el bot se leen de la base de datos (`companies.knowledge_base`, `companies.custom_prompt`, `products`). El archivo `config/knowledge-base.md` y `AGENT_KNOWLEDGE_BASE_FILE` ya no se usan.

## Landing y panel web

El frontend vive en [frontend/](frontend/) (React + TypeScript + Vite + Tailwind) y genera dos páginas:

- **`/` Landing de ventas**: presentación del producto, preguntas frecuentes y formulario de solicitud de demo (con protección anti-spam). Si `SALES_WHATSAPP` está definido, muestra además un botón para escribir por WhatsApp.
- **`/app/` Panel** de cada empresa, con estas secciones:

- **Bandeja**: conversaciones en vivo para los asesores.
- **Resultados**: dashboard con conversaciones activas, % resuelto por el bot, leads calientes, escalaciones y tiempo de respuesta del asesor, comparados con el período anterior (7, 30 o 90 días), gráfico de mensajes por día, estado actual y leads más calientes.
- **Bot**: editor de directrices y base de conocimiento, catálogo de productos (alta, edición, disponibilidad) y un probador que responde como el bot real sin enviar nada por WhatsApp. Editar requiere rol `ADMIN`; un `COMERCIAL` lo ve en modo lectura.
- **Ajustes**: lista de puesta en marcha, equipo (invitar con contraseña temporal, roles, desactivar, restablecer contraseña), conexión del número de WhatsApp verificada contra Meta, y marca blanca (color y logo de la empresa aplicados a todo el panel).
- **Plataforma**: solo para los dueños de la plataforma (`PLATFORM_ADMIN_EMAILS`). *Solicitudes* lista las demos pedidas en la landing y permite convertirlas en cliente (crea la empresa y su primer administrador con contraseña temporal). *Clientes* lista las empresas y permite darlas de alta o suspenderlas: una empresa suspendida no puede entrar al panel y el bot deja de atenderla.

### Ventana de 24 horas de WhatsApp

Meta solo permite escribir texto libre hasta 24 horas después del último mensaje del cliente. Pasado ese plazo, el chat muestra la ventana como cerrada y ofrece las **plantillas aprobadas** de la cuenta de WhatsApp Business de la empresa (se leen de Meta en vivo; requieren el ID de la cuenta en Ajustes → WhatsApp). El asesor completa los datos de la plantilla, ve el mensaje final y lo envía; cuando el cliente responde, se reabre la ventana.

La bandeja incluye contadores por estado, búsqueda (Ctrl K), orden por score, mensajes sin leer, chat con estados de entrega, adjuntos, respuestas rápidas con `/`, ficha del cliente con lead score y resumen del bot, alertas en vivo (aviso, sonido, notificación del navegador y contador en la pestaña) y diseño adaptado a celular.

Se compila dentro del jar: `npm run build` en `frontend/` genera `src/main/resources/static`, que no se versiona. El `Dockerfile` lo compila automáticamente. Para trabajar en el panel con recarga en caliente, ver [frontend/README.md](frontend/README.md).

## Ejecución local sin base de datos (perfil `dev`)

Para desarrollar o hacer demos sin PostgreSQL. Usa H2 en memoria y carga datos de ejemplo (dos empresas, conversaciones en cada estado y un catálogo):

```bash
(cd frontend && npm install && npm run build)   # solo la primera vez o tras cambiar el panel
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La landing queda en http://localhost:8084 y el panel en http://localhost:8084/app/. Entra con `admin@demo.com` / `demo1234` (administrador del Laboratorio Dental Demo y, en `dev`, dueño de la plataforma), `comercial@demo.com` / `demo1234` (comercial del mismo laboratorio) u `otra@demo.com` / `demo1234` (Óptica Demo). El laboratorio trae 30 días de actividad simulada para que el dashboard tenga datos en una demo. Los datos se pierden al reiniciar.

En `dev` el App Secret del webhook es `dev-app-secret`, así que se pueden simular mensajes de WhatsApp firmándolos en local:

```bash
BODY='{"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"dev-phone-1"},"contacts":[{"wa_id":"000000","profile":{"name":"Prueba"}}],"messages":[{"from":"000000","id":"wamid.PRUEBA1","type":"text","text":{"body":"Hola"}}]}}]}]}'
SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac dev-app-secret | awk '{print $NF}')"
curl -X POST http://localhost:8084/webhook/whatsapp -H 'Content-Type: application/json' -H "X-Hub-Signature-256: $SIG" -d "$BODY"
```

## Ejecución local con PostgreSQL

Se requiere Java 21 y Node.js 20+ (para el panel). Copia `.env.example` a `.env` y completa las credenciales: la aplicación lee `.env` automáticamente al arrancar. `.env` está en `.gitignore` y nunca debe subirse.

```bash
./mvnw test
./mvnw spring-boot:run
```

El endpoint de salud es `GET /health`. El webhook se publica en `POST /webhook/whatsapp` y la verificación de Meta se realiza mediante `GET /webhook/whatsapp`.

## Despliegue en Railway

El repositorio incluye `railway.json`: Railway construye con el `Dockerfile` (panel + backend), revisa `/health` y reinicia ante fallos. La app escucha en el `PORT` que asigna Railway y respeta su proxy HTTPS.

1. En Railway: **New Project → Deploy from GitHub repo** y elige este repositorio (rama `main`). Cada push a `main` vuelve a desplegar.
2. En el servicio, **Variables → Raw Editor**: pega las variables de tu `.env` (no hace falta `SERVER_PORT`). Mínimo: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `OPENAI_API_KEY`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_WEBHOOK_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET`, `AGENT_JWT_SECRET`, `PLATFORM_ADMIN_EMAILS` (y opcionales `OPENAI_API_BASE_URL`, `OPENAI_MODEL`, `WHATSAPP_API_BASE_URL`, `SALES_WHATSAPP`).
3. **Settings → Networking → Generate Domain** para obtener la URL pública `https://<tu-app>.up.railway.app` (o conecta un dominio propio).
4. En Meta (tu app → WhatsApp → Configuración): **URL de devolución de llamada** `https://<tu-app>.up.railway.app/webhook/whatsapp`, **token de verificación** = `WHATSAPP_WEBHOOK_VERIFY_TOKEN`, y suscríbete al campo `messages`.
5. Comprueba: `https://<tu-app>.up.railway.app/health` responde `UP`, la landing carga en `/` y el panel en `/app/`.

Mantén una sola réplica mientras el volumen sea bajo (el worker admite varias, pero no hace falta). Las migraciones Flyway se aplican solas al arrancar.

## Ejecución 24/7 con Docker Compose

```bash
cp .env.example .env
# edita .env
docker compose up -d --build
docker compose ps
curl http://localhost:8080/health
```

El servicio `app` y PostgreSQL tienen `restart: unless-stopped`; los datos de PostgreSQL permanecen en el volumen `postgres-data`. En producción, publica el puerto mediante HTTPS y configura en Meta la URL pública terminada en `/webhook/whatsapp`.

## Variables importantes

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión JDBC a PostgreSQL. Obligatorias fuera del perfil `dev`. |
| `OPENAI_API_BASE_URL`, `OPENAI_API_KEY`, `OPENAI_MODEL` | Proveedor y modelo de chat compatible con `/chat/completions` (por defecto Groq). |
| `WHATSAPP_API_BASE_URL`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN` | Credenciales globales de WhatsApp Cloud API; se usan si la empresa no tiene las suyas. |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | Token de verificación usado por Meta en el GET del webhook. |
| `WHATSAPP_APP_SECRET` | Secreto para verificar `X-Hub-Signature-256`. **Obligatorio**: sin él el webhook responde 403 a todo. |
| `AGENT_JWT_SECRET` | Secreto para firmar los JWT del panel, mínimo 32 caracteres (`openssl rand -base64 48`). La app no arranca sin él. |
| `PLATFORM_ADMIN_EMAILS` | Correos de los dueños de la plataforma: ven las solicitudes de la landing y el webhook a registrar en Meta. |
| `SALES_WHATSAPP` | Número de ventas para el botón de WhatsApp de la landing. Vacío = sin botón. |
| `PANEL_ALLOWED_ORIGINS` | Orígenes extra, separados por coma, para `/ws` si el panel vive en otro dominio. El mismo origen siempre se acepta. |
| `SERVER_PORT` | Puerto HTTP (por defecto 8084; 8080 en Docker Compose). |
| `AGENT_MAX_ATTEMPTS` | Intentos máximos para jobs LLM o WhatsApp. |
| `AGENT_RETRY_BASE_SECONDS` | Base del backoff exponencial de reintentos. |
| `AGENT_STALE_JOB_MINUTES` | Tiempo para recuperar un job que quedó en `PROCESSING`. |

## Flujo operativo

```text
Meta -> POST webhook -> firma HMAC -> persistencia + idempotencia -> HTTP 200
                                      |
                                      v
                           inbound_message_jobs
                                      |
                                      v
                           worker -> LLM -> outbox
                                      |
                                      v
                    WhatsApp Cloud API (número de la empresa)
                                      |
                                      v
                         statuses -> estado de entrega
```

La base de datos es la fuente durable de trabajos; por eso el proceso puede reiniciarse sin perder mensajes ya aceptados por el webhook.

## Seguridad del panel

- `POST /api/auth/login` devuelve un JWT (7 días) que el panel envía como `Authorization: Bearer ...` en todo `/api/**` (salvo `/api/public/**`, que usa la landing).
- En cada petición se consulta el usuario en la base: desactivar una cuenta o cambiar su rol aplica de inmediato, sin esperar a que venza el token.
- Las invitaciones y restablecimientos generan una contraseña temporal que se muestra una sola vez; con ella el usuario solo puede crear su propia contraseña.
- Las contraseñas se guardan con BCrypt. Las cuentas con hash SHA-256 antiguo o en texto plano siguen funcionando y se migran a BCrypt en su primer login.
- Cada usuario solo ve y opera conversaciones y archivos de su empresa; para las de otra empresa el API responde 404.
- El WebSocket `/ws` (STOMP) exige el JWT en el frame `CONNECT` y solo permite suscribirse a `/topic/empresa/{companyId}` de la propia empresa.

## API del panel

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/login` | Login con email y contraseña; devuelve el JWT. |
| `GET` | `/api/conversations?status=ESCALADO_PENDIENTE` | Lista conversaciones de la empresa, opcionalmente por estado. |
| `GET` | `/api/conversations/{id}/mensajes` | Devuelve el historial ordenado. |
| `POST` | `/api/conversations/{id}/tomar-control` | Cambia a control humano y asigna al usuario autenticado. |
| `POST` | `/api/conversations/{id}/liberar-control` | Devuelve la conversación al bot. |
| `POST` | `/api/conversations/{id}/archivar` | Archiva la conversación. |
| `POST` | `/api/conversations/{id}/mensajes` | Encola un mensaje del comercial (requiere control humano). |
| `GET` | `/api/media/{mediaId}` | Descarga una imagen o documento recibido por WhatsApp. |
| `GET` | `/api/dashboard?days=7&tz=America/Bogota` | Métricas del período (7, 30 o 90 días) y del período anterior. |
| `GET` | `/api/company` | Base de conocimiento y directrices del bot. |
| `PUT` | `/api/company/knowledge` | Actualiza conocimiento y directrices (ADMIN). |
| `GET` | `/api/products` | Catálogo de la empresa. |
| `POST`, `PUT`, `DELETE` | `/api/products[/{id}]` | Crea, edita o elimina productos (ADMIN). |
| `POST` | `/api/bot/preview` | Respuesta de prueba del bot para un historial simulado. |
| `PUT` | `/api/account/password` | Cambia la contraseña propia (también la temporal). |
| `GET`, `POST` | `/api/team` | Lista el equipo / invita a un usuario (ADMIN). |
| `PUT` | `/api/team/{id}` | Cambia rol o activa/desactiva (ADMIN). |
| `POST` | `/api/team/{id}/reset-password` | Genera una contraseña temporal (ADMIN). |
| `PUT` | `/api/company/branding` | Color y logo de la empresa (ADMIN). |
| `PUT` | `/api/company/whatsapp` | Verifica contra Meta y guarda el número de la empresa (ADMIN). |
| `POST` | `/api/company/whatsapp/test` | Prueba la conexión del número guardado. |
| `GET` | `/api/company/onboarding` | Pasos de puesta en marcha y su estado. |
| `GET`, `PUT` | `/api/platform/leads[/{id}]` | Solicitudes de la landing (dueños de la plataforma). |
| `GET`, `POST` | `/api/platform/companies` | Lista empresas clientes / crea una con su administrador (dueños de la plataforma). |
| `PUT` | `/api/platform/companies/{id}` | Suspende o reactiva una empresa (dueños de la plataforma). |
| `GET` | `/api/company/whatsapp/templates` | Plantillas aprobadas en Meta para la empresa. |
| `POST` | `/api/conversations/{id}/plantilla` | Envía una plantilla aprobada (necesaria con la ventana de 24 h cerrada). |
| `GET` | `/api/public/config` | Configuración pública de la landing. |
| `POST` | `/api/public/leads` | Solicitud de demo desde la landing (sin autenticación, limitada por IP). |
| `GET` | `/health` | Health check del proceso HTTP. |
| `GET` | `/webhook/whatsapp` | Verificación de callback de Meta. |
| `POST` | `/webhook/whatsapp` | Recepción de mensajes y estados. |

## Base de datos y migraciones

El esquema lo gestiona Flyway (`src/main/resources/db/migration`) y Hibernate solo lo valida (`ddl-auto=validate`). `V1` refleja el esquema de producción al adoptar Flyway; una base existente sin historial se marca como V1 automáticamente (`baseline-on-migrate`) sin ejecutar nada sobre ella. Los cambios de esquema se hacen siempre con una migración nueva (`V3__...sql`), nunca editando las anteriores.

## Tests

```bash
./mvnw test
```

Las llamadas a Meta (verificación de número, plantillas y envío) se prueban contra un servidor que la simula, con el worker entregando los mensajes de verdad. Cubren además hashing de contraseñas, JWT, firma HMAC del webhook y autenticación del WebSocket; contra el API real sobre H2: login, aislamiento entre empresas, idempotencia del webhook, estados de entrega, reclamo atómico de jobs, permisos de ADMIN, catálogo, probador y cálculo de métricas; y las migraciones Flyway aplicadas desde cero sobre un PostgreSQL real embebido.

## Pendientes antes de producción

- `customers.phone_number` es único global: un mismo cliente que escribe a dos empresas comparte el registro de cliente (las conversaciones sí quedan separadas por empresa).
- Publicar el endpoint por HTTPS, registrar el webhook en Meta y configurar `WHATSAPP_APP_SECRET`.
