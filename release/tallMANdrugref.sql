-- Copyright P Hutten-Czapski 2016-2021 released under GPL v2+
-- v 1.6
-- this script changes new OSCAR Rx into TALLman format
-- and cleans out generic company prefixes / suffixes
-- usage
-- mysql -uroot -p****** drugref < tallMANdrugref.sql
-- its run when you execute the Deb since oscar_emr15-67~677.deb 

UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AFATINIB' , 'AFAtinib') WHERE `name` LIKE '%AFATINIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AXITINIB' , 'aXitinib') WHERE `name` LIKE '%AXITINIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AMLODIPINE' , 'amLODIPine') WHERE `name` LIKE '%AMLODIPINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AZACITIDINE' , 'azaCITIDine') WHERE `name` LIKE '%AZACITIDINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AZATHIOPRINE' , 'azaTHIOprine') WHERE `name` LIKE '%AZATHIOPRINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'CARBOPLATIN' , 'CARBOplatin') WHERE `name` LIKE '%CARBOPLATIN%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'CISPLATIN' , 'CISplatin') WHERE `name` LIKE '%CISPLATIN%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'CYCLOSERINE' , 'cycloSERINE') WHERE `name` LIKE '%CYCLOSERINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'CYCLOSPORINE' , 'cycloSPORINE') WHERE `name` LIKE '%CYCLOSPORINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DABRAFENIB' , 'daBRAFenib') WHERE `name` LIKE '%DABRAFENIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DASATINIB' , 'daSATinib') WHERE `name` LIKE '%DASATINIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DACTINOMYCIN' , 'DACTINomycin') WHERE `name` LIKE '%DACTINOMYCIN%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DAUNORUBICIN' , 'DAUNOrubicin') WHERE `name` LIKE '%DAUNORUBICIN%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOXORUBICIN' , 'DOXOrubicin') WHERE `name` LIKE '%DOXORUBICIN%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DEXMEDETOMIDINE' , 'dexmedeTOMidine') WHERE `name` LIKE '%DEXMEDETOMIDINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DILTIAZEM' , 'dilTIAZem') WHERE `name` LIKE '%DILTIAZEM%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DIMENHYDRINATE' , 'dimenhyDRINATE') WHERE `name` LIKE '%DIMENHYDRINATE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DIPHENHYDRAMINE' , 'diphenhydrAMINE') WHERE `name` LIKE '%DIPHENHYDRAMINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOBUTAMINE' , 'DOBUTamine') WHERE `name` LIKE '%DOBUTAMINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOPAMINE' , 'DOPamine') WHERE `name` LIKE '%DOPAMINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOCETAXEL' , 'DOCEtaxel') WHERE `name` LIKE '%DOCETAXEL%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'PACLITAXEL' , 'PACLitaxel') WHERE `name` LIKE '%PACLITAXEL%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'IDARUBICIN' , 'IDArubicin') WHERE `name` LIKE '%IDARUBICIN%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'EPHEDRINE' , 'ePHEDrine') WHERE `name` LIKE '%EPHEDRINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'ERIBULIN' , 'eriBULin') WHERE `name` LIKE '%ERIBULIN%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'SUFENTANIL' , 'SUFentanil') WHERE `name` LIKE '%SUFENTANIL%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'HYDROMORPHONE' , 'HYDROmorphone') WHERE `name` LIKE '%HYDROMORPHONE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'HYDROXYUREA' , 'hydroxyUREA') WHERE `name` LIKE '%HYDROXYUREA%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'IBRUTINIB' , 'iBRUtinib') WHERE `name` LIKE '%IBRUTINIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'IMATINIB' , 'iMAtinib') WHERE `name` LIKE '%IMATINIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'INFLIXIMAB' , 'inFLIXimab') WHERE `name` LIKE '%INFLIXIMAB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'RITUXIMAB' , 'riTUXimab') WHERE `name` LIKE '%RITUXIMAB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'LAMIVUDINE' , 'lamiVUDine') WHERE `name` LIKE '%LAMIVUDINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'LAMOTRIGINE' , 'lamoTRIgine') WHERE `name` LIKE '%LAMOTRIGINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'MITOXANTRONE' , 'mitoXANTRONE') WHERE `name` LIKE '%MITOXANTRONE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'NILOTINIB' , 'niLOtinib') WHERE `name` LIKE '%NILOTINIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'NILUTAMIDE' , 'niLUTAmide') WHERE `name` LIKE '%NILUTAMIDE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'OBINUTUZUMAB' , 'oBINutuzumab') WHERE `name` LIKE '%OBINUTUZUMAB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'OFATUMUMAB' , 'oFAtumumab') WHERE `name` LIKE '%OFATUMUMAB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'PANITUMUMAB' , 'PANitumumab') WHERE `name` LIKE '%PANITUMUMAB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'PERTUZUMAB' , 'PERTuzumab') WHERE `name` LIKE '%PERTUZUMAB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'QUINIDINE' , 'quiNIDine') WHERE `name` LIKE '%QUINIDINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'QUININE' , 'quiNINE') WHERE `name` LIKE '%QUININE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'SAXAGLIPTIN' , 'sAXagliptin') WHERE `name` LIKE '%SAXAGLIPTIN%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'SORAFENIB' , 'SORAfenib') WHERE `name` LIKE '%SORAFENIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'SUNITINIB' , 'SUNItinib') WHERE `name` LIKE '%SUNITINIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'VANDETANIB' , 'vanDETanib') WHERE `name` LIKE '%VANDETANIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'VEMURAFENIB' , 'vemURAFenib') WHERE `name` LIKE '%VEMURAFENIB%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'VINBLASTINE' , 'vinBLAStine') WHERE `name` LIKE '%VINBLASTINE%' ;
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'VINCRISTINE' , 'vinCRIStine') WHERE `name` LIKE '%VINCRISTINE%' ;

UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, ' .' , ' 0.') WHERE `name` LIKE '% .%';
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, '.0 ' , ' ') WHERE `name` LIKE '%.0 %';
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, '.00 ' , ' ') WHERE `name` LIKE '%.00 %';
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, '.0MG' , 'MG') WHERE `name` LIKE '%.0MG%';
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, '.00MG' , 'MG') WHERE `name` LIKE '%.00MG%';
UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, '.0MCG' , 'MCG') WHERE `name` LIKE '%.0MCG%';

DELETE FROM `cd_drug_search` WHERE `name` LIKE 'AA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ABBOTT-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ACCEL-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ACH-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ACT %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ACT-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ACTI-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'AG-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'AJ-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ALTI-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'AMI-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'APO %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'APO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'AURO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'AVA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'BCI %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'BIO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'CCP-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'CO %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'DOM-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ECL-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'EURO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'FTP-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'GD-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'GEN-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'GLN-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'JAA %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'JAMP %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'JAMP-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'KYE-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'LIN-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'MANDA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'M-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'MAR-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'MDK-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'MED %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'MED-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'MINT-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'MYL-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'MYLAN-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'NAT-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'NEO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'NG %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'NOVO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'NRA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'NTP-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'NU-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PAT-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PENTA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PHARMA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PDP-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PHL-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PMS %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PMS-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PMSC-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PRIVA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PRO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'PRZ-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'Q-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'RAN-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'RATIO %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'RATIO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'RHO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'RIVA %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'RIVA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'SANDOZ %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'SANDOZ-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'SCHEINPHARM %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'SDZ %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'SIG-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'SEPTA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'SNS-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'SYN-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'TARO-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'TEVA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'TIS-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'TRIA-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'UNI-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'VAL-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'VAN-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ZYM-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE '%-ODAN %';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'ORB-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'RHOXAL-%';
DELETE FROM `cd_drug_search` WHERE `name` LIKE 'REDDY-%';

DELETE t1 FROM `cd_drug_search` t1
INNER  JOIN `cd_drug_search` t2
WHERE
    t1.id > t2.id AND
    t1.name = t2.name AND
    t2.`drug_code` IN (SELECT `drug_code` FROM `cd_therapeutic_class`);


DELETE t1 FROM `cd_drug_search` t1
INNER  JOIN `cd_drug_search` t2
WHERE
    t1.id > t2.id AND
    t1.name = t2.name AND
    t1.`drug_code` NOT IN (SELECT `drug_code` FROM `cd_therapeutic_class`);




