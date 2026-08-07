--
--  Office procedures for FHN FHO and similar Ontario Primary Care Models
--  Bolds service codes that count towards performance bonus
--  Copyright Peter Hutten-Czapski 2012 released under the GPL v2
--

INSERT INTO `cssStyles` (`id`, `name`, `style`, `status`) VALUES (1, 'Office Procedures', 'font-weight:bold;', 'A')
  ON DUPLICATE KEY UPDATE `name`='Office Procedures', `style`='font-weight:bold;', `status`='A';


UPDATE `billingservice` SET `displaystyle`=1 WHERE `service_code` IN (
'G420A','Z101A','Z173A','Z174A','Z103A','Z106A','Z104A',
'Z114A','Z118A','Z116A','Z113A',
'Z156A','Z157A','Z158A','Z159A','Z160A','Z161A','Z162A',
'Z163A','Z164A','Z166A','Z167A','Z168A','Z169A','Z170A','Z171A',
'Z122A','Z123A','Z124A','Z125A','Z126A','Z127A','Z096A',
'R048A','R049A','R050A','R094A','R040A','R041A',
'R018A','R019A','R020A','R031A','R032A','R033A',
'Z314A','Z315A','Z316A','G370A','G371A',
'F004A','F005A','E558A','F006A','F008A','F009A','E504A',
'F012A','F013A','F102A','F016A','F017A','F018A',
'D001A','E576A','D004A','E577A','D007A','D012A',
'Z200A','Z201A','Z202A','Z203A','Z204A','Z211A','Z213A',
'Z154A','Z175A','Z177A','Z179A','Z190A','Z191A','Z192A',
'Z110A','Z128A','Z129A','Z130A','Z131A','Z117A','Z141A','Z139A',
'Z515A','Z567A','Z527A','Z547A','Z528A','Z580A','Z555A',
'E740A','E741A','E747A','E705A','Z535A','Z536A',
'Z714A','Z733A','Z736A',
'Z847A','Z848A','Z845A','Z854A','Z874A','Z915A','Z904A',
'G378A','G361A','Z770A'
);

