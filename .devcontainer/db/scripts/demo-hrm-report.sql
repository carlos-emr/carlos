-- Point one demo HRM report at the shipped fixture file.
--
-- The demo snapshot carries HRMDocument rows for demographic 1 whose reportFile
-- names come from the machine the snapshot was taken on; the XML files never
-- shipped. HRMReportParser cannot parse a missing file, HRMUtil.listHRMDocuments()
-- drops every row it cannot parse, and the Rich Text Letter / consultation attach
-- popups show "No HRM documents available" — the HRM attachment family is
-- untestable on a demo install.
--
-- Retarget the lowest-id report linked to demographic 1 at the fictitious,
-- schema-valid report in .devcontainer/db/db_data/hrm/, which the devcontainer
-- seed (seed_data.sh) and `carlos-ctl demo-data` copy into DOCUMENT_DIR under
-- this exact name. Idempotent: re-running leaves the same row pointing at the
-- same file. Guarded against an empty link table (no row, no update).
UPDATE HRMDocument
SET reportFile = 'demo-hrm-diagnostic-imaging.xml'
WHERE id = (
    SELECT MIN(hrmDocumentId) FROM HRMDocumentToDemographic WHERE demographicNo = 1
);
