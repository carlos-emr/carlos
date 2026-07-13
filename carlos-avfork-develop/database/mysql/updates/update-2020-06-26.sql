alter table DemographicContact add mrp tinyint(1);
alter table DemographicContact add programNo int(11);

alter table HRMDocumentSubClass add sendingFacilityId varchar(50);
alter table HRMCategory add sendingFacilityId varchar(50),

alter table professionalSpecialists add deleted tinyint(1) not null default 0;