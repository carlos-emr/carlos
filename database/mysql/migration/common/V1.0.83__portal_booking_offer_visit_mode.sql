-- Patient portal offered times: the visit mode an offer was made with (#3850).
--
-- A booking prompt's times can be offered as in person, by phone or by video. The booking-sync job
-- offers REPLACEMENT times after a refused pick from the clinic settings, and the portal's pick
-- carries no visit mode, so each offer remembers its own mode and a replacement is made with the
-- mode of the time it replaces. NULL means the offer was made before this column existed, or with
-- the clinic's default: it is then shown with patient_portal.booking.visit_mode, as before.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS, so a re-run (the devcontainer re-applies migrations over
-- demo data) changes nothing.
--
-- Renumber before merge: take the next free version above the highest in BOTH develop and
-- release/2026.08 at merge time.

ALTER TABLE portal_booking_offer
    ADD COLUMN IF NOT EXISTS visit_mode VARCHAR(16) DEFAULT NULL AFTER template_code;
