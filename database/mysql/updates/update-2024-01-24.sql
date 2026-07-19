/* This create table is added because not all instances will have this table already in the DB*/
CREATE TABLE IF NOT EXISTS billing_preferences (
  id int(10) unsigned NOT NULL auto_increment,
  referral int(10) unsigned NOT NULL default '0',
  providerNo int(10) unsigned NOT NULL default '0',
  defaultPayeeNo varchar(11) NOT NULL default '0',
  PRIMARY KEY  (id)
) ;

ALTER TABLE `billing_preferences` MODIFY `defaultPayeeNo` VARCHAR(11) NOT NULL DEFAULT '0';
ALTER TABLE `SystemPreferences` MODIFY `value` varchar(255);