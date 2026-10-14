-- Rich Text Letter: expose the provider identity the signature stamp is chosen from
--
-- The RTL's Stamp button and its Closing Salutation both resolve a signature image
-- through pickStamp() in editControl2.js. That used to know nothing about providers:
-- it looked the signer up in ImgArray, the "doctor|SignatureFile.png" list a clinic
-- hand-maintains in a stamps.js uploaded to the eForm images folder, and fell back to
-- a single stamp.png for everyone. A multi-provider clinic without a stamps.js
-- therefore signed every letter with the same image.
--
-- CARLOS already stores a per-provider signature as consult_sig_<provider_no>.png
-- (Administration > Provider), which is what the consultation module and the visual
-- eForm editor stamp with. This migration gives the RTL the three values it needs to
-- pick the same file, as hidden inputs the eForm framework populates server-side from
-- their oscarDB= attributes:
--
--   user_id            (current_user_id)        the logged-in provider
--   user_ohip_no       (current_user_ohip_no)   their billing number
--   doctor_provider_no (doctor_provider_no)     the patient's MRP
--
-- editControl2.js applies the same delegation rule as sign() in visualEformEditor.jsp:
-- an ohip_no above the billing threshold means the logged-in user is an MD/NP/RMW who
-- signs their own letters; anything below it is a non-billing account (resident, nurse,
-- clerical, room/resource pseudo-provider) writing under the direction of the patient's
-- MRP, so the MRP's signature is used instead. Numbers below the threshold are what
-- lets a clinic give a non-OHIP provider a schedule.
--
-- The script falls back to stamps.js / stamp.png when no signature file exists, so an
-- install that has not loaded per-provider signatures keeps working unchanged.
--
-- Idempotent: the UPDATE is a no-op once the inputs are present, and the WHERE clause
-- skips a form_html that already carries them.

DROP PROCEDURE IF EXISTS rtl_add_provider_stamp_fields;
DELIMITER //
CREATE PROCEDURE rtl_add_provider_stamp_fields()
BEGIN
    DECLARE row_count INT;

    SELECT COUNT(*) INTO row_count FROM eform
    WHERE form_name = 'Rich Text Letter'
      AND subject LIKE 'Rich Text Letter Generator%';

    IF row_count = 0 THEN
        SELECT 'RTL eForm not found, skipping provider stamp fields' AS info;
    ELSE
        -- Anchored on the existing WT measurement input: it sits immediately after the
        -- <form> tag and BEFORE the editControl2.js <script>, which matters because the
        -- script's $('input:hidden') sweep copies hidden inputs into the APCache as it
        -- runs. An input added after the script would still be found by getElementById,
        -- but would not seed the cache.
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

        SELECT 'RTL eForm provider stamp fields applied' AS info;
    END IF;
END //
DELIMITER ;
CALL rtl_add_provider_stamp_fields();
DROP PROCEDURE IF EXISTS rtl_add_provider_stamp_fields;
