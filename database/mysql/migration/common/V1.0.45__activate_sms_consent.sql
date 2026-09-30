-- Turn on the SMS consent type with its approved staff-facing wording (#3848).
--
-- The SMS consent migration (add_sms_consent) seeded sms_communication_consent inactive, with a
-- draft description awaiting compliance review. This sets the approved description and
-- active = 1 in one statement, so the type is never switched on with the draft wording.
--
-- It only changes the row while both of these hold, so a clinic's own choices stand:
--   * its description is still the seeded draft (compared the way the column's collation
--     compares text: ignoring letter case and spaces at the end). A clinic that wrote its own
--     wording keeps it, and keeps its own active setting.
--   * the sms_communication property, which names the consent type SMS sends are checked
--     against, still points at this type: at least one such row, and none cleared, blank or
--     pointing elsewhere. Clearing or repointing it is how a clinic turns SMS consent off, so
--     a clinic that did so keeps it off.
-- A clinic that switched only the type's active flag off, keeping the draft and the property,
-- cannot be told apart from one that never switched it on, and is switched on.
-- Running it again changes nothing.
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
  AND EXISTS (SELECT 1 FROM property p
              WHERE p.name = 'sms_communication' AND TRIM(p.value) = 'sms_communication_consent')
  AND NOT EXISTS (SELECT 1 FROM property p
                  WHERE p.name = 'sms_communication'
                    AND (p.value IS NULL OR TRIM(p.value) <> 'sms_communication_consent'))
  AND description = 'This patient has consented to receive text messages (SMS) from the clinic, including appointment reminders and administrative notices. Text messages are not encrypted and may be visible on a locked screen. The patient may withdraw consent at any time.';
