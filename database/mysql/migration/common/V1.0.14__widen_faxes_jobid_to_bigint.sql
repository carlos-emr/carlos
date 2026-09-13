-- SRFax Queue_Fax returns FaxDetailsID values that are not bounded to the INT
-- range, and FaxJob.jobId is already a Java Long. Widen the column so a large
-- provider job id cannot fail persistence and strand an outbound fax.
ALTER TABLE faxes
    MODIFY COLUMN jobId bigint DEFAULT NULL;
