-- The development snapshot contains HRM metadata from July-September 2023, but the
-- corresponding XML reports are intentionally not distributed with the repository.
-- Remove only those original snapshot rows. The id and import-time bounds ensure this
-- repair is safe to reapply without deleting HRM reports created later by developers.

CREATE TEMPORARY TABLE `DevelopmentDanglingHrm` AS
SELECT `id`
FROM `HRMDocument`
WHERE `id` BETWEEN 1 AND 41
  AND `timeReceived` >= '2023-07-25 00:00:00'
  AND `timeReceived` < '2023-09-06 00:00:00'
  AND `reportFile` LIKE 'LabUpload.%';

DELETE hrm_comment
FROM `HRMDocumentComment` hrm_comment
JOIN `DevelopmentDanglingHrm` fixture
  ON fixture.`id` = hrm_comment.`hrmDocumentId`;

DELETE hrm_subclass
FROM `HRMDocumentSubClass` hrm_subclass
JOIN `DevelopmentDanglingHrm` fixture
  ON fixture.`id` = hrm_subclass.`hrmDocumentId`;

DELETE hrm_demographic
FROM `HRMDocumentToDemographic` hrm_demographic
JOIN `DevelopmentDanglingHrm` fixture
  ON fixture.`id` = CAST(hrm_demographic.`hrmDocumentId` AS UNSIGNED);

DELETE hrm_provider
FROM `HRMDocumentToProvider` hrm_provider
JOIN `DevelopmentDanglingHrm` fixture
  ON fixture.`id` = CAST(hrm_provider.`hrmDocumentId` AS UNSIGNED);

UPDATE `HRMDocument` hrm_document
JOIN `DevelopmentDanglingHrm` fixture
  ON fixture.`id` = hrm_document.`parentReport`
SET hrm_document.`parentReport` = NULL
WHERE hrm_document.`id` NOT IN (SELECT `id` FROM `DevelopmentDanglingHrm`);

DELETE hrm_document
FROM `HRMDocument` hrm_document
JOIN `DevelopmentDanglingHrm` fixture
  ON fixture.`id` = hrm_document.`id`;

DROP TEMPORARY TABLE `DevelopmentDanglingHrm`;
