-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Keep accepted clinical resume intent independently discoverable across worker/process failure.
-- Do not backfill from historical RESUME_REQUESTED events: older review-only retries used that
-- same event name. Their existing state/report projection remains the compatible discovery path.
ALTER TABLE cds_import_run
    ADD COLUMN resume_requested BOOLEAN NOT NULL DEFAULT FALSE,
    ADD INDEX idx_cds_run_resume_requested (resume_requested, id),
    ADD INDEX idx_cds_run_state_id (state, id),
    ADD INDEX idx_cds_run_report_state_id (report_state, state, id);

-- Each authoritative control lookup fixes run_id and one of three literal event_type values.
-- MAX(id) and newer-cancel/applied checks can then use a bounded covering index range.
ALTER TABLE cds_import_event
    ADD INDEX idx_cds_event_run_type_id (run_id, event_type, id);
