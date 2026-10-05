-- Rich Text Letter: add the provider identity inputs the signature stamp is chosen from, on
-- databases that already hold the letter.
--
-- update-2026-09-20-rtl-provider-stamp-fields.sql (#3816) adds three hidden inputs to the seeded
-- Rich Text Letter's form_html: user_id, user_ohip_no and doctor_provider_no. editControl2.js reads
-- them to stamp a letter with the signer's consult_sig_<provider_no>.png. That script lives in the
-- frozen updates/ tree, so it only ever runs where something replays it explicitly: the
-- devcontainer's populate_db.sh, the deb's one-time `carlos-ctl demo-data` load, and
-- `carlos-ctl import-o19`. A package UPGRADE runs none of those -- it runs Flyway -- so an install
-- whose Rich Text Letter was seeded by an earlier package (demo data loaded on 2026.08.0-alpha13 or
-- before, then upgraded) kept a form_html without the inputs, and its letters never picked up the
-- provider's stored signature. This migration applies the same edit through Flyway so the upgrade
-- path gets it too.
--
-- Scope and safety:
--   * Fresh installs: Flyway runs on an empty schema that holds no eform rows, so this matches
--     nothing; the demo-data load and the O19 importer still replay the updates/ script after it.
--   * The statement below is byte-for-byte the UPDATE in that script, including its guards: only
--     the canonical 2026.3.0 row (form_name + subject), only when the anchor input is present, and
--     never when id="user_ohip_no" is already there. A clinic-customized form without the anchor,
--     or a row the updates/ script already patched, is left alone. Re-running matches nothing.
--   * The anchor is the WT measurement input, which sits before the editControl2.js <script>, so
--     the new inputs are seeded into the APCache by the script's $('input:hidden') sweep.
--   * No stored procedure (unlike the updates/ script): a guarded UPDATE is already a no-op when
--     the row is absent, and plain DML keeps the migration portable across Flyway runners.

UPDATE eform
SET form_html = REPLACE(
        form_html,
        '<input type="hidden" name="WT" id="WT" oscarDB=m$WT#value>',
        CONCAT(
            '<input type="hidden" name="WT" id="WT" oscarDB=m$WT#value>', '\r\n',
            '<input type="hidden" name="user_id" id="user_id" oscarDB="current_user_id">', '\r\n',
            '<input type="hidden" name="user_ohip_no" id="user_ohip_no" oscarDB="current_user_ohip_no">', '\r\n',
            '<input type="hidden" name="doctor_provider_no" id="doctor_provider_no" oscarDB="doctor_provider_no">'
        ))
WHERE form_name = 'Rich Text Letter'
  AND subject LIKE 'Rich Text Letter Generator%'
  AND form_html LIKE '%<input type="hidden" name="WT" id="WT" oscarDB=m$WT#value>%'
  AND form_html NOT LIKE '%id="user_ohip_no"%';
