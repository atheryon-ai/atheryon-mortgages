-- Column mapping configuration per migration job
CREATE TABLE migration_field_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES migration_jobs(id) ON DELETE CASCADE,
    source_column   VARCHAR(200) NOT NULL,
    target_path     VARCHAR(500),
    confidence      DECIMAL(3,2),
    status          VARCHAR(10) NOT NULL DEFAULT 'SUGGESTED'
                    CHECK (status IN ('SUGGESTED','CONFIRMED','REJECTED','MANUAL')),
    transform_type  VARCHAR(20),
    confirmed_by    VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_field_mappings_job ON migration_field_mappings(job_id);
CREATE INDEX idx_field_mappings_status ON migration_field_mappings(job_id, status);
