CREATE TABLE IF NOT EXISTS incomingLabRulesType (
  id int(10) NOT NULL AUTO_INCREMENT,
  forward_rule_id int(10),
  type VARCHAR(10) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT FOREIGN KEY (forward_rule_id) REFERENCES incomingLabRules (id) ON DELETE CASCADE ON UPDATE CASCADE
);

DELIMITER $$
DROP PROCEDURE IF EXISTS insertForwardTypes$$
CREATE PROCEDURE insertForwardTypes()
BEGIN
    DECLARE i INT;
    SET i = 1;
    WHILE i <= (SELECT MAX(id) FROM incomingLabRules) DO
            IF (SELECT id FROM incomingLabRules WHERE id = i) THEN
                INSERT INTO incomingLabRulesType (forward_rule_id, type) VALUES  (i, 'HL7'), (i, 'DOC'), (i, 'HRM');
            END IF;
            SET i = i + 1;
        END WHILE;
END$$
DELIMITER ;
CALL insertForwardTypes();
DROP PROCEDURE IF EXISTS insertForwardTypes;