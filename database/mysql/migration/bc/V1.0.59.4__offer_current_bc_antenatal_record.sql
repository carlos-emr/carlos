-- Offer the current BC antenatal record in the eChart's Add Form menu (issue #5051).
--
-- The BC reference baseline (bc/V1.0.2) shows the two oldest antenatal records, BC-AR and
-- BC-AR 2007, in the Add Form menu and hides BC-AR 2012 and BC-AR 2020, so on a new BC install a
-- clinician starts an outdated antenatal record unless an administrator first changes
-- Administration > Select Forms. encounterForm.hidden is the menu position; 0 hides the form.
--
-- After this migration the menu offers BC-AR 2020 (in the position BC-AR had) and hides BC-AR
-- and BC-AR 2007. BC-AR 2012 stays hidden. Hiding a form only removes its "(new)" menu entry:
-- a patient's saved BC-AR and BC-AR 2007 records are still listed in the chart and still open
-- (EctDisplayForm2Action lists saved records of every registered form, hidden or not).
--
-- A clinic's own choice is kept: the change applies only while all four BC antenatal rows are
-- exactly as the baseline seeded them (BC-AR 1, BC-AR 2007 1, BC-AR 2012 0, BC-AR 2020 0). If any
-- of them was changed through Select Forms, or a row is missing, nothing is updated. Re-running
-- it changes nothing, because the rows are then no longer at their starter values.

SET @carlos_bcar_starter_rows := (
    SELECT COUNT(*)
    FROM `encounterForm`
    WHERE (`form_value` = '../form/formbcar.jsp?demographic_no=' AND `hidden` = 1)
       OR (`form_value` = '../form/formbcar2007.jsp?demographic_no=' AND `hidden` = 1)
       OR (`form_value` = '../form/formbcar2012.jsp?demographic_no=' AND `hidden` = 0)
       OR (`form_value` = '../form/formBCAR2020pg1.jsp?demographic_no=' AND `hidden` = 0)
);

UPDATE `encounterForm`
SET `hidden` = CASE `form_value`
        WHEN '../form/formBCAR2020pg1.jsp?demographic_no=' THEN 1
        ELSE 0
    END
WHERE @carlos_bcar_starter_rows = 4
  AND `form_value` IN (
      '../form/formbcar.jsp?demographic_no=',
      '../form/formbcar2007.jsp?demographic_no=',
      '../form/formBCAR2020pg1.jsp?demographic_no='
  );

SET @carlos_bcar_starter_rows := NULL;
