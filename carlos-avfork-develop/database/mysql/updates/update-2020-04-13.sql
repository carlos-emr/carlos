alter table fax_config add senderEmail varchar(255);
alter table demographicPharmacy add consentToContact tinyint(1);
alter table consultationRequests add demographicContactId int(10);