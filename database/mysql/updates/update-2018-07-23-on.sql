
DELETE FROM LookupListItem where value='CNO' AND lookupListId = (select id from LookupList where name = 'practitionerNoType');
INSERT INTO `LookupListItem` VALUES (\N,(select id from LookupList where name = 'practitionerNoType'),'OCP','Ontario College of Pharmacists (OCP)',3,1,'oscar',now());
INSERT INTO `LookupListItem` VALUES (\N,(select id from LookupList where name = 'practitionerNoType'),'CNORNP','RNP - College of Nurses of Ontario (CNO)',3,1,'oscar',now());
INSERT INTO `LookupListItem` VALUES (\N,(select id from LookupList where name = 'practitionerNoType'),'CNORN','RN - College of Nurses of Ontario  (CNO)',3,1,'oscar',now());
INSERT INTO `LookupListItem` VALUES (\N,(select id from LookupList where name = 'practitionerNoType'),'CNORPN','RPN - College of Nurses of Ontario  (CNO)',3,1,'oscar',now());
INSERT INTO `LookupListItem` VALUES (\N,(select id from LookupList where name = 'practitionerNoType'),'CMO','College of Midwives of Ontario',3,1,'oscar',now());

-- NOTE: The following SQL updates have been commented out as they have been removed from the codebase.
-- They are left here for historical reference. If you are restoring these features, please ensure to uncomment these lines.
-- INSERT INTO `OscarJobType` VALUES (\N,'CanadianVaccineCatalogueUpdater','Updates the local copy of the data','org.oscarehr.integration.born.CanadianVaccineCatalogueJob',1,now()),(\N,'BORN FHIR','','org.oscarehr.integration.born.BORNFhirJob',1,now());
-- INSERT INTO `OscarJob` VALUES (\N,'CanadianVaccineCatalogueUpdater','Updates the CVC data',(select id from OscarJobType where name='CanadianVaccineCatalogueUpdater'),'0 * 0 * * *','999998',0,now()),(\N,'BORN FHIR','',(select id from OscarJobType where name='BORN FHIR'),'0 * * * * *','999998',0,now());
