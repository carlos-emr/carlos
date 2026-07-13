-- this index is designed to reduce the time it takes to import a HL7 lab.  The specific line this is optimizing is
-- for (Measurement m : measurementDao.findByValue("lab_no", matchingLabs[k - 1])) {
-- in oscar/oscarLab/ca/all/Hl7textResultsData.java
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `measurements_ext_keyval_val` ON `measurementsExt` (`keyval`, `val`(100));
