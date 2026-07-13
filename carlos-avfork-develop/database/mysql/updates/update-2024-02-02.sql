UPDATE professionalSpecialists SET deleted = 0 WHERE deleted IS NULL;
ALTER TABLE professionalSpecialists MODIFY deleted tinyint(1) NOT NULL default 0;