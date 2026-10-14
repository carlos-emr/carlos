-- Preserve independently numbered lab sources in consultation/eForm attachments (#4024).
-- V1.0.36 is reserved by the concurrent Messenger-coordination change (#3986).
ALTER TABLE `consultdocs` ADD COLUMN IF NOT EXISTS `lab_type` varchar(10) DEFAULT NULL;
ALTER TABLE `EFormDocs` ADD COLUMN IF NOT EXISTS `lab_type` varchar(10) DEFAULT NULL;

-- Infer only a unique source routed to the attachment owner's patient. Ambiguous or
-- no-longer-routed legacy rows remain NULL and require explicit source confirmation;
-- never guess HL7 or import another patient's colliding record. Re-running does not
-- overwrite a source chosen after migration or resurrect a deleted attachment.
UPDATE `consultdocs` cd
JOIN `consultationRequests` cr ON cr.`requestId` = cd.`requestId`
JOIN (
    SELECT `demographic_no`, `lab_no`, MIN(UPPER(`lab_type`)) AS `source`
    FROM `patientLabRouting`
    WHERE UPPER(`lab_type`) IN ('HL7', 'MDS', 'CML', 'BCP')
    GROUP BY `demographic_no`, `lab_no`
    HAVING COUNT(DISTINCT UPPER(`lab_type`)) = 1
) routed ON routed.`demographic_no` = cr.`demographicNo` AND routed.`lab_no` = cd.`document_no`
SET cd.`lab_type` = routed.`source`
WHERE cd.`doctype` = 'L' AND cd.`lab_type` IS NULL;

UPDATE `EFormDocs` ed
JOIN `eform_data` ef ON ef.`fdid` = ed.`fdid`
JOIN (
    SELECT `demographic_no`, `lab_no`, MIN(UPPER(`lab_type`)) AS `source`
    FROM `patientLabRouting`
    WHERE UPPER(`lab_type`) IN ('HL7', 'MDS', 'CML', 'BCP')
    GROUP BY `demographic_no`, `lab_no`
    HAVING COUNT(DISTINCT UPPER(`lab_type`)) = 1
) routed ON routed.`demographic_no` = ef.`demographic_no` AND routed.`lab_no` = ed.`document_no`
SET ed.`lab_type` = routed.`source`
WHERE ed.`doctype` = 'L' AND ed.`lab_type` IS NULL;

ALTER TABLE `consultResponseDoc` ADD COLUMN IF NOT EXISTS `lab_type` varchar(10) DEFAULT NULL;
UPDATE `consultResponseDoc` cd
JOIN `consultationResponse` cr ON cr.`responseId` = cd.`responseId`
JOIN (
    SELECT `demographic_no`, `lab_no`, MIN(UPPER(`lab_type`)) AS `source`
    FROM `patientLabRouting`
    WHERE UPPER(`lab_type`) IN ('HL7', 'MDS', 'CML', 'BCP')
    GROUP BY `demographic_no`, `lab_no`
    HAVING COUNT(DISTINCT UPPER(`lab_type`)) = 1
) routed ON routed.`demographic_no` = cr.`demographicNo` AND routed.`lab_no` = cd.`documentNo`
SET cd.`lab_type` = routed.`source`
WHERE cd.`docType` = 'L' AND cd.`lab_type` IS NULL;
