-- Migración V4: Esquema de evidencias digitales periciales y manifiestos firmados
--
-- Nombres de tablas y columnas en inglés según las convenciones del proyecto.
-- Las tablas se ubican en el esquema lógico `visits`, con vistas sinónimas en `visitas`
-- para permitir interoperabilidad con especificaciones de dominio en español.

CREATE TABLE IF NOT EXISTS visits.evidences (
    id UUID PRIMARY KEY,
    visit_id UUID NOT NULL,
    evidence_type VARCHAR(30) NOT NULL,
    storage_path VARCHAR(255) NOT NULL UNIQUE,
    file_name VARCHAR(255),
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_evidences_visit_id ON visits.evidences (visit_id);
CREATE INDEX IF NOT EXISTS idx_evidences_sha256 ON visits.evidences (sha256_hash);

CREATE TABLE IF NOT EXISTS visits.visit_manifests (
    id UUID PRIMARY KEY,
    visit_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES core.users (id),
    device_info VARCHAR(255),
    manifest_data JSONB NOT NULL,
    hmac_signature VARCHAR(64) NOT NULL,
    verification_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_manifests_visit_id ON visits.visit_manifests (visit_id);

-- Sinónimos en español para compatibilidad de dominio
CREATE OR REPLACE VIEW visitas.evidencias AS
    SELECT * FROM visits.evidences;

CREATE OR REPLACE VIEW visitas.manifiestos_visita AS
    SELECT * FROM visits.visit_manifests;
