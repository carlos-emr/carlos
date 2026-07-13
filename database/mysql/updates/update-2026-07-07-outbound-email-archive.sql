-- Durable outbound email archive metadata.
-- Exact artifacts are stored in the patient eDoc document store; these tables
-- link the artifact to emailLog/demographic and retain integrity/deletion audit data.

CREATE TABLE IF NOT EXISTS `outboundEmailArchive` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `emailLogId` BIGINT NOT NULL,
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
    `legalHold` BOOLEAN NOT NULL DEFAULT FALSE,
    `deleted` BOOLEAN NOT NULL DEFAULT FALSE,
    `sendStatus` VARCHAR(25) NOT NULL DEFAULT 'ARCHIVED',
    `archivedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `sendAttemptedAt` DATETIME,
    `sentAt` DATETIME,
    `deletedAt` DATETIME,
    `deletedByProviderNo` VARCHAR(6),
    `deleteReason` VARCHAR(1000),
    `lastUpdateUser` VARCHAR(6),
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
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `outboundEmailArchiveAttachment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `archiveId` BIGINT NOT NULL,
    `documentNo` INT,
    `fileName` VARCHAR(255) NOT NULL,
    `contentType` VARCHAR(100),
    `sha256Hash` CHAR(64) NOT NULL,
    `byteSize` BIGINT NOT NULL,
    `sourceDocumentType` VARCHAR(50),
    `sourceDocumentId` INT,
    `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastUpdateUser` VARCHAR(6),
    `lastUpdateDate` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_outboundEmailArchiveAttachment_archiveId` (`archiveId`),
    INDEX `idx_outboundEmailArchiveAttachment_documentNo` (`documentNo`),
    CONSTRAINT `fk_outboundEmailArchiveAttachment_archive`
        FOREIGN KEY (`archiveId`) REFERENCES `outboundEmailArchive` (`id`),
    CONSTRAINT `fk_outboundEmailArchiveAttachment_document`
        FOREIGN KEY (`documentNo`) REFERENCES `document` (`document_no`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `outboundEmailArchiveDeletion` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `archiveId` BIGINT NOT NULL,
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
    `lastUpdateUser` VARCHAR(6),
    `lastUpdateDate` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `idx_outboundEmailArchiveDeletion_archiveId` (`archiveId`),
    INDEX `idx_outboundEmailArchiveDeletion_emailLogId` (`emailLogId`),
    INDEX `idx_outboundEmailArchiveDeletion_demographicNo` (`demographicNo`),
    CONSTRAINT `fk_outboundEmailArchiveDeletion_emailLog`
        FOREIGN KEY (`emailLogId`) REFERENCES `emailLog` (`id`)
) ENGINE=InnoDB;
