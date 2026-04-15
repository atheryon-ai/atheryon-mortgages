-- Reconciliation report snapshots
CREATE TABLE reconciliation_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES migration_jobs(id),
    report_data     JSONB NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_job ON reconciliation_reports(job_id);
