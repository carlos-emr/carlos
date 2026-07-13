alter table FaxClientLog add transactionType varchar(25);
alter table FaxClientLog modify column requestId int(10);
alter table FaxClientLog modify column faxId int(10);
INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('-1', '_fax', 'x', 0, '999999');
alter table document add fileSignature varchar(255);
alter table document add receivedDate date;
alter table document add abnormal int(1);
update document set abnormal = 0 where abnormal is null;