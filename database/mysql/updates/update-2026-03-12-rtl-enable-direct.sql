-- Enable Rich Text Letter (RTL) directly via SQL
--
-- The RTLSettings2Action admin UI has been removed. Use this SQL to
-- enable or disable the Rich Text Letter e-form directly in the database.
--
-- The eform table's `status` column (mapped as `current` in the EForm entity)
-- controls whether a form is active. 1 = enabled, 0 = disabled.

-- Enable Rich Text Letter
UPDATE eform
SET status = 1
WHERE form_name = 'Rich Text Letter'
  AND subject = 'Rich Text Letter Generator';

-- To disable Rich Text Letter, run the following instead:
-- UPDATE eform
-- SET status = 0
-- WHERE form_name = 'Rich Text Letter'
--   AND subject = 'Rich Text Letter Generator';
