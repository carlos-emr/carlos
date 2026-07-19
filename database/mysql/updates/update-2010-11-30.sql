-- ET: Multiple Offices Support
-- updated by vweng July 15, 2010

-- create sec objects
insert into `secObjectName` 
	(objectName, `description`, orgapplicable)
	values
	('_team_access_privacy', 'restrict access to only the same team of a providers', 0);

insert into `secObjectName` 
	(objectName, `description`, orgapplicable)
	values
	('_site_access_privacy', 'restrict access to only the assigned sites of a providers', 0);

-- create new roles
insert into `secRole` 
	(role_no, role_name, `description`)
SELECT 
	 (SELECT MAX(role_no) from `secRole`)  +  1 AS role_no
	,'Site Manager' AS role_name
	,'Site Manager' AS `description`;

insert into `secRole` 
	(role_no, role_name, `description`)
SELECT 
	 (SELECT MAX(role_no) from `secRole`)  +  1 AS role_no
	,'Partner Doctor' AS role_name
	,'Partner Doctor' AS `description`;

-- add site providers id range

ALTER TABLE site ADD providerId_from int null;
ALTER TABLE site ADD providerId_to int null;


alter table consultationRequests add site_name varchar(255) null ;


