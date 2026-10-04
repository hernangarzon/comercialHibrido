-- Momento de la última escalación a un asesor. Permite medir escalaciones por
-- período y el tiempo hasta la primera respuesta humana (dashboard).
-- Aditiva y nula: las conversaciones existentes quedan sin dato hasta su próxima escalación.
ALTER TABLE conversations ADD COLUMN escalated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_conversations_company_escalated ON conversations (company_id, escalated_at);
