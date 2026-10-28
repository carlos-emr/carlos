-- Clinic code v1 (carlos-emr/mycarlos#141): a new device can be approved with a 9-digit code the
-- clinic emails the patient, instead of by staff. Written with Ben's OK (28 Oct 2026); the number is
-- the next free one and is fixed at merge time. V1.0.81 is not edited.
--
-- 1. The approval kind 'code' joins 'staff_first' and 'accepted_device'. Like 'staff_first' it has
--    no predecessor and no predecessor proof. Its accepted device has no staff fields (policy
--    "code"), or all of them (policy "code_and_staff": staff approve after the code matched).
-- 2. mycarlos_device_approval_code keeps, per code challenge, a keyed hash of the code (never the
--    code itself), the wrong attempts, the expiry and when the email was sent.
--
-- Safe to run again: each CHECK is dropped if present and added back, the table is created only if
-- missing. Existing rows already meet the wider CHECKs.

ALTER TABLE mycarlos_device_approval_challenge DROP CONSTRAINT IF EXISTS chk_mycarlos_challenge_kind;
ALTER TABLE mycarlos_device_approval_challenge ADD CONSTRAINT chk_mycarlos_challenge_kind
  CHECK (approval_kind IN ('staff_first','accepted_device','code'));

ALTER TABLE mycarlos_device_approval_challenge DROP CONSTRAINT IF EXISTS chk_mycarlos_challenge_predecessor;
ALTER TABLE mycarlos_device_approval_challenge ADD CONSTRAINT chk_mycarlos_challenge_predecessor CHECK (
    (approval_kind IN ('staff_first','code') AND predecessor_accepted_id IS NULL
      AND predecessor_device_key_id IS NULL AND predecessor_version IS NULL)
    OR (approval_kind='accepted_device' AND binding_id IS NOT NULL AND predecessor_accepted_id IS NOT NULL
      AND predecessor_device_key_id IS NOT NULL AND predecessor_version IS NOT NULL AND predecessor_version>=0));

ALTER TABLE mycarlos_device_approval_challenge DROP CONSTRAINT IF EXISTS chk_mycarlos_challenge_evidence;
ALTER TABLE mycarlos_device_approval_challenge ADD CONSTRAINT chk_mycarlos_challenge_evidence CHECK (
    (approval_kind IN ('staff_first','code') AND consumed_approval_jti IS NULL AND consumed_approval IS NULL
      AND consumed_approval_sha256 IS NULL)
    OR (approval_kind='accepted_device' AND (
      (consumed_at IS NULL AND consumed_approval_jti IS NULL AND consumed_approval IS NULL
        AND consumed_approval_sha256 IS NULL)
      OR (consumed_at IS NOT NULL AND consumed_approval_jti IS NOT NULL AND consumed_approval IS NOT NULL
        AND OCTET_LENGTH(consumed_approval)>0 AND consumed_approval_sha256 IS NOT NULL))));

ALTER TABLE mycarlos_accepted_device DROP CONSTRAINT IF EXISTS chk_mycarlos_accepted_kind;
ALTER TABLE mycarlos_accepted_device ADD CONSTRAINT chk_mycarlos_accepted_kind
  CHECK (approval_kind IN ('staff_first','accepted_device','code'));

ALTER TABLE mycarlos_accepted_device DROP CONSTRAINT IF EXISTS chk_mycarlos_accepted_evidence;
ALTER TABLE mycarlos_accepted_device ADD CONSTRAINT chk_mycarlos_accepted_evidence CHECK (
    (approval_kind='staff_first' AND approved_by_provider_no IS NOT NULL AND approved_by_security_no IS NOT NULL
      AND staff_verification_method IS NOT NULL AND staff_verification_method='in_person_device_comparison' AND predecessor_accepted_id IS NULL
      AND predecessor_version IS NULL AND predecessor_approval IS NULL)
    OR (approval_kind='accepted_device' AND approved_by_provider_no IS NULL AND approved_by_security_no IS NULL
      AND staff_verification_method IS NULL AND predecessor_accepted_id IS NOT NULL
      AND predecessor_version IS NOT NULL AND predecessor_version>=0 AND predecessor_approval IS NOT NULL
      AND OCTET_LENGTH(predecessor_approval)>0)
    OR (approval_kind='code' AND predecessor_accepted_id IS NULL AND predecessor_version IS NULL
      AND predecessor_approval IS NULL AND (
        (approved_by_provider_no IS NULL AND approved_by_security_no IS NULL AND staff_verification_method IS NULL)
        OR (approved_by_provider_no IS NOT NULL AND approved_by_security_no IS NOT NULL
          AND staff_verification_method='in_person_device_comparison'))));

-- One row per code challenge. code_mac is HMAC-SHA256 over the request id and the code under a key
-- held only in the running server's memory (code_key_id names it), so this table alone cannot
-- give the code back. A restart forgets the key: open codes then cannot match and expire.
CREATE TABLE IF NOT EXISTS mycarlos_device_approval_code (
  challenge_id BINARY(32) NOT NULL,
  clinic_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_id BINARY(16) NOT NULL,
  policy VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  code_key_id BINARY(16) NOT NULL,
  code_mac BINARY(32) NOT NULL,
  wrong_attempts INT NOT NULL DEFAULT 0,
  issued_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  emailed_at DATETIME(6) DEFAULT NULL,
  matched_at DATETIME(6) DEFAULT NULL,
  refused_at DATETIME(6) DEFAULT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (challenge_id),
  UNIQUE KEY uq_mycarlos_code_request (clinic_id, request_id),
  KEY ix_mycarlos_code_open (clinic_id, matched_at, refused_at, expires_at),
  CONSTRAINT fk_mycarlos_code_challenge FOREIGN KEY (challenge_id)
    REFERENCES mycarlos_device_approval_challenge(challenge_id),
  CONSTRAINT chk_mycarlos_code_policy CHECK (policy IN ('code','code_and_staff')),
  CONSTRAINT chk_mycarlos_code_attempts CHECK (wrong_attempts BETWEEN 0 AND 5),
  CONSTRAINT chk_mycarlos_code_time CHECK (expires_at>issued_at
    AND expires_at<=DATE_ADD(issued_at,INTERVAL 600 SECOND)),
  CONSTRAINT chk_mycarlos_code_terminal CHECK (matched_at IS NULL OR refused_at IS NULL),
  CONSTRAINT chk_mycarlos_code_refused CHECK (refused_at IS NULL OR wrong_attempts=5),
  CONSTRAINT chk_mycarlos_code_version CHECK (version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
