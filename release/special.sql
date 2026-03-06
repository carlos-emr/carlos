-- Special Payment codes for Primary Care Models eg FHN, FHO etc
-- Version 19.01

INSERT INTO `cssStyles` (`id`, `name`, `style`, `status`) VALUES (2, 'Special Payment', 'font-weight:bold;', 'A');

UPDATE `billingservice` SET `displaystyle`=2 WHERE `service_code` IN (
-- palliative care
'K023A','C882A','A945A','C945A','W882A','W872A','B998A',
-- prenatal
'P003A','P004A',
-- housecall
'A901A','A902A','B990A','B992A','B994A','B996A','A900A',
-- Long Term Care
'W010A','W102A','W002A','W008A','W121A','W003A','W001A',
'W109A','W107A','W777A','W903A','W004A','W104A',
-- serious mental illness
'Q021A','Q020A',
-- hospital special payment codes
'A933A','C002A','C003A','C004A','C005A','C006A','C007A',
'C008A','C009A','C010A','C121A','C122A','C123A','C124A',
'C142A','C143A','C777A','C905A','C933A','H001A','E082A','E083A',
-- OB special payment codes
'P006A','P007A','P009A','P018A','P020A','P038A','P041A'
);

