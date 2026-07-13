INSERT INTO `encounterForm`(`form_name`, `form_value`, `form_table`, `hidden`) VALUES ('Rourke2017', '../form/formrourke2017complete.jsp?demographic_no=', 'formRourke2017', 38);
INSERT INTO `encounterForm`(`form_name`, `form_value`, `form_table`, `hidden`) VALUES ('Rourke2020', '../form/formrourke2020complete.jsp?demographic_no=', 'formRourke2020', 0);

CREATE TABLE IF NOT EXISTS `form_boolean_value` (
                                      `form_name` varchar(50) NOT NULL,
                                      `form_id` int(10) NOT NULL,
                                      `field_name` varchar(50) NOT NULL,
                                      `value` tinyint(1) DEFAULT NULL,
                                      PRIMARY KEY (`form_name`,`form_id`,`field_name`)
);