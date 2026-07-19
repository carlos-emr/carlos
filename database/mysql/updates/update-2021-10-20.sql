CREATE TABLE IF NOT EXISTS `formBCAR2020` (
                                              `id` int(10) NOT NULL AUTO_INCREMENT,
                                              `demographic_no` int(10) NOT NULL,
                                              `provider_no` varchar(6) NOT NULL,
                                              `formCreated` date DEFAULT NULL,
                                              `formEdited` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              UNIQUE KEY `id` (`id`)
);
CREATE TABLE IF NOT EXISTS `formBCAR2020Data` (
                                                  `form_id` int(10) NOT NULL,
                                                  `provider_no` varchar(6) NOT NULL,
                                                  `page_no` int(1) NOT NULL,
                                                  `field` varchar(255) NOT NULL,
                                                  `val` varchar(255) NOT NULL DEFAULT '',
                                                  `field_edited` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                  UNIQUE KEY `form_data` (`form_id`,`page_no`,`field`)
);

CREATE TABLE IF NOT EXISTS `formBCAR2020Text` (
                                                  `form_id` int(10) NOT NULL,
                                                  `provider_no` varchar(6) NOT NULL,
                                                  `page_no` int(1) NOT NULL,
                                                  `field` varchar(255) NOT NULL,
                                                  `val` text NOT NULL,
                                                  `field_edited` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                  UNIQUE KEY `form_data` (`form_id`,`page_no`,`field`)
);

INSERT INTO encounterForm VALUES ('BC-AR 2020', '../form/formBCAR2020pg1.jsp?demographic_no=', 'formBCAR2020', 1);