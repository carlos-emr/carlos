-- Synthetic infant for the PR 5705 Growth 0-36 visual check (copy of demo patient 1, renamed).
DROP TEMPORARY TABLE IF EXISTS pr5705_patient;
CREATE TEMPORARY TABLE pr5705_patient SELECT * FROM demographic WHERE demographic_no = 1;
UPDATE pr5705_patient SET demographic_no = 990705, last_name = 'FAKE-GROWTH', first_name = 'Visual',
  sex = 'F', year_of_birth = '2025', month_of_birth = '05', date_of_birth = '07', hin = '', ver = '',
  chart_no = 'PR5705', email = NULL;
INSERT INTO demographic SELECT * FROM pr5705_patient
  WHERE NOT EXISTS (SELECT 1 FROM demographic WHERE demographic_no = 990705);
SELECT demographic_no, last_name, first_name, sex, year_of_birth, month_of_birth, date_of_birth FROM demographic WHERE demographic_no = 990705;
