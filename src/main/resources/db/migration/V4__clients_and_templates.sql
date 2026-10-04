-- Alta de clientes desde la plataforma y plantillas de WhatsApp (ventana de 24 h).

-- Una empresa recién creada aún no tiene número conectado. UNIQUE en PostgreSQL
-- admite varios NULL, así que la unicidad se mantiene para los números reales.
ALTER TABLE companies ALTER COLUMN whatsapp_phone_number_id DROP NOT NULL;

-- Cuenta de WhatsApp Business (WABA): necesaria para listar las plantillas aprobadas.
ALTER TABLE companies ADD COLUMN whatsapp_business_account_id VARCHAR(50);

-- Mensajes salientes que son plantillas (fuera de la ventana de 24 h).
ALTER TABLE outbound_message_jobs ADD COLUMN template_name VARCHAR(512);
ALTER TABLE outbound_message_jobs ADD COLUMN template_language VARCHAR(15);
ALTER TABLE outbound_message_jobs ADD COLUMN template_params TEXT;
