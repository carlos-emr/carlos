-- Gate outbound SMS on a dedicated patient consent type and record the consent
-- record each send relied on.
--
-- Every statement is idempotent so a partially applied or re-run migration is safe.

-- Consent audit snapshot for each SMS system-of-record row: the resolved consent state,
-- the Consent row it came from, and that row's edit date at the time of the decision.
ALTER TABLE sms_transaction
    ADD COLUMN IF NOT EXISTS consent_status VARCHAR(32) NULL AFTER consent_reason_code,
    ADD COLUMN IF NOT EXISTS consent_id INT NULL AFTER consent_status,
    ADD COLUMN IF NOT EXISTS consent_last_update_date DATETIME NULL AFTER consent_id;

-- SMS gets its own consent type instead of reusing electronic_communication_consent:
-- that wording never mentions text messages, and SMS carries a lock-screen disclosure
-- risk email does not. Existing patients therefore start at "unknown" (blocked) until
-- SMS consent is recorded for them.
--
-- Seeded INACTIVE: the wording below is a draft awaiting compliance sign-off, and an
-- active consent type is shown on every patient record at every clinic. While it is
-- inactive, SMS stays blocked as SMS_CONSENT_NOT_CONFIGURED and staff never see the
-- wording. There is no administration screen for consent types, so a clinic (or a later
-- migration carrying the approved wording) turns it on with:
--   UPDATE consentType SET active = 1 WHERE type = 'sms_communication_consent';
INSERT INTO consentType (type, name, description, active, providerNo, remoteEnabled)
SELECT 'sms_communication_consent',
       'SMS Text Message Consent',
       'This patient has consented to receive text messages (SMS) from the clinic, including appointment reminders and administrative notices. Text messages are not encrypted and may be visible on a locked screen. The patient may withdraw consent at any time.',
       0, NULL, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM consentType WHERE type = 'sms_communication_consent');

-- Mirrors the email_communication property: names the consent type SMS sends are checked
-- against. Clearing or repointing this row is how a clinic disables or changes SMS consent.
INSERT INTO property (name, value, provider_no)
SELECT 'sms_communication', 'sms_communication_consent', NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM property WHERE name = 'sms_communication');
