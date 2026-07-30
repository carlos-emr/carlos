CREATE TABLE IF NOT EXISTS sms_transaction (
  id BIGINT NOT NULL AUTO_INCREMENT,
  direction VARCHAR(16) NOT NULL,
  provider_type VARCHAR(16) NOT NULL,
  transaction_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  demographic_no INT NULL,
  requested_by_healthcare_provider_no VARCHAR(16) NULL,
  requested_by_security_no INT NULL,
  appointment_no INT NULL,
  from_phone_number VARCHAR(32) NULL,
  to_phone_number VARCHAR(32) NULL,
  recipient_phone_type VARCHAR(16) NULL,
  provider_message_id VARCHAR(128) NULL,
  client_reference_id VARCHAR(64) NULL,
  message_body TEXT NULL,
  message_body_sha256 CHAR(64) NOT NULL,
  message_body_length INT NOT NULL DEFAULT 0,
  consent_reason_code VARCHAR(64) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(1024) NULL,
  provider_metadata TEXT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  next_attempt_at DATETIME NULL,
  last_attempt_at DATETIME NULL,
  sent_at DATETIME NULL,
  delivered_at DATETIME NULL,
  received_at DATETIME NULL,
  provider_event_at DATETIME NULL,
  claim_token VARCHAR(64) NULL,
  PRIMARY KEY (id),
  KEY sms_transaction_demographic_created_idx (demographic_no, created_at),
  UNIQUE KEY sms_transaction_provider_message_uidx (provider_type, provider_message_id),
  UNIQUE KEY sms_transaction_client_reference_uidx (provider_type, client_reference_id),
  KEY sms_transaction_queue_idx (direction, provider_type, status, next_attempt_at, created_at),
  KEY sms_transaction_status_updated_idx (status, updated_at),
  KEY sms_transaction_claim_token_idx (claim_token)
);

CREATE TABLE IF NOT EXISTS sms_provider_rate_limit (
  provider_type VARCHAR(16) NOT NULL,
  send_count INT NOT NULL DEFAULT 0,
  window_started_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (provider_type)
);

INSERT IGNORE INTO secObjectName (objectName, description, orgapplicable)
VALUES ('_msgSMS', 'Read SMS message bodies', 0);

INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no)
VALUES
  ('admin', '_msgSMS', 'x', 0, '999998'),
  ('doctor', '_msgSMS', 'x', 0, '999998');

-- Default send limit is enforced in JpaSmsSendRateLimiter at 5 SMS/5 seconds until SMS provider limits are confirmed.
INSERT IGNORE INTO sms_provider_rate_limit (
  provider_type,
  send_count,
  window_started_at,
  created_at,
  updated_at
) VALUES
  ('STUB', 0, NOW(), NOW(), NOW()),
  ('VOIPMS', 0, NOW(), NOW(), NOW()),
  ('CLOUDLI', 0, NOW(), NOW(), NOW());
