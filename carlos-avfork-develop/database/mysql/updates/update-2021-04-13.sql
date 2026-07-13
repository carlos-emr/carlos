DROP TABLE `billingvisit`;
CREATE TABLE `billingvisit` (
  `visittype` varchar(10) DEFAULT '00',
  `visit_desc` varchar(100) DEFAULT '',
  `region` varchar(5) DEFAULT ''
);

INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('A', 'Practitioner\'s Office - In Community', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('B', 'Community Health Centre', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('C', 'Continuing Care facility', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('D', 'Diagnostic Facility', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('E', 'Hospital Emergency Depart. or Diagnostic & Treatment Centre', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('F', 'Private Medical / Surgical Facility', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('G', 'Hospital - Day Care (Surgery) ', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('I', 'Hospital Inpatient', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('J', 'First Nations Primary Health Care Clinic', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('K', 'Hybrid Primary Care Practice (part-time longitudinal practice, part-time walk-in clinic)', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('L', 'Longitudinal Primary Care Practice (e.g. GP family practice or PCN clinic)', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('M', 'Mental Health Centre', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('N', 'Health Care Practitioner Office (non-physician)', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('P', 'Outpatient', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('Q', 'Specialist Physician Office', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('R', 'Patient\'s residence', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('T', 'Practitioner\'s Office - In Publicly Administered Facility', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('U', 'Urgent and Primary Care Centre', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('V', 'Virtual Care Clinic', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('W', 'Walk-In Clinic', 'BC');
INSERT INTO `billingvisit`(`visittype`, `visit_desc`, `region`) VALUES ('Z', 'None of the above', 'BC');