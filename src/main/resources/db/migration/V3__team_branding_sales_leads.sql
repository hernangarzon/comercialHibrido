-- Fase 3: equipo, marca blanca y solicitudes de la landing. Solo agrega; no modifica datos existentes.

-- Contraseña temporal (invitación o restablecimiento): obliga a cambiarla al entrar.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Marca blanca por empresa. El logo se guarda como data URL (imagen pequeña, máx. ~200 KB).
ALTER TABLE companies ADD COLUMN brand_color VARCHAR(7);
ALTER TABLE companies ADD COLUMN logo_data_url TEXT;

-- Solicitudes de demo enviadas desde la landing pública.
CREATE TABLE sales_leads (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(120) NOT NULL,
    company_name VARCHAR(150) NOT NULL,
    email        VARCHAR(150) NOT NULL,
    phone        VARCHAR(40)  NOT NULL,
    message      VARCHAR(2000),
    status       VARCHAR(20)  NOT NULL DEFAULT 'NUEVA',
    source_ip    VARCHAR(64),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_sales_leads_created ON sales_leads (created_at DESC);
