-- PROPOSED common/V1.0.81__mycarlos_accepted_device_approval.sql
-- OUTSIDE protected database/: direct human approval and final numbering check required.
-- Root/A1 must review final live migration inventory and exact M3 wire before any source patch.
-- No existing linked device is promoted. Unexpected existing tables require inspection, not silent reuse.
-- No ON DELETE CASCADE: patient/key/proof deletion may not silently remove the acceptance history.

-- Flyway's nontransactional-DDL retry must refuse a partially applied schema rather than
-- silently reuse an unknown shape. See the companion exact recovery runbook before repair.
DELIMITER //
CREATE PROCEDURE migrate_mycarlos_approval_v81_preflight()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME IN ('mycarlos_patient_binding','mycarlos_device_approval_challenge',
                        'mycarlos_accepted_device','mycarlos_device_approval_outbox')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='myCarlos approval tables already exist; inspect partial DDL before Flyway repair';
  END IF;
END//
CALL migrate_mycarlos_approval_v81_preflight()//
DROP PROCEDURE migrate_mycarlos_approval_v81_preflight//
DELIMITER ;

CREATE TABLE mycarlos_patient_binding (
  id BIGINT NOT NULL AUTO_INCREMENT,
  clinic_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  demographic_no INT NOT NULL,
  recipient_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  public_portal_base_url TEXT COLLATE utf8mb4_bin NOT NULL,
  internal_portal_origin TEXT COLLATE utf8mb4_bin NOT NULL,
  config_binding_sha256 BINARY(32) NOT NULL,
  root_device_key_id CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mycarlos_patient_binding (clinic_id, demographic_no),
  UNIQUE KEY uq_mycarlos_recipient_binding (clinic_id, recipient_id),
  CONSTRAINT fk_mycarlos_binding_demographic FOREIGN KEY (demographic_no)
    REFERENCES demographic(demographic_no),
  CONSTRAINT chk_mycarlos_binding_bounds CHECK (
    CHAR_LENGTH(clinic_id) BETWEEN 1 AND 64 AND demographic_no>0
    AND CHAR_LENGTH(recipient_id) BETWEEN 1 AND 64
    AND OCTET_LENGTH(public_portal_base_url) BETWEEN 1 AND 1024
    AND OCTET_LENGTH(internal_portal_origin) BETWEEN 1 AND 2048 AND version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
-- Created only alongside successful first staff approval; root_device_key_id is NEVER cleared.
-- No circular root FK: the same transaction creates this anchor then its accepted root.

CREATE TABLE mycarlos_device_approval_challenge (
  challenge_id BINARY(32) NOT NULL,
  request_id BINARY(16) NOT NULL,
  binding_id BIGINT DEFAULT NULL,
  clinic_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  demographic_no INT NOT NULL,
  recipient_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  public_portal_base_url TEXT COLLATE utf8mb4_bin NOT NULL,
  config_binding_sha256 BINARY(32) NOT NULL,
  candidate_device_key_id CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  candidate_x25519_public_key BINARY(32) NOT NULL,
  candidate_ed25519_public_key BINARY(32) NOT NULL,
  candidate_intent VARBINARY(8192) NOT NULL,
  candidate_intent_sha256 BINARY(32) NOT NULL,
  signed_challenge VARBINARY(8192) NOT NULL,
  challenge_sha256 BINARY(32) NOT NULL,
  approval_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  predecessor_accepted_id BIGINT DEFAULT NULL,
  predecessor_device_key_id CHAR(43) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  predecessor_version BIGINT DEFAULT NULL,
  issued_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  consumed_at DATETIME(6) DEFAULT NULL,
  consumed_approval_jti BINARY(16) DEFAULT NULL,
  consumed_approval VARBINARY(8192) DEFAULT NULL,
  consumed_approval_sha256 BINARY(32) DEFAULT NULL,
  cancelled_at DATETIME(6) DEFAULT NULL,
  cancellation_reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  PRIMARY KEY (challenge_id),
  UNIQUE KEY uq_mycarlos_approval_request (clinic_id, request_id),
  UNIQUE KEY uq_mycarlos_predecessor_approval_jti (predecessor_accepted_id, consumed_approval_jti),
  KEY ix_mycarlos_challenge_candidate (clinic_id, demographic_no, candidate_device_key_id, expires_at),
  KEY ix_mycarlos_challenge_predecessor (binding_id, predecessor_accepted_id, consumed_at, cancelled_at),
  KEY ix_mycarlos_challenge_expiry (expires_at),
  CONSTRAINT fk_mycarlos_challenge_demographic FOREIGN KEY (demographic_no)
    REFERENCES demographic(demographic_no),
  CONSTRAINT fk_mycarlos_challenge_binding FOREIGN KEY (binding_id)
    REFERENCES mycarlos_patient_binding(id),
  CONSTRAINT chk_mycarlos_challenge_public_url CHECK (OCTET_LENGTH(public_portal_base_url) BETWEEN 1 AND 1024),
  CONSTRAINT chk_mycarlos_challenge_kind CHECK (approval_kind IN ('staff_first','accepted_device')),
  CONSTRAINT chk_mycarlos_challenge_predecessor CHECK (
    (approval_kind='staff_first' AND predecessor_accepted_id IS NULL
      AND predecessor_device_key_id IS NULL AND predecessor_version IS NULL)
    OR (approval_kind='accepted_device' AND binding_id IS NOT NULL AND predecessor_accepted_id IS NOT NULL
      AND predecessor_device_key_id IS NOT NULL AND predecessor_version IS NOT NULL AND predecessor_version>=0)),
  CONSTRAINT chk_mycarlos_challenge_time CHECK (expires_at>issued_at AND expires_at<=DATE_ADD(issued_at,INTERVAL 600 SECOND)),
  CONSTRAINT chk_mycarlos_challenge_terminal CHECK (consumed_at IS NULL OR cancelled_at IS NULL),
  CONSTRAINT chk_mycarlos_challenge_consumed_binding CHECK (consumed_at IS NULL OR binding_id IS NOT NULL),
  CONSTRAINT chk_mycarlos_challenge_evidence CHECK (
    (approval_kind='staff_first' AND consumed_approval_jti IS NULL AND consumed_approval IS NULL
      AND consumed_approval_sha256 IS NULL)
    OR (approval_kind='accepted_device' AND (
      (consumed_at IS NULL AND consumed_approval_jti IS NULL AND consumed_approval IS NULL
        AND consumed_approval_sha256 IS NULL)
      OR (consumed_at IS NOT NULL AND consumed_approval_jti IS NOT NULL AND consumed_approval IS NOT NULL
        AND OCTET_LENGTH(consumed_approval)>0 AND consumed_approval_sha256 IS NOT NULL)))),
  CONSTRAINT chk_mycarlos_challenge_cancel CHECK ((cancelled_at IS NULL)=(cancellation_reason IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
-- First staff challenge binding_id is provisional NULL; set it once when creating the root binding.
-- Current/local scope+key/version/strict wire validation is REQUIRED in the locked service, not delegated to CHECKs.

CREATE TABLE mycarlos_accepted_device (
  id BIGINT NOT NULL AUTO_INCREMENT,
  binding_id BIGINT NOT NULL,
  device_key_id CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  x25519_public_key BINARY(32) NOT NULL,
  ed25519_public_key BINARY(32) NOT NULL,
  approval_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  approval_challenge_id BINARY(32) NOT NULL,
  approved_by_provider_no VARCHAR(6) DEFAULT NULL,
  approved_by_security_no INT DEFAULT NULL,
  staff_verification_method VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  predecessor_accepted_id BIGINT DEFAULT NULL,
  predecessor_version BIGINT DEFAULT NULL,
  predecessor_approval VARBINARY(8192) DEFAULT NULL,
  clinic_acceptance VARBINARY(8192) NOT NULL,
  clinic_acceptance_sha256 BINARY(32) NOT NULL,
  accepted_at DATETIME(6) NOT NULL,
  removed_at DATETIME(6) DEFAULT NULL,
  removal_id BINARY(16) DEFAULT NULL,
  removal_reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  removed_by_provider_no VARCHAR(6) DEFAULT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mycarlos_accepted_kid (device_key_id),
  UNIQUE KEY uq_mycarlos_removal_id (removal_id),
  UNIQUE KEY uq_mycarlos_consumed_approval (approval_challenge_id),
  UNIQUE KEY uq_mycarlos_accepted_binding_id (binding_id,id),
  KEY ix_mycarlos_accepted_eligible (binding_id,removed_at),
  CONSTRAINT fk_mycarlos_accepted_binding FOREIGN KEY (binding_id) REFERENCES mycarlos_patient_binding(id),
  CONSTRAINT fk_mycarlos_accepted_challenge FOREIGN KEY (approval_challenge_id)
    REFERENCES mycarlos_device_approval_challenge(challenge_id),
  CONSTRAINT fk_mycarlos_accepted_predecessor FOREIGN KEY (binding_id,predecessor_accepted_id)
    REFERENCES mycarlos_accepted_device(binding_id,id),
  CONSTRAINT chk_mycarlos_accepted_kind CHECK (approval_kind IN ('staff_first','accepted_device')),
  CONSTRAINT chk_mycarlos_accepted_evidence CHECK (
    (approval_kind='staff_first' AND approved_by_provider_no IS NOT NULL AND approved_by_security_no IS NOT NULL
      AND staff_verification_method IS NOT NULL AND staff_verification_method='in_person_device_comparison' AND predecessor_accepted_id IS NULL
      AND predecessor_version IS NULL AND predecessor_approval IS NULL)
    OR (approval_kind='accepted_device' AND approved_by_provider_no IS NULL AND approved_by_security_no IS NULL
      AND staff_verification_method IS NULL AND predecessor_accepted_id IS NOT NULL
      AND predecessor_version IS NOT NULL AND predecessor_version>=0 AND predecessor_approval IS NOT NULL
      AND OCTET_LENGTH(predecessor_approval)>0)),
  CONSTRAINT chk_mycarlos_accepted_removal CHECK ((removed_at IS NULL AND removal_id IS NULL AND removal_reason IS NULL AND removed_by_provider_no IS NULL) OR (removed_at IS NOT NULL AND removal_id IS NOT NULL AND removal_reason IS NOT NULL AND removal_reason='staff' AND removed_by_provider_no IS NOT NULL AND removed_at>=accepted_at AND version>0)),
  CONSTRAINT chk_mycarlos_accepted_version CHECK (version>=0 AND OCTET_LENGTH(clinic_acceptance)>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
ALTER TABLE mycarlos_device_approval_challenge
  ADD CONSTRAINT fk_mycarlos_challenge_predecessor FOREIGN KEY (binding_id,predecessor_accepted_id)
  REFERENCES mycarlos_accepted_device(binding_id,id);

CREATE TABLE mycarlos_device_approval_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  challenge_id BINARY(32) NOT NULL,
  message_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  clinic_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  target_uri TEXT COLLATE utf8mb4_bin NOT NULL,
  config_binding_sha256 BINARY(32) NOT NULL,
  exact_body MEDIUMBLOB NOT NULL,
  body_sha256 BINARY(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  next_attempt_at DATETIME(6) NOT NULL,
  failures INT NOT NULL DEFAULT 0,
  lease_token BINARY(32) DEFAULT NULL,
  lease_expires_at DATETIME(6) DEFAULT NULL,
  published_at DATETIME(6) DEFAULT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mycarlos_approval_outbox_message (challenge_id,message_kind),
  KEY ix_mycarlos_approval_outbox_due (published_at,next_attempt_at,lease_expires_at),
  CONSTRAINT fk_mycarlos_approval_outbox_challenge FOREIGN KEY (challenge_id)
    REFERENCES mycarlos_device_approval_challenge(challenge_id),
  CONSTRAINT chk_mycarlos_outbox_kind CHECK (message_kind IN ('challenge','decision','removal')),
  CONSTRAINT chk_mycarlos_outbox_lease CHECK ((lease_token IS NULL)=(lease_expires_at IS NULL)),
  CONSTRAINT chk_mycarlos_outbox_bounds CHECK (OCTET_LENGTH(exact_body) BETWEEN 1 AND 32768
    AND OCTET_LENGTH(target_uri) BETWEEN 1 AND 4096 AND failures>=0 AND version>=0),
  CONSTRAINT chk_mycarlos_outbox_published CHECK (published_at IS NULL OR lease_token IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Existing customized object/grant rows are preserved; no privilege inference from _admin or _portal.account.
INSERT INTO secObjectName (objectName,description,orgapplicable)
SELECT '_portal.mycarlos.device_approval','Approve and remove myCarlos devices',0
WHERE NOT EXISTS (SELECT 1 FROM secObjectName WHERE objectName='_portal.mycarlos.device_approval');
INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
SELECT 'admin','_portal.mycarlos.device_approval','x',0,'999998'
WHERE NOT EXISTS (SELECT 1 FROM secObjPrivilege WHERE roleUserGroup='admin' AND objectName='_portal.mycarlos.device_approval');
