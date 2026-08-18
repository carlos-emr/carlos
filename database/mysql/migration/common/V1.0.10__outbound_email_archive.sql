-- Durable outbound email archive metadata.
-- Exact artifacts are stored in the patient eDoc document store; these tables
-- link the artifact to emailLog/demographic and retain integrity/deletion audit data.

CREATE TABLE IF NOT EXISTS `outboundEmailArchive` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `emailLogId` BIGINT NOT NULL,
    -- No foreign keys on demographicNo/providerNo, unlike emailLogId, configId and
    -- documentNo below. The reason is engine/legacy scope, not decoupling: V1.0.9
    -- guarantees InnoDB only for document, emailConfig and emailLog, and constraining
    -- these two would widen that conversion to demographic and provider.
    --
    -- Do NOT read this as "the archive survives a missing demographic". It does not.
    -- OutboundEmailArchive maps both as @ManyToOne with nullable = false, so a dangling
    -- value would throw EntityNotFoundException the moment any non-identifier property
    -- is dereferenced. That is currently safe only because CARLOS has no hard-delete
    -- path for demographics or providers -- merges go through demographic_merged and
    -- keep both rows -- and because the service reads these two associations for their
    -- identifier alone. A future reader that displays patient or provider names will be
    -- the first caller to depend on the row actually being there.
    `demographicNo` INT NOT NULL,
    `providerNo` VARCHAR(6),
    `configId` BIGINT,
    `documentNo` INT NOT NULL,
    `artifactType` VARCHAR(50) NOT NULL,
    `transportType` VARCHAR(50) NOT NULL,
    `providerName` VARCHAR(100),
    `providerMessageId` VARCHAR(255),
    `providerResponse` VARCHAR(1000),
    `contentType` VARCHAR(100) NOT NULL,
    `fileName` VARCHAR(255) NOT NULL,
    `originalFileName` VARCHAR(255),
    `sha256Hash` CHAR(64) NOT NULL,
    `byteSize` BIGINT NOT NULL,
    `storageType` VARCHAR(25) NOT NULL DEFAULT 'EDOC',
    `retentionPolicy` VARCHAR(50) NOT NULL DEFAULT 'PERMANENT',
    -- Legal hold is ON for every archive from creation. Releasing it is an
    -- admin-only action recorded in outboundEmailArchiveLegalHoldEvent, and it is
    -- the only route to controlled deletion. OutboundEmailArchive initialises the
    -- field to true independently; this default backstops rows inserted outside
    -- the service.
    `legalHold` BOOLEAN NOT NULL DEFAULT TRUE,
    `deleted` BOOLEAN NOT NULL DEFAULT FALSE,
    `sendStatus` VARCHAR(25) NOT NULL DEFAULT 'ARCHIVED',
    `archivedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `sendAttemptedAt` DATETIME,
    `sentAt` DATETIME,
    `deletedAt` DATETIME,
    `deletedByProviderNo` VARCHAR(6),
    `deleteReason` VARCHAR(1000),
    `lastUpdateUser` VARCHAR(6) NOT NULL,
    `lastUpdateDate` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_outboundEmailArchive_emailLogId` (`emailLogId`),
    INDEX `idx_outboundEmailArchive_demographicNo` (`demographicNo`),
    INDEX `idx_outboundEmailArchive_documentNo` (`documentNo`),
    INDEX `idx_outboundEmailArchive_sha256Hash` (`sha256Hash`),
    CONSTRAINT `fk_outboundEmailArchive_emailLog`
        FOREIGN KEY (`emailLogId`) REFERENCES `emailLog` (`id`),
    CONSTRAINT `fk_outboundEmailArchive_emailConfig`
        FOREIGN KEY (`configId`) REFERENCES `emailConfig` (`id`),
    CONSTRAINT `fk_outboundEmailArchive_document`
        FOREIGN KEY (`documentNo`) REFERENCES `document` (`document_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `outboundEmailArchiveAttachment` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `archiveId` INT NOT NULL,
    `documentNo` INT,
    `fileName` VARCHAR(255) NOT NULL,
    `contentType` VARCHAR(100),
    `sha256Hash` CHAR(64) NOT NULL,
    `byteSize` BIGINT NOT NULL,
    `sourceDocumentType` VARCHAR(50),
    `sourceDocumentId` INT,
    `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastUpdateUser` VARCHAR(6) NOT NULL,
    `lastUpdateDate` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_outboundEmailArchiveAttachment_archiveId` (`archiveId`),
    INDEX `idx_outboundEmailArchiveAttachment_documentNo` (`documentNo`),
    CONSTRAINT `fk_outboundEmailArchiveAttachment_archive`
        FOREIGN KEY (`archiveId`) REFERENCES `outboundEmailArchive` (`id`),
    CONSTRAINT `fk_outboundEmailArchiveAttachment_document`
        FOREIGN KEY (`documentNo`) REFERENCES `document` (`document_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `outboundEmailArchiveDeletion` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `archiveId` INT NOT NULL,
    `emailLogId` BIGINT NOT NULL,
    `demographicNo` INT NOT NULL,
    `documentNo` INT,
    `fileName` VARCHAR(255) NOT NULL,
    `contentType` VARCHAR(100),
    `sha256Hash` CHAR(64) NOT NULL,
    `byteSize` BIGINT NOT NULL,
    `deletedByProviderNo` VARCHAR(6) NOT NULL,
    `deletedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleteReason` VARCHAR(1000) NOT NULL,
    `lastUpdateUser` VARCHAR(6) NOT NULL,
    `lastUpdateDate` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- One tombstone per archive.
    UNIQUE INDEX `idx_outboundEmailArchiveDeletion_archiveId` (`archiveId`),
    INDEX `idx_outboundEmailArchiveDeletion_emailLogId` (`emailLogId`),
    INDEX `idx_outboundEmailArchiveDeletion_demographicNo` (`demographicNo`),
    -- archiveId IS constrained, and costs nothing to constrain: archives are never
    -- hard-deleted, so RESTRICT can never block anything this design permits.
    -- OutboundEmailArchive.preRemove() refuses removal outright and retirement is a
    -- soft `deleted` flag plus this tombstone. The constraint is what makes an
    -- orphaned tombstone impossible, which matters because OutboundEmailArchiveDeletion
    -- maps archive as a navigable @ManyToOne: unconstrained, a stale archiveId would
    -- surface as EntityNotFoundException on first dereference instead of being
    -- rejected at insert.
    --
    -- If a retention or cold-storage job ever needs to remove archive rows, it must
    -- drop this constraint deliberately in its own migration. That is the point --
    -- discarding the evidence of a deletion should be an explicit schema decision,
    -- not a silent side effect of a purge.
    CONSTRAINT `fk_outboundEmailArchiveDeletion_archive`
        FOREIGN KEY (`archiveId`) REFERENCES `outboundEmailArchive` (`id`),
    CONSTRAINT `fk_outboundEmailArchiveDeletion_emailLog`
        FOREIGN KEY (`emailLogId`) REFERENCES `emailLog` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Append-only record of every legal hold transition on an archive.
--
-- Because legal hold is on by default, releasing it is the act that makes an
-- archive deletable at all -- and the provider who authorises that release is
-- frequently not the provider who later performs the deletion. The deletion
-- tombstone names only the latter, so it cannot express that split on its own.
CREATE TABLE IF NOT EXISTS `outboundEmailArchiveLegalHoldEvent` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `archiveId` INT NOT NULL,
    -- PLACED | RELEASED
    `action` VARCHAR(25) NOT NULL,
    `providerNo` VARCHAR(6) NOT NULL,
    `reason` VARCHAR(1000) NOT NULL,
    `eventAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastUpdateUser` VARCHAR(6) NOT NULL,
    `lastUpdateDate` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Composite index matches the DAO's ORDER BY eventAt DESC, id DESC.
    INDEX `idx_outboundEmailArchiveLegalHoldEvent_archiveId` (`archiveId`, `eventAt`),
    INDEX `idx_outboundEmailArchiveLegalHoldEvent_providerNo` (`providerNo`),
    -- Constrained for the same reason as outboundEmailArchiveDeletion above:
    -- archives are never hard-deleted, so RESTRICT cannot block anything this design
    -- permits, and the key is what makes an orphaned event impossible. Both audit
    -- tables now follow one rule rather than differing for no stated reason.
    CONSTRAINT `fk_outboundEmailArchiveLegalHoldEvent_archive`
        FOREIGN KEY (`archiveId`) REFERENCES `outboundEmailArchive` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Seed the security object that guards archive deletion and legal hold release.
--
-- REQUIRED, not cosmetic. `_admin.edocdelete` is created only by the FROZEN
-- legacy script database/mysql/updates/update-2008-10-20.sql and appears nowhere
-- else in the Flyway migration set. Without this insert a fresh CARLOS database
-- has no such row in secObjectName, hasPrivilege() returns false for every user,
-- and no one can ever release a legal hold or retire an archive.
--
-- Idempotent: safe on fresh installs and on legacy databases that already ran
-- the 2008 patch.
INSERT INTO `secObjectName` (`objectName`, `description`, `orgapplicable`)
SELECT '_admin.edocdelete', 'Right to delete eDocs', 0
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM `secObjectName` WHERE `objectName` = '_admin.edocdelete'
 );

-- Grant it to the admin role, and only to the admin role.
--
-- CARLOS does not infer dotted-object privileges: `admin` holds `_admin` = 'x'
-- AND a separate explicit row for each of the 31 `_admin.*` objects it can use.
-- Nothing walks the hierarchy, so seeding the object name alone would leave
-- _admin.edocdelete granted to no one and the feature unreachable. This row is
-- what the other 31 grants look like -- privilege 'x' ("All rights", which
-- SecurityInfoManagerImpl accepts in place of the 'w' this service asks for).
--
-- Deliberately NOT granted to `doctor`, which holds 14 other `_admin.*` objects.
-- Retiring an evidentiary communication archive is an administrative act; the
-- whole point of requiring _admin.edocdelete rather than _edoc is that the
-- deletion gate must be stricter than the archive gate.
INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
SELECT 'admin', '_admin.edocdelete', 'x', 0, NULL
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM `secObjPrivilege`
    WHERE `roleUserGroup` = 'admin' AND `objectName` = '_admin.edocdelete'
 );
