-- Soft-delete stale active consultation eForm/document attachment rows.
--
-- consultdocs.deleted IS NULL is active in this schema; detached rows use
-- deleted='Y'. This cleanup only touches active E/D rows where target
-- existence and patient ownership can be validated safely:
--   E: missing eform_data.fdid, or non-patient-independent eForm attached to a
--      consultation for a different demographic.
--   D: missing document row, deleted document row, or no demographic
--      ctl_document link for the consultation patient whose status matches a
--      non-deleted document.
--
-- Labs, HRM, and form attachments are intentionally not changed here.
--
-- Manual-only cleanup script. Run the dry-run/reporting SELECT first, review
-- the row count, then opt in to the APPLY section in the same maintenance
-- window if the count matches expectations:
--
--   SET @APPLY_STALE_CONSULT_ATTACHMENT_CLEANUP := 1;
--   SOURCE database/mysql/updates/update-2026-07-08-clean-stale-consult-attachments.sql;
--
-- Without that explicit session variable, the UPDATE below is gated off and
-- this file reports only.

SET @APPLY_STALE_CONSULT_ATTACHMENT_CLEANUP := COALESCE(@APPLY_STALE_CONSULT_ATTACHMENT_CLEANUP, 0);

-- DRY-RUN/REPORTING SECTION
SELECT COUNT(*) AS stale_active_consult_attachments_to_soft_delete
FROM consultdocs cd
JOIN consultationRequests cr ON cr.requestId = cd.requestId
LEFT JOIN eform_data e ON cd.doctype = 'E' AND e.fdid = cd.document_no
LEFT JOIN document d ON cd.doctype = 'D' AND d.document_no = cd.document_no
LEFT JOIN ctl_document ctl ON cd.doctype = 'D'
  AND ctl.document_no = cd.document_no
  AND ctl.module = 'demographic'
  AND ctl.module_id = cr.demographicNo
  AND d.status = ctl.status
  AND d.status <> 'D'
WHERE cd.deleted IS NULL
  AND (
    (
      cd.doctype = 'E'
      AND (
        e.fdid IS NULL
        OR (
          (e.patient_independent IS NULL OR e.patient_independent = FALSE)
          AND (e.demographic_no IS NULL OR e.demographic_no <> cr.demographicNo)
        )
      )
    )
    OR (
      cd.doctype = 'D'
      AND (
        d.document_no IS NULL
        OR d.status = 'D'
        OR ctl.document_no IS NULL
      )
    )
  );

-- APPLY SECTION
-- Run only after reviewing the dry-run/reporting count above.
UPDATE consultdocs cd
JOIN consultationRequests cr ON cr.requestId = cd.requestId
LEFT JOIN eform_data e ON cd.doctype = 'E' AND e.fdid = cd.document_no
LEFT JOIN document d ON cd.doctype = 'D' AND d.document_no = cd.document_no
LEFT JOIN ctl_document ctl ON cd.doctype = 'D'
  AND ctl.document_no = cd.document_no
  AND ctl.module = 'demographic'
  AND ctl.module_id = cr.demographicNo
  AND d.status = ctl.status
  AND d.status <> 'D'
SET cd.deleted = 'Y'
WHERE cd.deleted IS NULL
  AND @APPLY_STALE_CONSULT_ATTACHMENT_CLEANUP = 1
  AND (
    (
      cd.doctype = 'E'
      AND (
        e.fdid IS NULL
        OR (
          (e.patient_independent IS NULL OR e.patient_independent = FALSE)
          AND (e.demographic_no IS NULL OR e.demographic_no <> cr.demographicNo)
        )
      )
    )
    OR (
      cd.doctype = 'D'
      AND (
        d.document_no IS NULL
        OR d.status = 'D'
        OR ctl.document_no IS NULL
      )
    )
  );
