-- drugref truncated 

--
-- Table structure for table `cd_active_ingredients`
--


CREATE TABLE IF NOT EXISTS `cd_active_ingredients` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `active_ingredient_code` int(11) DEFAULT NULL,
  `ingredient` varchar(240) DEFAULT NULL,
  `ingredient_supplied_ind` char(1) DEFAULT NULL,
  `strength` varchar(20) DEFAULT NULL,
  `strength_unit` varchar(40) DEFAULT NULL,
  `strength_type` varchar(40) DEFAULT NULL,
  `dosage_value` varchar(20) DEFAULT NULL,
  `base` char(1) DEFAULT NULL,
  `dosage_unit` varchar(40) DEFAULT NULL,
  `notes` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19592 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;


--
-- Table structure for table `cd_companies`
--


CREATE TABLE IF NOT EXISTS `cd_companies` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `mfr_code` varchar(5) DEFAULT NULL,
  `company_code` int(11) DEFAULT NULL,
  `company_name` varchar(80) DEFAULT NULL,
  `company_type` varchar(40) DEFAULT NULL,
  `address_mailing_flag` char(1) DEFAULT NULL,
  `address_billing_flag` char(1) DEFAULT NULL,
  `address_notification_flag` char(1) DEFAULT NULL,
  `address_other` varchar(20) DEFAULT NULL,
  `suite_number` varchar(20) DEFAULT NULL,
  `street_name` varchar(80) DEFAULT NULL,
  `city_name` varchar(60) DEFAULT NULL,
  `province` varchar(40) DEFAULT NULL,
  `country` varchar(40) DEFAULT NULL,
  `postal_code` varchar(20) DEFAULT NULL,
  `post_office_box` varchar(15) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `cd_drug_product`
--

CREATE TABLE IF NOT EXISTS `cd_drug_product` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `product_categorization` varchar(80) DEFAULT NULL,
  `class` varchar(40) DEFAULT NULL,
  `drug_identification_number` varchar(8) DEFAULT NULL,
  `brand_name` varchar(200) DEFAULT NULL,
  `descriptor` varchar(150) DEFAULT NULL,
  `pediatric_flag` char(1) DEFAULT NULL,
  `accession_number` varchar(5) DEFAULT NULL,
  `number_of_ais` varchar(10) DEFAULT NULL,
  `last_update_date` date DEFAULT NULL,
  `ai_group_no` varchar(10) DEFAULT NULL,
  `company_code` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `cd_drug_search`
--


CREATE TABLE IF NOT EXISTS `cd_drug_search` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` varchar(30) DEFAULT NULL,
  `category` int(11) DEFAULT NULL,
  `name` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `cd_drug_status`
--


CREATE TABLE IF NOT EXISTS `cd_drug_status` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `current_status_flag` char(1) DEFAULT NULL,
  `status` varchar(40) DEFAULT NULL,
  `history_date` date DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `cd_form`
--


CREATE TABLE IF NOT EXISTS `cd_form` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `pharm_cd_form_code` int(11) DEFAULT NULL,
  `pharmaceutical_cd_form` varchar(65) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `cd_inactive_products`
--


CREATE TABLE IF NOT EXISTS `cd_inactive_products` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `drug_identification_number` varchar(255) DEFAULT NULL,
  `brand_name` varchar(200) DEFAULT NULL,
  `history_date` date DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;


--
-- Table structure for table `cd_packaging`
--


CREATE TABLE IF NOT EXISTS `cd_packaging` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `upc` varchar(12) DEFAULT NULL,
  `package_size_unit` varchar(40) DEFAULT NULL,
  `package_type` varchar(40) DEFAULT NULL,
  `package_size` varchar(5) DEFAULT NULL,
  `product_inforation` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8132 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;


--
-- Table structure for table `cd_pharmaceutical_std`
--


CREATE TABLE IF NOT EXISTS `cd_pharmaceutical_std` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `pharmaceutical_std` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6523 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `cd_route`
--


CREATE TABLE IF NOT EXISTS `cd_route` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `route_of_administration_code` int(11) DEFAULT NULL,
  `route_of_administration` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16091 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;


--
-- Table structure for table `cd_schedule`
--


CREATE TABLE IF NOT EXISTS `cd_schedule` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `schedule` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13946 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `cd_therapeutic_class`
--


CREATE TABLE IF NOT EXISTS `cd_therapeutic_class` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `tc_atc_number` varchar(8) DEFAULT NULL,
  `tc_atc` varchar(120) DEFAULT NULL,
  `tc_ahfs_number` varchar(20) DEFAULT NULL,
  `tc_ahfs` varchar(80) DEFAULT NULL,
  `tc_atc_f` varchar(240) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `cd_veterinary_species`
--


CREATE TABLE IF NOT EXISTS `cd_veterinary_species` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `drug_code` int(11) DEFAULT NULL,
  `vet_species` varchar(80) DEFAULT NULL,
  `vet_sub_species` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;


--
-- Table structure for table `history`
--


CREATE TABLE IF NOT EXISTS `history` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `date_time` datetime DEFAULT NULL,
  `action` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Table structure for table `interactions`
--


CREATE TABLE IF NOT EXISTS `interactions` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `affectingatc` varchar(8) DEFAULT NULL,
  `affectedatc` varchar(8) DEFAULT NULL,
  `effect` char(1) DEFAULT NULL,
  `significance` char(1) DEFAULT NULL,
  `evidence` char(1) DEFAULT NULL,
  `comment` text DEFAULT NULL,
  `affectingdrug` text DEFAULT NULL,
  `affecteddrug` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UNQ_ATC_EFFECT` (`affectingatc`,`affectedatc`,`effect`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Table structure for table `link_generic_brand`
--


CREATE TABLE IF NOT EXISTS `link_generic_brand` (
  `pk_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `id` int(11) DEFAULT NULL,
  `drug_code` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`pk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;


--
-- Table structure for table `utility`
--


CREATE TABLE IF NOT EXISTS `utility` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `drug_identification_number` varchar(8) DEFAULT NULL,
  `brand_name` varchar(200) DEFAULT NULL,
  `descriptor` varchar(150) DEFAULT NULL,
  `tc_atc_number` varchar(8) DEFAULT NULL,
  `tc_atc` varchar(120) DEFAULT NULL,
  `tc_ahfs_number` varchar(20) DEFAULT NULL,
  `tc_ahfs` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2042 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
