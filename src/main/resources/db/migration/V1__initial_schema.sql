CREATE TABLE customers (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    assigned_salesperson_id UUID,
    summary TEXT,
    last_summarized_at TIMESTAMP WITH TIME ZONE,
    lead_score INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT,

    CONSTRAINT fk_conversations_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id),

    CONSTRAINT chk_conversations_lead_score
        CHECK (lead_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_conversations_customer_id
    ON conversations(customer_id);

CREATE INDEX idx_conversations_status
    ON conversations(status);

CREATE INDEX idx_conversations_assigned_salesperson
    ON conversations(assigned_salesperson_id);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    whatsapp_message_id VARCHAR(255) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversations(id)
);

CREATE INDEX idx_messages_conversation_id_created_at
    ON messages(conversation_id, created_at DESC);

CREATE INDEX idx_messages_whatsapp_message_id
    ON messages(whatsapp_message_id);