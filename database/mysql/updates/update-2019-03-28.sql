alter table groupMembers_tbl add facilityId int(6);
alter table groupMembers_tbl add destinationFacilityId int(6);
alter table messagelisttbl add destinationFacilityId int(6);
alter table messagelisttbl add sourceFacilityId int(6);
alter table oscar_msg_type add code varchar(255);
alter table groupMembers_tbl add clinicLocationNo int(11);

update groupMembers_tbl set facilityId = 0 where facilityId is null;
update messagelisttbl set destinationFacilityId = 0 where destinationFacilityId is null;
update messagelisttbl set sourceFacilityId = 0 where sourceFacilityId is null;
update groupMembers_tbl set clinicLocationNo = 0 where clinicLocationNo is null;
update oscar_msg_type set `code` = 'GENERAL_TYPE' where `type` = 2;
update oscar_msg_type set `code` = 'OSCAR_REVIEW_TYPE' where `type` = 1;

INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('-1', '_msg', 'x', 0, '999998');
INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('-1', '_demographic', 'r', 0, '999998');

CREATE TABLE `msgIntegratorDemoMap` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `messageId` int(11) ,
  `sourceDemographicNo` int(11),
  `sourceFacilityId` int(6),
  `msgDemoMapId` int(11),
  PRIMARY KEY (`id`)
);

INSERT INTO `oscar_msg_type`(`type`, `description`, `code`) VALUES (3, 'Integrator Message', 'INTEGRATOR_TYPE');

INSERT INTO `property`(`name`, `value`, `provider_no`) VALUES ('integrator_patient_consent', '1', NULL); 