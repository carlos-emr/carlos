-- PROPOSAL ONLY. Outside protected database/: requires independent concrete review and Ben's direct
-- authorization before migration placement/rollout. Number82 reserved after proposed M3 number81;
-- recheck both release/develop at publication. No table is created by runtime application code.
-- Signed originals and complete immutable sender snapshots are encrypted with a dedicated AES-256-GCM
-- key domain. A missing keyring fails closed; it is never replaced automatically.
DELIMITER //
CREATE PROCEDURE migrate_mycarlos_sender_v82_preflight()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME IN ('mycarlos_delivery_original','mycarlos_delivery_outbox',
                        'mycarlos_delivery_checkpoint','mycarlos_delivery_event_page','mycarlos_delivery_event')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='myCarlos sender tables already exist; inspect partial DDL before Flyway reconciliation';
  END IF;
END//
CALL migrate_mycarlos_sender_v82_preflight()//
DROP PROCEDURE migrate_mycarlos_sender_v82_preflight//
DELIMITER ;

CREATE TABLE mycarlos_delivery_original (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clinic_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  delivery_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  original_sha256 BINARY(32) NOT NULL,
  encrypted_original LONGBLOB NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mycarlos_original_identity (clinic_id, delivery_id),
  CONSTRAINT chk_mycarlos_original_time CHECK (created_at >= 0),
  CONSTRAINT chk_mycarlos_original_bytes CHECK (OCTET_LENGTH(encrypted_original) BETWEEN 41 AND 20971560)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE mycarlos_delivery_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  original_id BIGINT NOT NULL,
  clinic_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  delivery_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  device_key_id CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  demographic_no INT NOT NULL,
  accepted_device_id BIGINT NOT NULL,
  acceptance_version BIGINT NOT NULL,
  config_sha256 BINARY(32) NOT NULL,
  encrypted_snapshot LONGBLOB NOT NULL,
  snapshot_sha256 BINARY(32) NOT NULL,
  state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'staged',
  lease_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  lease_until BIGINT NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  retry_after BIGINT NOT NULL DEFAULT 0,
  failure_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  encrypted_receipt BLOB NULL,
  receipt_sha256 BINARY(32) NULL,
  receipt_jti CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mycarlos_outbox_identity (clinic_id, delivery_id, device_key_id),
  KEY ix_mycarlos_outbox_claim (clinic_id, state, retry_after, lease_until, id),
  KEY ix_mycarlos_outbox_device (accepted_device_id, state),
  CONSTRAINT fk_mycarlos_outbox_original FOREIGN KEY (original_id) REFERENCES mycarlos_delivery_original(id),
  CONSTRAINT chk_mycarlos_outbox_state CHECK (state IN ('staged','waiting','picked_up','expired','removed','fenced','error')),
  CONSTRAINT chk_mycarlos_outbox_versions CHECK (acceptance_version >= 0 AND row_version >= 0 AND retry_after >= 0),
  CONSTRAINT chk_mycarlos_outbox_patient CHECK (demographic_no > 0 AND accepted_device_id > 0),
  CONSTRAINT chk_mycarlos_outbox_lease CHECK ((lease_id IS NULL AND lease_until IS NULL) OR (lease_id IS NOT NULL AND lease_until IS NOT NULL AND lease_until >= 0)),
  CONSTRAINT chk_mycarlos_outbox_snapshot CHECK (OCTET_LENGTH(encrypted_snapshot) BETWEEN 41 AND 33554472),
  CONSTRAINT chk_mycarlos_outbox_receipt CHECK (
    (state='picked_up' AND encrypted_receipt IS NOT NULL AND receipt_sha256 IS NOT NULL AND receipt_jti IS NOT NULL)
    OR (state<>'picked_up' AND encrypted_receipt IS NULL AND receipt_sha256 IS NULL AND receipt_jti IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE mycarlos_delivery_checkpoint (
  clinic_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  committed_through BIGINT NOT NULL DEFAULT 0,
  acknowledged_through BIGINT NOT NULL DEFAULT 0,
  writer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  writer_until BIGINT NULL,
  PRIMARY KEY (clinic_id),
  CONSTRAINT chk_mycarlos_delivery_checkpoint CHECK (committed_through >= 0 AND acknowledged_through >= 0 AND acknowledged_through <= committed_through),
  CONSTRAINT chk_mycarlos_delivery_writer CHECK ((writer_id IS NULL AND writer_until IS NULL) OR (writer_id IS NOT NULL AND writer_until IS NOT NULL AND writer_until >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE mycarlos_delivery_event_page (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clinic_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  after_sequence BIGINT NOT NULL,
  page_sha256 BINARY(32) NOT NULL,
  encrypted_page MEDIUMBLOB NOT NULL,
  collected_at BIGINT NOT NULL,
  failure_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mycarlos_event_page (clinic_id, page_sha256),
  KEY ix_mycarlos_event_page_cursor (clinic_id, after_sequence),
  CONSTRAINT chk_mycarlos_event_page_cursor CHECK (after_sequence >= 0 AND collected_at >= 0),
  CONSTRAINT chk_mycarlos_event_page_size CHECK (OCTET_LENGTH(encrypted_page) BETWEEN 41 AND 1048616)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE mycarlos_delivery_event (
  clinic_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  sequence BIGINT NOT NULL,
  page_id BIGINT NOT NULL,
  page_ordinal INT NOT NULL,
  kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  verified BOOLEAN NOT NULL,
  PRIMARY KEY (clinic_id, sequence),
  CONSTRAINT fk_mycarlos_event_page FOREIGN KEY (page_id) REFERENCES mycarlos_delivery_event_page(id),
  CONSTRAINT chk_mycarlos_event_sequence CHECK (sequence > 0 AND page_ordinal BETWEEN 0 AND 49),
  CONSTRAINT chk_mycarlos_event_kind CHECK (kind IN ('picked_up','expired','removed','device_removed','anchor')),
  CONSTRAINT chk_mycarlos_event_verified CHECK (verified IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Partial DDL is deliberately refused on retry rather than adopting unknown tables.
-- See sender82-partial-ddl-recovery.txt: inspect the exact installed prefix and row provenance,
-- complete only approved missing DDL, and reconcile history using supported Flyway tooling.

INSERT INTO secObjectName (objectName,description,orgapplicable)
SELECT '_mycarlos_delivery','Send documents to approved myCarlos devices',0
WHERE NOT EXISTS (SELECT 1 FROM secObjectName WHERE objectName='_mycarlos_delivery');
INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
SELECT 'admin','_mycarlos_delivery','w',0,NULL
WHERE NOT EXISTS (SELECT 1 FROM secObjPrivilege WHERE roleUserGroup='admin' AND objectName='_mycarlos_delivery');
