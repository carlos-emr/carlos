-- Durable outbound email archive metadata.
-- Exact artifacts are stored in the patient eDoc document store; these tables
-- link the artifact to emailLog/demographic and retain integrity/deletion audit data.

CREATE TABLE IF NOT EXISTS `outboundEmailArchive` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `emailLogId` BIGINT NOT NULL,
    -- No foreign keys on demographicNo/providerNo, unlike emailLogId, configId and
    -- documentNo below. The reason is engine scope on the LEGACY UPGRADE PATH, not
    -- decoupling. On a Flyway-built database this constraint could be added today:
    -- V1__baseline_schema.sql creates demographic and provider as InnoDB already (there
    -- is no MyISAM table left in that baseline). It is on an install upgraded in place
    -- from an older OSCAR/OpenO schema that the engine is unknown, and V1.0.9 converts
    -- only the three tables this feature strictly needs. Omitting the key keeps one
    -- schema across both paths rather than diverging by install history.
    --
    -- Do NOT read this as "the archive survives a missing demographic". It does not.
    -- OutboundEmailArchive maps demographic as @ManyToOne with nullable = false (provider
    -- is optional, matching the nullable column below), so a dangling non-null value
    -- would throw EntityNotFoundException the moment any non-identifier property is
    -- dereferenced. That is currently safe because the service reads these two
    -- associations for their identifier alone, and because nothing reachable hard-deletes
    -- a demographic -- DemographicManagerImpl.deleteDemographic only sets patient_status,
    -- and merges go through demographic_merged and keep both rows. Note the weaker
    -- guarantee on provider: SecProviderDaoImpl.delete and the inherited
    -- ProviderDataDao.remove ARE hard deletes, with no production caller today. If one
    -- appears, providerNo here becomes dangling with no key to stop it.
    --
    -- A future reader that displays patient or provider names is the first caller to
    -- depend on the row actually being there.
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
    -- Composite, not bare foreign-key columns: both DAO lookups filter on the key and then
    -- ORDER BY archivedAt DESC, so a single-column index leaves every patient and email-log
    -- listing to filesort. Cheap to get right while this migration is unmerged; correcting it
    -- afterwards costs a whole ALTER TABLE migration on live archive data.
    INDEX `idx_outboundEmailArchive_emailLogId` (`emailLogId`, `archivedAt`),
    INDEX `idx_outboundEmailArchive_demographicNo` (`demographicNo`, `archivedAt`),
    INDEX `idx_outboundEmailArchive_documentNo` (`documentNo`),
    -- Not read by any current query. Retained because verifying a stored artifact against its
    -- tombstone hash is the point of the table, and that lookup is by hash.
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
    -- Covers the DAO's archiveId filter and its eventAt ordering. The `id` tiebreak in
    -- ORDER BY eventAt DESC, id DESC is resolved in the sort, not by this index.
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
-- The row below is for the admin UI, which lists objects out of secObjectName.
-- hasPrivilege() never reads this table: OscarRoleObjectPrivilege goes straight to
-- SecObjPrivilegeDao.findByObjectNames, so it is the GRANT further down that opens the
-- gate, not this name. Both are seeded because the object exists in neither table on a
-- Flyway-built database.
--
-- `_admin.edocdelete` is created only by the FROZEN legacy script
-- database/mysql/updates/update-2008-10-20.sql, which inserts the name and grants it to
-- no role at all. So the gate has been permanently shut everywhere: on fresh installs
-- (no row in either table) AND on legacy databases that DID run the 2008 patch (name
-- present, still no grant). That is what the second INSERT fixes, and why it is required
-- rather than belt-and-braces.
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
-- CARLOS does not infer dotted-object privileges. `admin` holds `_admin` = 'x' AND a
-- separate explicit row per dotted object; OscarRoleObjectPrivilege.getVecObjectName
-- only comma-splits the role list and never walks the '.' hierarchy, so `_admin` = 'x'
-- confers nothing on `_admin.edocdelete`. Seeding the object name alone would leave it
-- granted to no one and the feature unreachable.
--
-- Privilege 'x' is "All rights": SecurityInfoManagerImpl short-circuits to true on an
-- 'x' grant before it looks at the 'w' this service requests.
--
-- Deliberately NOT granted to `doctor`. Note that doctor's existing `_admin.*` rows are
-- almost all privilege 'o' -- SecurityInfoManager.NORIGHTS, i.e. explicit no rights --
-- so this is withholding a right doctor was never comparably given. Retiring an
-- evidentiary communication archive is an administrative act; the whole point of
-- requiring _admin.edocdelete rather than _edoc is that the deletion gate must be
-- stricter than the archive gate.
INSERT INTO `secObjPrivilege` (`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`)
SELECT 'admin', '_admin.edocdelete', 'x', 0, NULL
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM `secObjPrivilege`
    WHERE `roleUserGroup` = 'admin' AND `objectName` = '_admin.edocdelete'
 );
