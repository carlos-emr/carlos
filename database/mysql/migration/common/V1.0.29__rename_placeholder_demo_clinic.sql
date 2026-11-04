-- Replace the upstream placeholder clinic name with a CARLOS-neutral one.
--
-- The genesis reference data (on/V1.0.2__on_data.sql, bc/V1.0.2__bc_data.sql) seeds a single
-- clinic row carried over from the OSCAR McMaster heritage:
--
--   (1234,'McMaster Hospital','Hamilton',...)
--
-- That row is not decoration. `clinic_name` is what the eForm AP of the same name returns, and
-- the Rich Text Letter's ##letterhead## button prints it at the top of every letter -- so a
-- brand-new CARLOS install writes patient correspondence on McMaster Hospital letterhead until
-- an administrator notices and edits it under Administration > Clinic. CARLOS has no affiliation
-- with McMaster University, and a placeholder that looks like a real institution is worse than
-- one that is obviously a placeholder.
--
-- Scope: the name only. The address, phone and fax on that row are placeholder values too
-- (555-555-5555), but they read as placeholders and a clinic replaces them in the same screen.
--
-- Guarded on the exact seeded value, so an install that has already set its real clinic name --
-- including one that legitimately IS in Hamilton -- is left alone. Idempotent: re-running it
-- matches nothing.

UPDATE `clinic`
SET `clinic_name` = 'CARLOS Demo Clinic'
WHERE `clinic_name` = 'McMaster Hospital';
