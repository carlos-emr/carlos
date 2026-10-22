-- Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
-- This software is published under the GPL GNU General Public License, version 2 or later.
--
-- Retire the Patient Encounter Worksheet (issue #5089). The worksheet printed a paper encounter summary with space for
-- handwritten notes; its page, PDF template and print configuration are removed from the application, so its Forms-menu
-- registration would open a page that no longer exists. It saved nothing (form_table is empty), so no clinical rows go.
--
-- Fresh installs: the Ontario and BC reference data (V1.0.2__on_data.sql, V1.0.2__bc_data.sql) still insert the row;
-- applied migrations are never edited, so this later migration removes it on fresh and upgraded databases alike.
-- Idempotent: the DELETE matches nothing once the row is gone. It matches the worksheet's address (form_value, the
-- primary key) whatever display name, order or hidden flag a clinic gave it: the seeded
-- ../form/patientEncounterWorksheet.jsp?demographic_no= and the extensionless form, under any path prefix. The name
-- must end at .jsp, the query string or the end of the address, so a clinic's own form whose name merely starts with
-- patientEncounterWorksheet (patientEncounterWorksheetCustom.jsp) keeps its registration.
DELETE FROM encounterForm
WHERE form_value LIKE '%form/patientEncounterWorksheet.jsp?%'
   OR form_value LIKE '%form/patientEncounterWorksheet.jsp'
   OR form_value LIKE '%form/patientEncounterWorksheet?%'
   OR form_value LIKE '%form/patientEncounterWorksheet';
