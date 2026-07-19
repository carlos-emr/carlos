CREATE TABLE `PreventionReport` (
  `id` int(10) NOT NULL AUTO_INCREMENT,
  `providerNo` varchar(6) DEFAULT NULL,
  `reportName` varchar(255) DEFAULT NULL,
  `json` text,
  `updateDate` datetime DEFAULT NULL,
  `createDate` datetime DEFAULT NULL,
  `active` tinyint(1) DEFAULT NULL,
  `archived` tinyint(1) DEFAULT NULL,
  `uuid` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
);
