-- Migration job tracking
CREATE TABLE migration_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    source_filename     VARCHAR(500) NOT NULL,
    source_row_count    BIGINT NOT NULL,
    source_column_count INTEGER NOT NULL,
    lender_code         VARCHAR(50),
    status              VARCHAR(20) NOT NULL DEFAULT 'CREATED'
                        CHECK (status IN ('CREATED','INGESTED','MAPPED','TRANSFORMING','VALIDATING','CLASSIFIED','PROMOTED','RECONCILED')),
    quality_report      JSONB,
    reconciliation      JSONB,
    created_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_migration_jobs_status ON migration_jobs(status);
CREATE INDEX idx_migration_jobs_created ON migration_jobs(created_at);
