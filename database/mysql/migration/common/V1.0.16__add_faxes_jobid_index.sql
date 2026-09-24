-- The inbound importer deduplicates by exact provider job id (faxes.jobId) on every poll
-- cycle for every unread fax; without an index the lookup full-scans a table whose rows
-- carry base64 PDF payloads in the legacy document column.
ALTER TABLE faxes
    ADD INDEX IF NOT EXISTS idx_faxes_jobid (jobId);
