CREATE TABLE OLISResultNomenclature (
  id INT NOT NULL AUTO_INCREMENT,
  nameId  VARCHAR(10),
  name TEXT,
  PRIMARY KEY(id)
);

-- Data file last updated March 10, 2023
-- https://ehealthontario.on.ca/en/olis-nomenclature/download/olis-nomenclatures/prod/v2.69
LOAD DATA LOCAL INFILE 'OLISTestResultNomenclature.csv'
INTO TABLE OLISResultNomenclature
FIELDS TERMINATED BY '\t'
OPTIONALLY ENCLOSED BY '\"' 
LINES TERMINATED BY '\n'
(nameId, name);

CREATE TABLE OLISRequestNomenclature (
  id INT NOT NULL AUTO_INCREMENT,
  nameId  VARCHAR(10),
  name TEXT,
  category VARCHAR(20),
  PRIMARY KEY(id)
);

-- Data file last updated March 10, 2023
-- https://ehealthontario.on.ca/en/olis-nomenclature/download/olis-nomenclatures/prod/v2.69
LOAD DATA LOCAL INFILE 'OLISTestRequestNomenclature.csv'
INTO TABLE OLISRequestNomenclature
FIELDS TERMINATED BY '\t'
OPTIONALLY ENCLOSED BY '\"' 
LINES TERMINATED BY '\n'
(nameId, name, category);

CREATE TABLE OLISProviderPreferences (
  providerId  VARCHAR(10),
  startTime VARCHAR(20),
  lastRun datetime,
  PRIMARY KEY(providerId)
);

CREATE TABLE OLISSystemPreferences (
  id INT NOT NULL AUTO_INCREMENT,
  startTime VARCHAR(20),
  endTime VARCHAR(20),
  pollFrequency INT,
  lastRun timestamp,
  filterPatients tinyint(1),
  PRIMARY KEY(id)
);

update OLISSystemPreferences set filterPatients=0;

CREATE TABLE OLISResults (
  id int(11) auto_increment,
  requestingHICProviderNo varchar(30),
  providerNo varchar(30),
  queryType varchar(20),
  results text,
  hash varchar(255),
  status varchar(10),
  uuid varchar(255),
  query varchar(255),
  demographicNo integer,
  queryUuid varchar(255),
  PRIMARY KEY(id)
);

CREATE TABLE OLISQueryLog (
  id int(11) auto_increment,
  initiatingProviderNo varchar(30),
  queryType varchar(20),
  queryExecutionDate datetime,
  uuid varchar(255),
  requestingHIC varchar(30),
  demographicNo integer,
  PRIMARY KEY(id)
);
