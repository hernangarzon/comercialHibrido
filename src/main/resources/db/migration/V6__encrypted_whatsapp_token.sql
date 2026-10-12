-- El token de WhatsApp de cada empresa se guarda cifrado (AES-256-GCM, prefijo "enc:v1:"),
-- que ocupa más que el token original: la columna pasa a TEXT.
ALTER TABLE companies ALTER COLUMN whatsapp_access_token TYPE TEXT;
