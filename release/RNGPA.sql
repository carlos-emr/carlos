-- Out of Basket codes for RNGPA

INSERT INTO `cssStyles` (`id`, `name`, `style`, `status`) VALUES (3, 'Out of Basket', 'color:#CC3300;', 'A')
  ON DUPLICATE KEY UPDATE `name`='Out of Basket', `style`='color:#CC3300;', `status`='A';

UPDATE `billingservice` SET `displaystyle`=3 WHERE `service_code` IN (
'C989A','E079A','E409A','E410A','E411A',
'G002A','G004A','G005A','G010A','G014A',
'G310A','G319A','G365A','G440A','G480A',
'G481A','G482A','G489A','G700A',
'K018A','K021A','K031A','K035A','K036A',
'K038A','K050A','K051A','K052A','K053A',
'K054A','K055A','K061A','K070A','K071A',
'K072A','K101A','K102A','K111A','K112A',
'K623A','K624A','K629A',
'P006A','P009A','P011A','P018A','P020A',
'P030A','P038A','P041A',
'Q003A','Q012A','Q013A','Q023A','Q040A',
'Q042A','Q043A','Q050A','Q053A','Q054A',
'Q055A','Q150A','Z555A'
);
