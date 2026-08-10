-- Provide useful measurement groups for new and upgraded installations.
-- Existing groups are preserved, and unavailable measurement types are skipped.

CREATE TEMPORARY TABLE `_defaultMeasurementGroup` (
    `name` VARCHAR(100) NOT NULL,
    `typeDisplayName` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`name`, `typeDisplayName`)
);

INSERT INTO `_defaultMeasurementGroup` (`name`, `typeDisplayName`) VALUES
    ('Vitals', 'BP'),
    ('Vitals', 'Heart Rate'),
    ('Vitals', 'RR'),
    ('Vitals', 'Temp'),
    ('Vitals', 'Oxygen Saturation'),
    ('Anthropometrics', 'HT'),
    ('Anthropometrics', 'WT'),
    ('Anthropometrics', 'Body Mass Index'),
    ('Anthropometrics', 'Waist'),
    ('Anthropometrics', 'Head circumference'),
    ('Diabetes Review', 'A1C'),
    ('Diabetes Review', 'Blood Glucose'),
    ('Diabetes Review', 'FBS'),
    ('Diabetes Review', 'Alb creat ratio'),
    ('Diabetes Review', 'EGFR'),
    ('Respiratory Review', 'Oxygen Saturation'),
    ('Respiratory Review', 'RR'),
    ('Respiratory Review', 'PEFR value'),
    ('Respiratory Review', 'Forced Expiratory Volume 1 Second'),
    ('Respiratory Review', 'Spirometry'),
    ('Respiratory Review', 'Smoking Status'),
    ('Mental Health Scores', 'PHQ9 Score'),
    ('Mental Health Scores', 'GAD7 Anxiety Score');

INSERT INTO `measurementGroup` (`name`, `typeDisplayName`)
SELECT DISTINCT defaults.`name`, defaults.`typeDisplayName`
FROM `_defaultMeasurementGroup` AS defaults
INNER JOIN `measurementType` AS mt
    ON mt.`typeDisplayName` = defaults.`typeDisplayName`
LEFT JOIN `measurementGroup` AS mg
    ON mg.`name` = defaults.`name`
    AND mg.`typeDisplayName` = defaults.`typeDisplayName`
WHERE mg.`id` IS NULL;

INSERT INTO `measurementGroupStyle` (`groupName`, `cssID`)
SELECT DISTINCT defaults.`name`, 0
FROM `_defaultMeasurementGroup` AS defaults
INNER JOIN `measurementGroup` AS mg
    ON mg.`name` = defaults.`name`
LEFT JOIN `measurementGroupStyle` AS mgs
    ON mgs.`groupName` = defaults.`name`
WHERE mgs.`groupID` IS NULL;

DROP TEMPORARY TABLE `_defaultMeasurementGroup`;

-- The old development placeholder should disappear only when it has no mappings.
DELETE mgs
FROM `measurementGroupStyle` AS mgs
LEFT JOIN `measurementGroup` AS mg
    ON mg.`name` = mgs.`groupName`
WHERE mgs.`groupName` = 'Test'
    AND mg.`id` IS NULL;
