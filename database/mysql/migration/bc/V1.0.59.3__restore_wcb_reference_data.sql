-- Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
-- Restore the WorkSafeBC reference rows omitted from the Flyway baseline (#5062).
-- Source: database/mysql/oscardata_bc.sql at 80ce799ee564fcbe80e6dec9805f70cb789019ac^.
-- Match by the code the WCB form stores, not the legacy auto-increment ID:
-- adopted installs may assign different IDs or customize an existing code.
-- Leave existing rows and schema untouched; fail on SQL errors rather than ignore them.

-- 85 legacy wcb_bp_code rows; retain leading zeroes, notes and NULL values.
INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','CRANIAL','01100','BRAIN','Includes brain stem. Use for concussion or other intracranial injuries'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='01100');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','CRANIAL','01200','SCALP','Includes skin, hair'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='01200');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','CRANIAL','01300','SKULL','Includes occipital, parietal and temporal bones.'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='01300');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','CRANIAL','01800','CRANIAL REGION, MULTIPLE LOCATIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='01800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','EAR(S)','02000','EAR(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='02000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03100','FOREHEAD','Includes eyebrows'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03100');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03200','EYE(S)','Includes conjunctiva, cornea, eyeball, inside and outside of the eyelids, iris, lacrimal glands, lens, optic nerve, orbit, and retina'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03200');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03300','NOSE, EXTERNAL',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03300');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03310','NOSE/NASAL, INTERNAL LOCATION, OTHER',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03310');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03400','CHEEKS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03400');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03500','JAW/CHIN',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03500');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03610','LIP(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03610');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03620','TONGUE',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03620');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03630','TOOTH(TEETH)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03630');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'HEAD','FACE','03800','FACE, MULTIPLE LOCATIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='03800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'NECK','NECK','10009','NECK (SOFT TISSUES)','Includes throat, muscle, skin, subcutaneous tissue, veins and arteries'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='10009');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'NECK','NECK','10001','NECK, CERVICAL VERTEBRAE','Includes bony structures and cartilage'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='10001');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'NECK','NECK','12000','VOCAL CORD(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='12000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'NECK','NECK','13000','LARYNX',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='13000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'NECK','NECK','14000','LARYNGOPHARYNX',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='14000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'NECK','NECK','15000','PHARYNX',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='15000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'NECK','NECK','80001','NECK AND SHOULDER',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='80001');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','CHEST','22000','CHEST',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='22000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','CHEST','22200','ESOPHAGUS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='22200');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','CHEST','22300','HEART',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='22300');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','CHEST','22400','BRONCHUS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='22400');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','CHEST','22500','LUNG(S) PLEURA',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='22500');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','CHEST','22800','CHEST, MULTIPLE INTERNAL LOCATIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='22800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','BACK','23200','BACK, THORACIC REGION','Includes 12 vertebrae just below cervical vertebrae of the neck, the trapezius muscle, the Cervico-thoracic/Thoraco-lumbar regions'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='23200');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','BACK','23100','BACK, LUMBAR REGION','Includes 5 vertebrae in lower portion of back and Lumbo-sacral region'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='23100');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','BACK','23300','BACK, SACRAL REGION',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='23300');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','BACK','23400','BACK, COCCYGEAL REGION','Includes tail bone'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='23400');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','BACK','23800','BACK, MULTIPLE REGIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='23800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24000','ABDOMEN, EXTERNAL',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24200','STOMACH',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24200');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24300','SPLEEN',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24300');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24410','BLADDER',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24410');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24420','KIDNEY(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24420');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24491','URETER',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24491');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24520','INTESTINE, SMALL',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24520');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24530','INTESTINE, LARGE/COLON, RECTUM',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24530');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24610','LIVER',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24610');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24620','GALLBLADDER',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24620');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24630','PANCREAS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24630');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','ABDOMEN','24800','ABDOMEN, MULTIPLE INTERNAL LOCATIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='24800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25100','HIP(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25100');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25200','PELVIS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25200');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25300','BUTTOCK(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25300');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25400','GROIN',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25400');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25510','SCROTUM',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25510');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25520','PENIS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25520');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25530','GENITAL REGION, EXTERNAL FEMALE',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25530');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25610','PROSTATE',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25610');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25620','TESTIS(TESTES)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25620');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25630','OVARY(IES)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25630');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25640','UTERUS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25640');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'TRUNK','PELVIC REGION','25800','PELVIS, MULTIPLE REGIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='25800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','SHOULDER','21000','SHOULDER, INCLUDING CLAVICLE, SCAPULA','Includes clavicle/collar bone, humerus, scapula/shoulder blade, and shoulder girdle'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='21000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','ARM(S)','31100','ARM(S), UPPER',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='31100');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','ARM(S)','31200','ARM(S), ELBOW(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='31200');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','ARM(S)','31300','ARM(S), FOREARM(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='31300');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','ARM(S)','31800','ARM(S), MULTIPLE LOCATIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='31800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','WRIST(S)','32000','WRIST(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='32000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','HAND(S)','33000','HAND(S), EXCEPT FINGER(S)','Includes knuckles and the areas between the fingers'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='33000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','FINGER(S)','34000','FINGER(S). FINGERNAIL(S)','Includes distal phalanx (phalanges), medial phalanx (phalanges), and proximal'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='34000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'UPPER EXTREMITIES','FINGER(S)','34001','THUMB OR THUMB AND OTHER FINGER(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='34001');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','LEG(S)','41100','LEG(S), THIGH(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='41100');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','LEG(S)','41200','LEG(S), KNEE(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='41200');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','LEG(S)','41300','LEG(S), LOWER',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='41300');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','LEG(S)','41800','LEG(S), MULTIPLE LOCATIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='41800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','ANKLE(S)','42000','ANKLE(S)','Classifies the hinge joint area between the foot and the lower leg'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='42000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','FOOT(FEET)','43000','FOOT(FEET), OTHER EXCEPT HEEL(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='43000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','FOOT(FEET)','43230','FOOT(FEET), HEEL(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='43230');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','FOOT(FEET)','43800','FOOT(FEET), MULTIPLE LOCATIONS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='43800');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'LOWER EXTREMITIES','TOE(S)','44000','TOE(S). TOENAIL(S)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='44000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'BODY SYSTEMS','BODY SYSTEMS','50000','IMMUNE & ENDOCRINE SYSTEMS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='50000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'BODY SYSTEMS','BODY SYSTEMS','50001','CIRCULATORY SYSTEM',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='50001');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'BODY SYSTEMS','BODY SYSTEMS','50002','DIGESTIVE SYSTEM',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='50002');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'BODY SYSTEMS','BODY SYSTEMS','50004','GENITO-URINARY SYSTEM',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='50004');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'BODY SYSTEMS','BODY SYSTEMS','50005','MUSCULOSKELETAL SYS(JOINTS, TENDONS,...)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='50005');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'BODY SYSTEMS','BODY SYSTEMS','50006','NERVOUS SYSTM(FOR NERVOUS SHOCK,BRKDOWN)',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='50006');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'BODY SYSTEMS','BODY SYSTEMS','50007','RESPIRATORY SYSTEM',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='50007');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'OTHER','OTHER','91000','PROSTHETIC DEVICES','Artificial arm(s), leg(s), dentures, hearing aids, eye glasses or corrective lenses'
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='91000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'OTHER','OTHER','98000','OTHER BODY PARTS',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='98000');

