-- Patient portal invitation delivery record (issue #3854).
--
-- One row per attempt to deliver a portal invitation. The portal's two-phase contract requires
-- CARLOS to hold the email job durably before the portal activates the token, and to record the
-- outcome itself; this table is that record. It is deliberately separate from emailLog: the email
-- row describes the message, this row describes where the invitation's lifecycle stands, and a
-- portal email password delivery (#3681) keeps its own state on emailLog without colliding.
--
-- delivery_operation_id is the idempotency key sent to the portal on every prepare and commit
-- retry, so it is unique and case-sensitive. The invite code itself is never stored here: it is
-- held in memory until the email row exists, and a lost prepare response recovers it by retrying
-- with the same operation id.
--
-- portal_origin and clinic_id pin the row to the portal connection that created it; recovery is
-- refused against a different one.
CREATE TABLE IF NOT EXISTS patient_portal_invite_delivery (
  id BIGINT NOT NULL AUTO_INCREMENT,
  delivery_operation_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  demographic_no INT NOT NULL,
  clinic_id VARCHAR(64) NOT NULL,
  portal_origin VARCHAR(512) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  state VARCHAR(32) NOT NULL,
  portal_invite_id BIGINT NULL,
  superseded_invite_id BIGINT NULL,
  email_log_id INT NULL,
  requested_by VARCHAR(16) NOT NULL,
  error_message VARCHAR(255) NULL,
  expires_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY ppid_operation_uidx (delivery_operation_id),
  KEY ppid_demographic_created_idx (demographic_no, created_at),
  KEY ppid_portal_invite_idx (portal_invite_id)
);
