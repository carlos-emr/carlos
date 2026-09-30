-- Turn on the SMS consent type with its approved staff-facing wording (#3848).
--
-- The SMS consent migration (add_sms_consent) seeded sms_communication_consent inactive, with a
-- draft description awaiting compliance review. This sets the approved description and
-- active = 1 in one statement, so the type is never switched on with the draft wording.
--
-- It only changes the row while its description is still the seeded draft (compared the way
-- the column's collation compares text: ignoring letter case and spaces at the end). A clinic
-- that has already written its own wording keeps it, and keeps its own active setting. Running
-- it again changes nothing.
--
-- The approved wording does not mention the patient's phone number: consent is recorded for
-- the patient, not for one number, until #2674. Consents are not tied to a wording version
-- either (#2674), so a clinic that switched the type on by hand earlier keeps consents that
-- were recorded under the draft.
--
-- Numbered at merge time: the version must be above the highest on both develop and
-- release/2026.08.
UPDATE consentType
SET description = 'This patient has been told the risks of text messaging and has explicitly consented to receive text messages (SMS) from the clinic, limited to appointment reminders and administrative notices with no clinical details or marketing. Text messages are not encrypted, may be seen by others on a locked or shared phone, and are not for urgent matters. The patient may withdraw consent at any time by telling the clinic.',
    active = 1
WHERE type = 'sms_communication_consent'
  AND description = 'This patient has consented to receive text messages (SMS) from the clinic, including appointment reminders and administrative notices. Text messages are not encrypted and may be visible on a locked screen. The patient may withdraw consent at any time.';
