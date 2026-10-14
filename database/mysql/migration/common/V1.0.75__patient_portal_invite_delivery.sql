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
--
-- outcome is a code (PatientPortalInviteDelivery.Outcome), not prose, so the staff page can explain
-- it in the reader's language. revoke_failed records that an unused code could not be withdrawn on
-- the portal and will expire on its own.
--
-- email_log_id is indexed because Manage Emails asks, per invitation email, whether its delivery is
-- still open: only then does the invitation workflow, not manual resolution, own the outbox row.
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
  email_log_id BIGINT NULL,
  requested_by VARCHAR(16) NOT NULL,
  outcome VARCHAR(32) NULL,
  revoke_failed BOOLEAN NOT NULL DEFAULT FALSE,
  expires_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY ppid_operation_uidx (delivery_operation_id),
  KEY ppid_demographic_created_idx (demographic_no, created_at),
  KEY ppid_portal_invite_idx (portal_invite_id),
  KEY ppid_email_log_idx (email_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Let the `doctor` role invite patients.
--
-- V1.0.41 granted `_portal.invite` to `admin` only, and `admin` holds no `_demographic` right in
-- either province's baseline data, so no user could invite a patient without an administrator
-- granting rights by hand. (For portal email, V1.0.41 also gives the doctor and nursing roles
-- read-only `_portal.account` and full `_portal.secret`.) `doctor` is the only non-admin role the
-- baseline grants `_email`, which sending an invitation also requires, so it is the only role that
-- can complete an invitation today.
--
-- `_portal.invite` is granted in full: issuing, resending, revoking and resolving a delivery are one
-- job. `_portal.account` read is not granted again here: V1.0.41 already gives it to `doctor`, and a
-- clinic may have deliberately removed it since. `_portal.account.unlock` is deliberately
-- NOT granted: V1.0.41 split it out because clearing a lockout forces a password reset on the patient,
-- and nothing in the invitation workflow needs it.
--
-- Front-desk roles (receptionist, secretary, nurse and the rest) are a deployment decision: they hold
-- `_demographic` but not `_email`, so `_portal.invite` would let them see and revoke invitations, but
-- not send one or resolve an unfinished delivery, both of which write to the email outbox. Grant it in
-- Administration > Security where a clinic wants that.
INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES
    ('doctor', '_portal.invite', 'x', 0, '999998');
