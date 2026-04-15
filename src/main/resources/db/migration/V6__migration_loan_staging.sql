-- Staging table for migration rows
CREATE TABLE migration_loan_staging (
    id                  BIGSERIAL,
    job_id              UUID NOT NULL REFERENCES migration_jobs(id) ON DELETE CASCADE,
    row_index           INTEGER NOT NULL,
    source_data         JSONB NOT NULL,
    mapped_data         JSONB,
    transformed_data    JSONB,
    lixi_data           JSONB,
    validation_result   JSONB,
    classification      VARCHAR(10) CHECK (classification IN ('CLEAN','WARNING','FAILED')),
    quality_score       DECIMAL(5,4),
    lifecycle_state     VARCHAR(30),
    promoted            BOOLEAN NOT NULL DEFAULT false,
    promoted_id         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE INDEX idx_staging_job ON migration_loan_staging(job_id);
CREATE INDEX idx_staging_classification ON migration_loan_staging(job_id, classification);
CREATE INDEX idx_staging_quality ON migration_loan_staging(job_id, quality_score);
CREATE INDEX idx_staging_promoted ON migration_loan_staging(job_id, promoted);
