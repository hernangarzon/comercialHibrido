-- Esquema base de Comercial Híbrido.
-- Refleja exactamente el esquema de producción (Supabase) al adoptar Flyway.
-- Las bases existentes se marcan como V1 con baseline (spring.flyway.baseline-on-migrate),
-- así que este script solo se ejecuta en instalaciones nuevas.
-- Los ids los genera Hibernate; los DEFAULT son solo para inserciones manuales.

CREATE TABLE companies (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(100) NOT NULL,
    whatsapp_phone_number_id VARCHAR(50)  NOT NULL,
    whatsapp_access_token    VARCHAR(500),
    knowledge_base           TEXT,
    custom_prompt            TEXT,
    active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT companies_whatsapp_phone_number_id_key UNIQUE (whatsapp_phone_number_id)
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID NOT NULL REFERENCES companies (id),
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL DEFAULT 'COMERCIAL',
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT users_email_key UNIQUE (email)
);

CREATE INDEX idx_users_company_email ON users (company_id, email);

CREATE TABLE products (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id         UUID NOT NULL REFERENCES companies (id),
    name               VARCHAR(150) NOT NULL,
    category           VARCHAR(80),
    material           VARCHAR(100),
    description        TEXT,
    price              NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(10) NOT NULL DEFAULT 'USD',
    delivery_time_days INTEGER,
    warranty_months    INTEGER DEFAULT 12,
    available          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_company_available ON products (company_id, available);

CREATE TABLE customers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT customers_phone_number_key UNIQUE (phone_number)
);

CREATE TABLE conversations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id              UUID NOT NULL REFERENCES companies (id),
    customer_id             UUID NOT NULL REFERENCES customers (id),
    status                  VARCHAR(30) NOT NULL DEFAULT 'BOT_ACTIVO',
    assigned_salesperson_id UUID,
    summary                 TEXT,
    last_summarized_at      TIMESTAMP WITH TIME ZONE,
    lead_score              INTEGER NOT NULL DEFAULT 0,
    version                 BIGINT DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_company_status ON conversations (company_id, status);

CREATE TABLE messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID NOT NULL REFERENCES conversations (id),
    sender              VARCHAR(20) NOT NULL,
    content             TEXT NOT NULL,
    whatsapp_message_id VARCHAR(255),
    delivery_status     VARCHAR(20) DEFAULT 'PENDING',
    delivery_error      VARCHAR(2000),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    media_type          VARCHAR(30) DEFAULT 'TEXT',
    media_id            VARCHAR(255),
    media_filename      VARCHAR(255),
    CONSTRAINT messages_whatsapp_message_id_key UNIQUE (whatsapp_message_id)
);

CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);

CREATE TABLE inbound_message_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    whatsapp_message_id VARCHAR(255) NOT NULL,
    conversation_id     UUID NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    locked_at           TIMESTAMP WITH TIME ZONE,
    last_error          VARCHAR(2000),
    version             BIGINT DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT inbound_message_jobs_whatsapp_message_id_key UNIQUE (whatsapp_message_id)
);

CREATE INDEX idx_inbound_jobs_status_attempt ON inbound_message_jobs (status, next_attempt_at);

CREATE TABLE outbound_message_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id          UUID NOT NULL,
    conversation_id     UUID NOT NULL,
    to_phone_number     VARCHAR(40) NOT NULL,
    body                TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    delivery_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    whatsapp_message_id VARCHAR(255),
    attempts            INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    locked_at           TIMESTAMP WITH TIME ZONE,
    last_error          VARCHAR(2000),
    version             BIGINT DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT outbound_message_jobs_message_id_key UNIQUE (message_id)
);

CREATE INDEX idx_outbound_jobs_status_attempt ON outbound_message_jobs (status, next_attempt_at);
