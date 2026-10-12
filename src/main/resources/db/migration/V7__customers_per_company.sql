-- Cada cliente final pertenece a una empresa: el mismo número que escribe a dos
-- empresas son dos clientes distintos (nombre, historial y datos separados).

-- 1. Nueva columna con la empresa del cliente.
ALTER TABLE customers ADD COLUMN company_id UUID REFERENCES companies (id);

-- 2. Cada cliente toma la empresa de su conversación más antigua.
UPDATE customers c
   SET company_id = (SELECT cv.company_id
                       FROM conversations cv
                      WHERE cv.customer_id = c.id
                      ORDER BY cv.created_at
                      LIMIT 1);

-- 3. El número deja de ser único global (lo será por empresa al final).
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_phone_number_key;

-- 4. Si el cliente también habló con otras empresas, se crea una copia por cada una...
INSERT INTO customers (id, phone_number, display_name, created_at, updated_at, company_id)
SELECT gen_random_uuid(), c.phone_number, c.display_name, c.created_at, now(), x.company_id
  FROM (SELECT DISTINCT cv.customer_id, cv.company_id
          FROM conversations cv
          JOIN customers owner ON owner.id = cv.customer_id
         WHERE cv.company_id <> owner.company_id) x
  JOIN customers c ON c.id = x.customer_id;

-- 5. ...y esas conversaciones pasan a apuntar a la copia de su empresa.
UPDATE conversations cv
   SET customer_id = (SELECT n.id
                        FROM customers n
                        JOIN customers old ON old.id = cv.customer_id
                       WHERE n.phone_number = old.phone_number
                         AND n.company_id = cv.company_id)
 WHERE cv.company_id <> (SELECT c.company_id FROM customers c WHERE c.id = cv.customer_id);

-- 6. Clientes sin ninguna conversación (no hay empresa a la que asignarlos) se eliminan.
DELETE FROM customers WHERE company_id IS NULL;

-- 7. Empresa obligatoria y número único por empresa.
ALTER TABLE customers ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE customers ADD CONSTRAINT customers_company_phone_key UNIQUE (company_id, phone_number);
