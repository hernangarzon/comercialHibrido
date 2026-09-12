# Comercial Híbrido

Backend Spring Boot 3.3.4 con Java 21 para atención comercial híbrida por WhatsApp Cloud API. El bot utiliza un modelo compatible con OpenAI para responder con salida estructurada, calcula un lead score y escala al equipo humano cuando no puede responder con confianza o detecta una oportunidad que requiere intervención.

## Qué quedó implementado

El webhook de WhatsApp valida la firma HMAC cuando `WHATSAPP_APP_SECRET` está configurado, persiste cada mensaje entrante con idempotencia y devuelve HTTP 200 sin esperar al modelo ni a la API de WhatsApp. Un worker de Spring procesa los trabajos persistentes, recupera trabajos bloqueados después de reinicios y aplica reintentos con backoff.

Las respuestas del bot y los mensajes del comercial se guardan como mensajes locales y se entregan mediante un outbox saliente. El identificador de Meta se conserva y los eventos `sent`, `delivered`, `read` y `failed` actualizan el estado de entrega. Si el modelo falla, el cliente recibe un mensaje de fallback y la conversación se escala.

La base de conocimiento se carga desde `config/knowledge-base.md` o desde la ruta indicada por `AGENT_KNOWLEDGE_BASE_FILE`. Debe reemplazarse por información aprobada de catálogo, precios, disponibilidad y políticas. Si no existe información suficiente, el prompt ordena escalar y no inventar.

El API del panel permite listar conversaciones por estado, consultar mensajes, tomar control, liberar control y encolar mensajes manuales. Los endpoints `/api/**` aceptan `X-Agent-Panel-Token` cuando `AGENT_PANEL_TOKEN` está configurado. El WebSocket `/ws` usa los orígenes de `PANEL_ALLOWED_ORIGINS`.

## Ejecución local

Se requiere Java 21 y Maven. Copia `.env.example` a `.env`, completa las credenciales y prepara PostgreSQL. Para ejecutar con Maven:

```bash
./mvnw test
./mvnw spring-boot:run
```

El endpoint de salud es `GET /health`. El webhook se publica en `POST /webhook/whatsapp` y la verificación de Meta se realiza mediante `GET /webhook/whatsapp`.

## Ejecución 24/7 con Docker Compose

Copia primero los archivos de ejemplo y modifica los valores:

```bash
cp .env.example .env
# edita .env y config/knowledge-base.md
docker compose up -d --build
docker compose ps
curl http://localhost:8080/health
```

El servicio `app` y PostgreSQL tienen `restart: unless-stopped`; los datos de PostgreSQL permanecen en el volumen `postgres-data`. En producción, publica el puerto mediante HTTPS y configura en Meta la URL pública terminada en `/webhook/whatsapp`.

## Variables importantes

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión JDBC a PostgreSQL cuando no se usa Compose. |
| `OPENAI_API_BASE_URL`, `OPENAI_API_KEY`, `OPENAI_MODEL` | Proveedor y modelo de chat compatible con `/chat/completions`. |
| `WHATSAPP_API_BASE_URL`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN` | Envío de mensajes a WhatsApp Cloud API. |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | Token de verificación usado por Meta en el GET del webhook. |
| `WHATSAPP_APP_SECRET` | Secreto para verificar `X-Hub-Signature-256`; debe configurarse en producción. |
| `AGENT_KNOWLEDGE_BASE_FILE` | Ruta absoluta o relativa al archivo de conocimiento comercial. |
| `AGENT_PANEL_TOKEN` | Token requerido por `/api/**`; no dejar vacío en producción. |
| `PANEL_ALLOWED_ORIGINS` | Orígenes separados por coma permitidos para `/ws`. |
| `AGENT_MAX_ATTEMPTS` | Intentos máximos para jobs LLM o WhatsApp. |
| `AGENT_RETRY_BASE_SECONDS` | Base del backoff exponencial de reintentos. |
| `AGENT_STALE_JOB_MINUTES` | Tiempo para recuperar un job que quedó en `PROCESSING`. |

## Flujo operativo

```text
Meta -> POST webhook -> persistencia + idempotencia -> HTTP 200
                                      |
                                      v
                           inbound_message_jobs
                                      |
                                      v
                           worker -> LLM -> outbox
                                      |
                                      v
                           WhatsApp Cloud API
                                      |
                                      v
                         statuses -> estado de entrega
```

El worker usa un solo hilo dentro de la instancia para mantener un orden predecible. La base de datos es la fuente durable de trabajos; por eso el proceso puede reiniciarse sin perder mensajes ya aceptados por el webhook.

## API del panel

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/conversations?status=ESCALADO_PENDIENTE` | Lista hasta 100 conversaciones por estado. |
| `GET` | `/api/conversations/{id}/mensajes` | Devuelve el historial ordenado. |
| `POST` | `/api/conversations/{id}/tomar-control` | Cambia a control humano. |
| `POST` | `/api/conversations/{id}/liberar-control` | Devuelve la conversación al bot. |
| `POST` | `/api/conversations/{id}/mensajes` | Encola un mensaje del comercial. |
| `GET` | `/health` | Health check del proceso HTTP. |
| `GET` | `/webhook/whatsapp` | Verificación de callback de Meta. |
| `POST` | `/webhook/whatsapp` | Recepción de mensajes y estados. |

## Validación

La verificación realizada con Java 21 fue:

```bash
./mvnw clean test
./mvnw -DskipTests package
```

El proyecto compila, el contexto Spring inicia con H2 y las pruebas de firma HMAC e idempotencia pasan. Las migraciones Flyway de producción requieren PostgreSQL real; el entorno de revisión no tenía Docker ni un servidor PostgreSQL disponible, por lo que debe ejecutarse esa comprobación durante el primer despliegue.

## Pendientes antes de producción

El archivo de conocimiento debe contener datos reales y aprobados. También hay que configurar las credenciales de Meta y del proveedor LLM, publicar el endpoint por HTTPS, registrar el webhook en Meta, restringir `PANEL_ALLOWED_ORIGINS`, configurar `AGENT_PANEL_TOKEN` y confirmar el acceso del comercial a un panel Angular u otra interfaz que consuma este API y el WebSocket. El proyecto entregado sigue siendo backend; el frontend Angular descrito en el documento original no formaba parte del ZIP.