INSERT INTO wcb_bp_code (level1, level2, code, level3, usagenote)
SELECT 'OTHER','OTHER','99990','NOT YET DIAGNOSED',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_bp_code WHERE code='99990');

-- 82 legacy wcb_noi_code rows; retain leading zeroes, notes and NULL values.
INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','BONES, NERVES, SPINAL CORD','01000','BONES, NERVES, SPINAL CORD, TRAUMA, OTHR		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='01000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','BONES, NERVES, SPINAL CORD','01100','DISLOCATIONS Displacement or dislocation of bone or cartilage.','Includes: subluxations, partial displacement and fractured or broken cartilage	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='01100');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','BONES, NERVES, SPINAL CORD','01200','FRACTURES','Includes Avulsion Fractures	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='01200');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','BONES, NERVES, SPINAL CORD','01300','SPINAL CORD, TRAUMATIC INJURIES		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='01300');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','BONES, NERVES, SPINAL CORD','01400','NERVES, TRAUMATIC INJ. (EX. SPINAL CORD)','Includes Traumatic Carpal Tunnel Syndrome	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='01400');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCLES JOINTS','02000','MUSCLES, TENDONS, JOINTS, OTHER INJURIES','Includes tears to Menisci, Ligaments, and Articular Cartilage	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='02000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCLES JOINTS','02100','SPRAINS, STRAINS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='02100');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCLES JOINTS','02101','ROTATOR CUFF TEAR, TRAUMATIC','If due to repetitive motion, use ROTATOR CUFF SYNDROME	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='02101');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','03000','WOUNDS, OTHER OPEN WOUNDS','Includes wounds with broken skin or outward opening, beyond the superficial skin surface	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='03000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','03110','AMPUTATIONS, FINGERTIP','Includes bone loss	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='03110');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','03190','AMPUTATIONS, EXCEPT FINGERTIP','Includes bone loss	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='03190');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','03200','BITES AND INSECT STINGS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='03200');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','03300','WOUNDS, AVULSIONS','Includes ripping or tearing away not involving bone, excludes Avulsion of joint capsule, ligament, muscle or tendon (Use MUSCLES, TENDONS, JOINTS,OTHER INJURIES	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='03300');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','03400','WOUNDS, CUTS & LACERATIONS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='03400');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','03700','WOUNDS, PUNCTURES		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='03700');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','04000','WOUNDS, OTHER CLOSED WOUNDS','Includes Blisters and Friction Burns	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='04000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','04100','WOUNDS, ABRASIONS & SCRATCHES','Includes Traumatic Conjunctivitis of the eye, use SURFACE WOUNDS, BRUISES, CONTUSIONS for closed wounds	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='04100');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','04300','WOUNDS, BRUISES & CONTUSIONS','Use for closed wounds	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='04300');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','WOUNDS','04400','FOREIGN BODIES (SUPERFCL SPLNTRS, CHIPS)','Includes foreign bodies in the eye	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='04400');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','BURNS','05100','BURNS, CHEMICAL		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='05100');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','BURNS','05200','BURNS, ELECTRICAL		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='05200');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','BURNS','05300','BURNS, SCALDS FROM HEAT		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='05300');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','INTRACRANIAL INJURIES','06000','INTRACRANIAL INJURIES, OTHER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='06000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','INTRACRANIAL INJURIES','06200','CONCUSSIONS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='06200');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','ENVIRONMENTAL TRAUMA','07000','ENVIRONMENTAL CONDITIONS, OTHER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='07000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','ENVIRONMENTAL TRAUMA','07110','FROSTBITE		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='07110');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','ENVIRONMENTAL TRAUMA','07120','HYPOTHERMIA		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='07120');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','ENVIRONMENTAL TRAUMA','07200','HEAT AND LIGHT EFFECTS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='07200');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','ENVIRONMENTAL TRAUMA','07300','AIR PRESSURE EFFECTS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='07300');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','ENVIRONMENTAL TRAUMA','12560','WELDER\'S FLASH		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='12560');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','ENVIRONMENTAL TRAUMA','12610','HEARING LOSS OR IMPAIRMENT, DEAFNESS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='12610');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17000','MUSCULOSKEL/CONNECT TIS. DISE. & DISOR.','Non traumatic Musculoskeletal system and connective tissue diseases and disorders	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17100','ARTHRITIS (ARTHROPATHIES & RELTD DISOR.)		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17100');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17210','SCIATICA (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17210');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17231','HERNIATED DISC','Including other disc pathologies (e.g. Degenerative, Disc Disease)		'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17231');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17293','RADICULITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17293');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17310','BURSITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17310');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17320','SYNOVITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17320');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17330','TENDONITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17330');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17340','TENOSYNOVITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17340');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17391','ROTATOR CUFF SYNDROME (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17391');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17393','EPICONDYLITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17393');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17394','CAPSULITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17394');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','MUSCULOSKELETAL DISORDERS','17901','FIBROMYALGIA, FIBROSITIS, MYOFASCIITIS Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='17901');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','OTHER TRAUMATIC INJURIES','09000','TRAUMATIC INJURIES & DISORDERS, OTHER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='09000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','OTHER TRAUMATIC INJURIES','09100','ASPHYXIATION,STRANGULATION,SUFFOCATION		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='09100');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','OTHER TRAUMATIC INJURIES','09200','DROWNINGS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='09200');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','OTHER TRAUMATIC INJURIES','09300','ELECTROCUTIONS, ELECTRIC SHOCKS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='09300');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','OTHER TRAUMATIC INJURIES','09500','POISONINGS AND TOXIC EFFECTS, OTHER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='09500');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','OTHER TRAUMATIC INJURIES','09600','TRAUMATIC COMPLICATIONS, OTHER','Complications peculiar to medical or surgical procedures or following traumatic injuries - includes anaphylactic shock and Post Traumatic Osteoarthritis	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='09600');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'TRAUMA & MUSCULOSKELETAL DISORDERS','OTHER TRAUMATIC INJURIES','09710','CRUSHING INJURIES		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='09710');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','DISEASE OF BLOOD & BLOOD FORMING ORGANS','11000','BLOOD & BLOOD FORMING ORGANS, DISEASES		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='11000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','NERVOUS SYSTEM DISEASES','12000','NERVOUS SYSTEM AND SENSE ORGANS DISEASES		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='12000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','NERVOUS SYSTEM DISEASES','12410','CARPAL TUNNEL SYNDROME (NOT TRAUMATIC)','For Traumatic Carpal Tunnel Syndrome use NERVES, TRAUMATC INJ. (EX.SPINAL CORD)	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='12410');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','NERVOUS SYSTEM DISEASES','12500','EYE DISORDERS, (NOT TRAUMATIC)','Do not use for Traumatic Injuries	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='12500');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','CIRCULATORY SYSTEM DISEASES','13000','CIRCULATORY SYSTEM DISEASES, OTHER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='13000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','CIRCULATORY SYSTEM DISEASES','13310','HEART ATTACK (MYOCARDIAL INFARCTION )		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='13310');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','CIRCULATORY SYSTEM DISEASES','13610','STROKE		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='13610');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','CIRCULATORY SYSTEM DISEASES','13710','RAYNAUDS SYNDROME,PHENOMENON:WHITE FINGR','Use for vibration induced white finger disease	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='13710');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14200','RESPIRATRY,DISEASES OF UPPER TRACT, OTHR		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14200');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14220','RESPIRATORY,CHRONIC COND. OF UPPER TRACT','Includes chronic sinusitis, pharyngitis	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14220');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14410','BRONCHITIS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14410');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14420','EMPHYSEMA		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14420');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14440','EXTRINSIC ALLERGIC ALVEOLITIS,PNEUMONITS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14440');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14490','CHRONIC OBSTRUCTV PULMONRY DISEASE,OTHER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14490');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14500','PNEUMOCONIOSES, OTHER','Includes bauxite fibrosis, graphite fibrosis and stannosis	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14500');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14510','PNEUMOCONIOSIS, COAL WORKERS','Includes Anthracosis, Black Lung, Miner\'s Asthma	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14510');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14520','ASBESTOSIS For Cancer use CANCERS, NEOPLASMS, AND TUMORS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14520');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14530','SILICOSIS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14530');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14900','RESPIRATORY SYSTEM DISEASES, OTHER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14900');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','RESPIRATORY SYSTEM DISEASES','14991','REACTV AIRWAY DYSFUNCTION SYND(R.A.D.S.)		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='14991');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','DIGESTIVE SYSTEM DISEASES','15000','DIGESTIVE SYSTEM DISEASES & DISOR, OTHER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='15000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','DIGESTIVE SYSTEM DISEASES','15390','HERNIA, OTHER Excludes herniated disc		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='15390');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','GENITOURINARY SYSTEM DISEASE & DISORDER','16000','GENITOURINARY SYSTEM DISEASE & DISORDER		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='16000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','DISORDERS: SKIN & SUBCUTANEOUS TISSUE','18200','DERMATITIS, OTHER','Classifies inflammation of the skin resulting from contact with allergens or irritant substances	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='18200');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','DISORDERS: SKIN & SUBCUTANEOUS TISSUE','18900','SKIN & SUBCUT. TISSUE, DISE & DISOR OTHR		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='18900');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'SYSTEMIC DISEASES & DISORDERS','OTHER SYSTEMIC DISEASES & DISORDERS','19000','SYSTEMIC DISEASES & DISORDERS, OTHER','Includes Scleroderma	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='19000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'INFECTIOUS & PARASITIC DISEASES','INFECTIOUS & PARASITIC DISEASES','29000','INFECTIOUS AND PARASITIC DISEASES','Classifies bacterial, viral, rickettsial, fungal and parasitic infections	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='29000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'CANCERS, NEOPLASMS, AND TUMORS','CANCERS, NEOPLASMS, AND TUMORS','30000','CANCERS, NEOPLASMS, AND TUMORS		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='30000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'PSYCHOLOGICAL DISORDERS','PSYCHOLOGICAL DISORDERS','52000','PSYCHOLOGICAL DISORDERS OR SYNDROMES		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='52000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'OTHER','OTHER','51000','DAMAGE OR LOSS OF PROSTHETIC DEVICES		',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='51000');

INSERT INTO wcb_noi_code (level1, level2, code, level3, usagenote)
SELECT 'OTHER','OTHER','99990','NOT YET DIAGNOSED','Describe symptoms in diagnosis	'
WHERE NOT EXISTS (SELECT 1 FROM wcb_noi_code WHERE code='99990');

-- 4 legacy wcb_side rows; retain leading zeroes, notes and NULL values.
INSERT INTO wcb_side (sidetype, sidedesc, usagenote)
SELECT 'N','Not Applicable','Use for body systems, a major body part (i.e. heart, stomach) or multiple/other parts'
WHERE NOT EXISTS (SELECT 1 FROM wcb_side WHERE sidetype='N');

INSERT INTO wcb_side (sidetype, sidedesc, usagenote)
SELECT 'L','Left',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_side WHERE sidetype='L');

INSERT INTO wcb_side (sidetype, sidedesc, usagenote)
SELECT 'R','Right',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_side WHERE sidetype='R');

INSERT INTO wcb_side (sidetype, sidedesc, usagenote)
SELECT 'B','Left and Right',NULL
WHERE NOT EXISTS (SELECT 1 FROM wcb_side WHERE sidetype='B');
