-- Momento en que un asesor tomó el control. Si no escribe en un tiempo prudencial
-- (agent.human-idle-minutes), el bot retoma la conversación para que el cliente no quede sin respuesta.
ALTER TABLE conversations ADD COLUMN taken_at TIMESTAMP WITH TIME ZONE;
