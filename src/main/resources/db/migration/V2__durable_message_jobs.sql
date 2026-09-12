ALTER TABLE messages
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE messages
    ADD COLUMN delivery_error TEXT;

CREATE TABLE inbound_message_jobs (
    id UUID PRIMARY KEY,
    whatsapp_message_id VARCHAR(255) NOT NULL UNIQUE,
    conversation_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT,
    CONSTRAINT fk_inbound_jobs_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversations(id)
);

CREATE INDEX idx_inbound_jobs_due
    ON inbound_message_jobs(status, next_attempt_at);

CREATE TABLE outbound_message_jobs (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL UNIQUE,
    conversation_id UUID NOT NULL,
    to_phone_number VARCHAR(40) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    whatsapp_message_id VARCHAR(255) UNIQUE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT,
    CONSTRAINT fk_outbound_jobs_message
        FOREIGN KEY (message_id)
        REFERENCES messages(id),
    CONSTRAINT fk_outbound_jobs_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversations(id)
);

CREATE INDEX idx_outbound_jobs_due
    ON outbound_message_jobs(status, next_attempt_at);

CREATE INDEX idx_outbound_jobs_whatsapp_message_id
    ON outbound_message_jobs(whatsapp_message_id);
