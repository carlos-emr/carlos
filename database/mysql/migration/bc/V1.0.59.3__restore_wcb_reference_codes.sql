-- Restore the WorkSafeBC reference lists the BC WCB form reads (issue #5062).
--
-- bc/V1.0.6 recreated wcb_bp_code and wcb_noi_code (CREATE TABLE IF NOT EXISTS) but inserted no rows,
-- and no migration seeds wcb_side. On a Flyway-built BC install the WCB form's Side list
-- (viewformwcb, #w_side) therefore had no options, and the Body Part and Nature of Injury lookups
-- (billing/CA/BC/support/BodyPart and NatureInjury) listed nothing for any search.
--
-- The rows are the ones the retired legacy build loaded from database/mysql/oscardata_bc.sql
-- (removed in 80ce799ee5; taken from its parent 8bcc3931bc): 85 body parts, 82 natures of injury
-- and the four sides. Trailing tabs that padded some legacy descriptions are trimmed; codes and
-- wording are otherwise unchanged.
--
-- Installs created before the legacy scripts were retired may already hold these rows, and a clinic
-- may have edited them, so only MISSING rows are added; nothing is updated or deleted:
--   * a body part or nature of injury is missing when no row carries its WorkSafeBC code. It keeps
--     its legacy row number when that number is free, and otherwise takes the next one;
--   * a side is missing when no row carries its side type.
-- Re-running the statements adds nothing.

CREATE TEMPORARY TABLE `_carlos_wcb_bp_code_seed` (
    `seed_no` INT NOT NULL,
    `level1` VARCHAR(100) NOT NULL,
    `level2` VARCHAR(100) NOT NULL,
    `code` VARCHAR(5) NOT NULL,
    `level3` VARCHAR(255) NOT NULL,
    `usagenote` TEXT NULL,
    PRIMARY KEY (`seed_no`)
);

CREATE TEMPORARY TABLE `_carlos_wcb_noi_code_seed` LIKE `_carlos_wcb_bp_code_seed`;

CREATE TEMPORARY TABLE `_carlos_wcb_side_seed` (
    `sidetype` CHAR(2) NOT NULL,
    `sidedesc` VARCHAR(30) NOT NULL,
    `usagenote` VARCHAR(255) NULL,
    PRIMARY KEY (`sidetype`)
);

-- wcb_bp_code: 85 rows
INSERT INTO `_carlos_wcb_bp_code_seed` (`seed_no`, `level1`, `level2`, `code`, `level3`, `usagenote`) VALUES
    (1, 'HEAD', 'CRANIAL', '01100', 'BRAIN', 'Includes brain stem. Use for concussion or other intracranial injuries'),
    (2, 'HEAD', 'CRANIAL', '01200', 'SCALP', 'Includes skin, hair'),
    (3, 'HEAD', 'CRANIAL', '01300', 'SKULL', 'Includes occipital, parietal and temporal bones.'),
    (4, 'HEAD', 'CRANIAL', '01800', 'CRANIAL REGION, MULTIPLE LOCATIONS', NULL),
    (5, 'HEAD', 'EAR(S)', '02000', 'EAR(S)', NULL),
    (6, 'HEAD', 'FACE', '03100', 'FOREHEAD', 'Includes eyebrows'),
    (7, 'HEAD', 'FACE', '03200', 'EYE(S)', 'Includes conjunctiva, cornea, eyeball, inside and outside of the eyelids, iris, lacrimal glands, lens, optic nerve, orbit, and retina'),
    (8, 'HEAD', 'FACE', '03300', 'NOSE, EXTERNAL', NULL),
    (9, 'HEAD', 'FACE', '03310', 'NOSE/NASAL, INTERNAL LOCATION, OTHER', NULL),
    (10, 'HEAD', 'FACE', '03400', 'CHEEKS', NULL),
    (11, 'HEAD', 'FACE', '03500', 'JAW/CHIN', NULL),
    (12, 'HEAD', 'FACE', '03610', 'LIP(S)', NULL),
    (13, 'HEAD', 'FACE', '03620', 'TONGUE', NULL),
    (14, 'HEAD', 'FACE', '03630', 'TOOTH(TEETH)', NULL),
    (15, 'HEAD', 'FACE', '03800', 'FACE, MULTIPLE LOCATIONS', NULL),
    (16, 'NECK', 'NECK', '10009', 'NECK (SOFT TISSUES)', 'Includes throat, muscle, skin, subcutaneous tissue, veins and arteries'),
    (17, 'NECK', 'NECK', '10001', 'NECK, CERVICAL VERTEBRAE', 'Includes bony structures and cartilage'),
    (18, 'NECK', 'NECK', '12000', 'VOCAL CORD(S)', NULL),
    (19, 'NECK', 'NECK', '13000', 'LARYNX', NULL),
    (20, 'NECK', 'NECK', '14000', 'LARYNGOPHARYNX', NULL),
    (21, 'NECK', 'NECK', '15000', 'PHARYNX', NULL),
    (22, 'NECK', 'NECK', '80001', 'NECK AND SHOULDER', NULL),
    (23, 'TRUNK', 'CHEST', '22000', 'CHEST', NULL),
    (24, 'TRUNK', 'CHEST', '22200', 'ESOPHAGUS', NULL),
    (25, 'TRUNK', 'CHEST', '22300', 'HEART', NULL),
    (26, 'TRUNK', 'CHEST', '22400', 'BRONCHUS', NULL),
    (27, 'TRUNK', 'CHEST', '22500', 'LUNG(S) PLEURA', NULL),
    (28, 'TRUNK', 'CHEST', '22800', 'CHEST, MULTIPLE INTERNAL LOCATIONS', NULL),
    (29, 'TRUNK', 'BACK', '23200', 'BACK, THORACIC REGION', 'Includes 12 vertebrae just below cervical vertebrae of the neck, the trapezius muscle, the Cervico-thoracic/Thoraco-lumbar regions'),
    (30, 'TRUNK', 'BACK', '23100', 'BACK, LUMBAR REGION', 'Includes 5 vertebrae in lower portion of back and Lumbo-sacral region'),
    (31, 'TRUNK', 'BACK', '23300', 'BACK, SACRAL REGION', NULL),
    (32, 'TRUNK', 'BACK', '23400', 'BACK, COCCYGEAL REGION', 'Includes tail bone'),
    (33, 'TRUNK', 'BACK', '23800', 'BACK, MULTIPLE REGIONS', NULL),
    (34, 'TRUNK', 'ABDOMEN', '24000', 'ABDOMEN, EXTERNAL', NULL),
    (35, 'TRUNK', 'ABDOMEN', '24200', 'STOMACH', NULL),
    (36, 'TRUNK', 'ABDOMEN', '24300', 'SPLEEN', NULL),
    (37, 'TRUNK', 'ABDOMEN', '24410', 'BLADDER', NULL),
    (38, 'TRUNK', 'ABDOMEN', '24420', 'KIDNEY(S)', NULL),
    (39, 'TRUNK', 'ABDOMEN', '24491', 'URETER', NULL),
    (40, 'TRUNK', 'ABDOMEN', '24520', 'INTESTINE, SMALL', NULL),
    (41, 'TRUNK', 'ABDOMEN', '24530', 'INTESTINE, LARGE/COLON, RECTUM', NULL),
    (42, 'TRUNK', 'ABDOMEN', '24610', 'LIVER', NULL),
    (43, 'TRUNK', 'ABDOMEN', '24620', 'GALLBLADDER', NULL),
    (44, 'TRUNK', 'ABDOMEN', '24630', 'PANCREAS', NULL),
    (45, 'TRUNK', 'ABDOMEN', '24800', 'ABDOMEN, MULTIPLE INTERNAL LOCATIONS', NULL),
    (46, 'TRUNK', 'PELVIC REGION', '25100', 'HIP(S)', NULL),
    (47, 'TRUNK', 'PELVIC REGION', '25200', 'PELVIS', NULL),
    (48, 'TRUNK', 'PELVIC REGION', '25300', 'BUTTOCK(S)', NULL),
    (49, 'TRUNK', 'PELVIC REGION', '25400', 'GROIN', NULL),
    (50, 'TRUNK', 'PELVIC REGION', '25510', 'SCROTUM', NULL),
    (51, 'TRUNK', 'PELVIC REGION', '25520', 'PENIS', NULL),
    (52, 'TRUNK', 'PELVIC REGION', '25530', 'GENITAL REGION, EXTERNAL FEMALE', NULL),
    (53, 'TRUNK', 'PELVIC REGION', '25610', 'PROSTATE', NULL),
    (54, 'TRUNK', 'PELVIC REGION', '25620', 'TESTIS(TESTES)', NULL),
    (55, 'TRUNK', 'PELVIC REGION', '25630', 'OVARY(IES)', NULL),
    (56, 'TRUNK', 'PELVIC REGION', '25640', 'UTERUS', NULL),
    (57, 'TRUNK', 'PELVIC REGION', '25800', 'PELVIS, MULTIPLE REGIONS', NULL),
    (58, 'UPPER EXTREMITIES', 'SHOULDER', '21000', 'SHOULDER, INCLUDING CLAVICLE, SCAPULA', 'Includes clavicle/collar bone, humerus, scapula/shoulder blade, and shoulder girdle'),
    (59, 'UPPER EXTREMITIES', 'ARM(S)', '31100', 'ARM(S), UPPER', NULL),
    (60, 'UPPER EXTREMITIES', 'ARM(S)', '31200', 'ARM(S), ELBOW(S)', NULL),
    (61, 'UPPER EXTREMITIES', 'ARM(S)', '31300', 'ARM(S), FOREARM(S)', NULL),
    (62, 'UPPER EXTREMITIES', 'ARM(S)', '31800', 'ARM(S), MULTIPLE LOCATIONS', NULL),
    (63, 'UPPER EXTREMITIES', 'WRIST(S)', '32000', 'WRIST(S)', NULL),
    (64, 'UPPER EXTREMITIES', 'HAND(S)', '33000', 'HAND(S), EXCEPT FINGER(S)', 'Includes knuckles and the areas between the fingers'),
    (65, 'UPPER EXTREMITIES', 'FINGER(S)', '34000', 'FINGER(S). FINGERNAIL(S)', 'Includes distal phalanx (phalanges), medial phalanx (phalanges), and proximal'),
    (66, 'UPPER EXTREMITIES', 'FINGER(S)', '34001', 'THUMB OR THUMB AND OTHER FINGER(S)', NULL),
    (67, 'LOWER EXTREMITIES', 'LEG(S)', '41100', 'LEG(S), THIGH(S)', NULL),
    (68, 'LOWER EXTREMITIES', 'LEG(S)', '41200', 'LEG(S), KNEE(S)', NULL),
    (69, 'LOWER EXTREMITIES', 'LEG(S)', '41300', 'LEG(S), LOWER', NULL),
    (70, 'LOWER EXTREMITIES', 'LEG(S)', '41800', 'LEG(S), MULTIPLE LOCATIONS', NULL),
    (71, 'LOWER EXTREMITIES', 'ANKLE(S)', '42000', 'ANKLE(S)', 'Classifies the hinge joint area between the foot and the lower leg'),
    (72, 'LOWER EXTREMITIES', 'FOOT(FEET)', '43000', 'FOOT(FEET), OTHER EXCEPT HEEL(S)', NULL),
    (73, 'LOWER EXTREMITIES', 'FOOT(FEET)', '43230', 'FOOT(FEET), HEEL(S)', NULL),
    (74, 'LOWER EXTREMITIES', 'FOOT(FEET)', '43800', 'FOOT(FEET), MULTIPLE LOCATIONS', NULL),
    (75, 'LOWER EXTREMITIES', 'TOE(S)', '44000', 'TOE(S). TOENAIL(S)', NULL),
    (76, 'BODY SYSTEMS', 'BODY SYSTEMS', '50000', 'IMMUNE & ENDOCRINE SYSTEMS', NULL),
    (77, 'BODY SYSTEMS', 'BODY SYSTEMS', '50001', 'CIRCULATORY SYSTEM', NULL),
    (78, 'BODY SYSTEMS', 'BODY SYSTEMS', '50002', 'DIGESTIVE SYSTEM', NULL),
    (79, 'BODY SYSTEMS', 'BODY SYSTEMS', '50004', 'GENITO-URINARY SYSTEM', NULL),
    (80, 'BODY SYSTEMS', 'BODY SYSTEMS', '50005', 'MUSCULOSKELETAL SYS(JOINTS, TENDONS,...)', NULL),
    (81, 'BODY SYSTEMS', 'BODY SYSTEMS', '50006', 'NERVOUS SYSTM(FOR NERVOUS SHOCK,BRKDOWN)', NULL),
    (82, 'BODY SYSTEMS', 'BODY SYSTEMS', '50007', 'RESPIRATORY SYSTEM', NULL),
    (83, 'OTHER', 'OTHER', '91000', 'PROSTHETIC DEVICES', 'Artificial arm(s), leg(s), dentures, hearing aids, eye glasses or corrective lenses'),
    (84, 'OTHER', 'OTHER', '98000', 'OTHER BODY PARTS', NULL),
    (85, 'OTHER', 'OTHER', '99990', 'NOT YET DIAGNOSED', NULL);

-- wcb_noi_code: 82 rows
INSERT INTO `_carlos_wcb_noi_code_seed` (`seed_no`, `level1`, `level2`, `code`, `level3`, `usagenote`) VALUES
    (1, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'BONES, NERVES, SPINAL CORD', '01000', 'BONES, NERVES, SPINAL CORD, TRAUMA, OTHR', NULL),
    (2, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'BONES, NERVES, SPINAL CORD', '01100', 'DISLOCATIONS Displacement or dislocation of bone or cartilage.', 'Includes: subluxations, partial displacement and fractured or broken cartilage'),
    (3, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'BONES, NERVES, SPINAL CORD', '01200', 'FRACTURES', 'Includes Avulsion Fractures'),
    (4, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'BONES, NERVES, SPINAL CORD', '01300', 'SPINAL CORD, TRAUMATIC INJURIES', NULL),
    (5, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'BONES, NERVES, SPINAL CORD', '01400', 'NERVES, TRAUMATIC INJ. (EX. SPINAL CORD)', 'Includes Traumatic Carpal Tunnel Syndrome'),
    (6, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCLES JOINTS', '02000', 'MUSCLES, TENDONS, JOINTS, OTHER INJURIES', 'Includes tears to Menisci, Ligaments, and Articular Cartilage'),
    (7, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCLES JOINTS', '02100', 'SPRAINS, STRAINS', NULL),
    (8, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCLES JOINTS', '02101', 'ROTATOR CUFF TEAR, TRAUMATIC', 'If due to repetitive motion, use ROTATOR CUFF SYNDROME'),
    (9, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '03000', 'WOUNDS, OTHER OPEN WOUNDS', 'Includes wounds with broken skin or outward opening, beyond the superficial skin surface'),
    (10, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '03110', 'AMPUTATIONS, FINGERTIP', 'Includes bone loss'),
    (11, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '03190', 'AMPUTATIONS, EXCEPT FINGERTIP', 'Includes bone loss'),
    (12, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '03200', 'BITES AND INSECT STINGS', NULL),
    (13, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '03300', 'WOUNDS, AVULSIONS', 'Includes ripping or tearing away not involving bone, excludes Avulsion of joint capsule, ligament, muscle or tendon (Use MUSCLES, TENDONS, JOINTS,OTHER INJURIES'),
    (14, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '03400', 'WOUNDS, CUTS & LACERATIONS', NULL),
    (15, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '03700', 'WOUNDS, PUNCTURES', NULL),
    (16, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '04000', 'WOUNDS, OTHER CLOSED WOUNDS', 'Includes Blisters and Friction Burns'),
    (17, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '04100', 'WOUNDS, ABRASIONS & SCRATCHES', 'Includes Traumatic Conjunctivitis of the eye, use SURFACE WOUNDS, BRUISES, CONTUSIONS for closed wounds'),
    (18, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '04300', 'WOUNDS, BRUISES & CONTUSIONS', 'Use for closed wounds'),
    (19, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'WOUNDS', '04400', 'FOREIGN BODIES (SUPERFCL SPLNTRS, CHIPS)', 'Includes foreign bodies in the eye'),
    (20, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'BURNS', '05100', 'BURNS, CHEMICAL', NULL),
    (21, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'BURNS', '05200', 'BURNS, ELECTRICAL', NULL),
    (22, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'BURNS', '05300', 'BURNS, SCALDS FROM HEAT', NULL),
    (23, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'INTRACRANIAL INJURIES', '06000', 'INTRACRANIAL INJURIES, OTHER', NULL),
    (24, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'INTRACRANIAL INJURIES', '06200', 'CONCUSSIONS', NULL),
    (25, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'ENVIRONMENTAL TRAUMA', '07000', 'ENVIRONMENTAL CONDITIONS, OTHER', NULL),
    (26, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'ENVIRONMENTAL TRAUMA', '07110', 'FROSTBITE', NULL),
    (27, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'ENVIRONMENTAL TRAUMA', '07120', 'HYPOTHERMIA', NULL),
    (28, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'ENVIRONMENTAL TRAUMA', '07200', 'HEAT AND LIGHT EFFECTS', NULL),
    (29, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'ENVIRONMENTAL TRAUMA', '07300', 'AIR PRESSURE EFFECTS', NULL),
    (30, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'ENVIRONMENTAL TRAUMA', '12560', 'WELDER''S FLASH', NULL),
    (31, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'ENVIRONMENTAL TRAUMA', '12610', 'HEARING LOSS OR IMPAIRMENT, DEAFNESS', NULL),
    (32, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17000', 'MUSCULOSKEL/CONNECT TIS. DISE. & DISOR.', 'Non traumatic Musculoskeletal system and connective tissue diseases and disorders'),
    (33, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17100', 'ARTHRITIS (ARTHROPATHIES & RELTD DISOR.)', NULL),
    (34, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17210', 'SCIATICA (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (35, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17231', 'HERNIATED DISC', 'Including other disc pathologies (e.g. Degenerative, Disc Disease)'),
    (36, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17293', 'RADICULITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (37, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17310', 'BURSITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (38, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17320', 'SYNOVITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (39, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17330', 'TENDONITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (40, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17340', 'TENOSYNOVITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (41, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17391', 'ROTATOR CUFF SYNDROME (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (42, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17393', 'EPICONDYLITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (43, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17394', 'CAPSULITIS (NOT TRAUMATIC) Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (44, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'MUSCULOSKELETAL DISORDERS', '17901', 'FIBROMYALGIA, FIBROSITIS, MYOFASCIITIS Use MUSCLES, TENDONS, JOINTS, OTHER INJURIES for Traumas', NULL),
    (45, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'OTHER TRAUMATIC INJURIES', '09000', 'TRAUMATIC INJURIES & DISORDERS, OTHER', NULL),
    (46, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'OTHER TRAUMATIC INJURIES', '09100', 'ASPHYXIATION,STRANGULATION,SUFFOCATION', NULL),
    (47, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'OTHER TRAUMATIC INJURIES', '09200', 'DROWNINGS', NULL),
    (48, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'OTHER TRAUMATIC INJURIES', '09300', 'ELECTROCUTIONS, ELECTRIC SHOCKS', NULL),
    (49, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'OTHER TRAUMATIC INJURIES', '09500', 'POISONINGS AND TOXIC EFFECTS, OTHER', NULL),
    (50, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'OTHER TRAUMATIC INJURIES', '09600', 'TRAUMATIC COMPLICATIONS, OTHER', 'Complications peculiar to medical or surgical procedures or following traumatic injuries - includes anaphylactic shock and Post Traumatic Osteoarthritis'),
    (51, 'TRAUMA & MUSCULOSKELETAL DISORDERS', 'OTHER TRAUMATIC INJURIES', '09710', 'CRUSHING INJURIES', NULL),
    (52, 'SYSTEMIC DISEASES & DISORDERS', 'DISEASE OF BLOOD & BLOOD FORMING ORGANS', '11000', 'BLOOD & BLOOD FORMING ORGANS, DISEASES', NULL),
    (53, 'SYSTEMIC DISEASES & DISORDERS', 'NERVOUS SYSTEM DISEASES', '12000', 'NERVOUS SYSTEM AND SENSE ORGANS DISEASES', NULL),
    (54, 'SYSTEMIC DISEASES & DISORDERS', 'NERVOUS SYSTEM DISEASES', '12410', 'CARPAL TUNNEL SYNDROME (NOT TRAUMATIC)', 'For Traumatic Carpal Tunnel Syndrome use NERVES, TRAUMATC INJ. (EX.SPINAL CORD)'),
    (55, 'SYSTEMIC DISEASES & DISORDERS', 'NERVOUS SYSTEM DISEASES', '12500', 'EYE DISORDERS, (NOT TRAUMATIC)', 'Do not use for Traumatic Injuries'),
    (56, 'SYSTEMIC DISEASES & DISORDERS', 'CIRCULATORY SYSTEM DISEASES', '13000', 'CIRCULATORY SYSTEM DISEASES, OTHER', NULL),
    (57, 'SYSTEMIC DISEASES & DISORDERS', 'CIRCULATORY SYSTEM DISEASES', '13310', 'HEART ATTACK (MYOCARDIAL INFARCTION )', NULL),
    (58, 'SYSTEMIC DISEASES & DISORDERS', 'CIRCULATORY SYSTEM DISEASES', '13610', 'STROKE', NULL),
    (59, 'SYSTEMIC DISEASES & DISORDERS', 'CIRCULATORY SYSTEM DISEASES', '13710', 'RAYNAUDS SYNDROME,PHENOMENON:WHITE FINGR', 'Use for vibration induced white finger disease'),
    (60, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14200', 'RESPIRATRY,DISEASES OF UPPER TRACT, OTHR', NULL),
    (61, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14220', 'RESPIRATORY,CHRONIC COND. OF UPPER TRACT', 'Includes chronic sinusitis, pharyngitis'),
    (62, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14410', 'BRONCHITIS', NULL),
    (63, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14420', 'EMPHYSEMA', NULL),
    (64, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14440', 'EXTRINSIC ALLERGIC ALVEOLITIS,PNEUMONITS', NULL),
    (65, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14490', 'CHRONIC OBSTRUCTV PULMONRY DISEASE,OTHER', NULL),
    (66, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14500', 'PNEUMOCONIOSES, OTHER', 'Includes bauxite fibrosis, graphite fibrosis and stannosis'),
    (67, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14510', 'PNEUMOCONIOSIS, COAL WORKERS', 'Includes Anthracosis, Black Lung, Miner''s Asthma'),
    (68, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14520', 'ASBESTOSIS For Cancer use CANCERS, NEOPLASMS, AND TUMORS', NULL),
    (69, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14530', 'SILICOSIS', NULL),
    (70, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14900', 'RESPIRATORY SYSTEM DISEASES, OTHER', NULL),
    (71, 'SYSTEMIC DISEASES & DISORDERS', 'RESPIRATORY SYSTEM DISEASES', '14991', 'REACTV AIRWAY DYSFUNCTION SYND(R.A.D.S.)', NULL),
    (72, 'SYSTEMIC DISEASES & DISORDERS', 'DIGESTIVE SYSTEM DISEASES', '15000', 'DIGESTIVE SYSTEM DISEASES & DISOR, OTHER', NULL),
    (73, 'SYSTEMIC DISEASES & DISORDERS', 'DIGESTIVE SYSTEM DISEASES', '15390', 'HERNIA, OTHER Excludes herniated disc', NULL),
    (74, 'SYSTEMIC DISEASES & DISORDERS', 'GENITOURINARY SYSTEM DISEASE & DISORDER', '16000', 'GENITOURINARY SYSTEM DISEASE & DISORDER', NULL),
    (75, 'SYSTEMIC DISEASES & DISORDERS', 'DISORDERS: SKIN & SUBCUTANEOUS TISSUE', '18200', 'DERMATITIS, OTHER', 'Classifies inflammation of the skin resulting from contact with allergens or irritant substances'),
    (76, 'SYSTEMIC DISEASES & DISORDERS', 'DISORDERS: SKIN & SUBCUTANEOUS TISSUE', '18900', 'SKIN & SUBCUT. TISSUE, DISE & DISOR OTHR', NULL),
    (77, 'SYSTEMIC DISEASES & DISORDERS', 'OTHER SYSTEMIC DISEASES & DISORDERS', '19000', 'SYSTEMIC DISEASES & DISORDERS, OTHER', 'Includes Scleroderma'),
    (78, 'INFECTIOUS & PARASITIC DISEASES', 'INFECTIOUS & PARASITIC DISEASES', '29000', 'INFECTIOUS AND PARASITIC DISEASES', 'Classifies bacterial, viral, rickettsial, fungal and parasitic infections'),
    (79, 'CANCERS, NEOPLASMS, AND TUMORS', 'CANCERS, NEOPLASMS, AND TUMORS', '30000', 'CANCERS, NEOPLASMS, AND TUMORS', NULL),
    (80, 'PSYCHOLOGICAL DISORDERS', 'PSYCHOLOGICAL DISORDERS', '52000', 'PSYCHOLOGICAL DISORDERS OR SYNDROMES', NULL),
    (81, 'OTHER', 'OTHER', '51000', 'DAMAGE OR LOSS OF PROSTHETIC DEVICES', NULL),
    (82, 'OTHER', 'OTHER', '99990', 'NOT YET DIAGNOSED', 'Describe symptoms in diagnosis');

-- wcb_side: 4 rows
INSERT INTO `_carlos_wcb_side_seed` (`sidetype`, `sidedesc`, `usagenote`) VALUES
    ('N', 'Not Applicable', 'Use for body systems, a major body part (i.e. heart, stomach) or multiple/other parts'),
    ('L', 'Left', NULL),
    ('R', 'Right', NULL),
    ('B', 'Left and Right', NULL);

-- Body parts: first every missing code whose legacy row number is free, keeping that number ...
INSERT INTO `wcb_bp_code` (`wcb_bp_code_no`, `level1`, `level2`, `code`, `level3`, `usagenote`)
SELECT seed.`seed_no`, seed.`level1`, seed.`level2`, seed.`code`, seed.`level3`, seed.`usagenote`
FROM `_carlos_wcb_bp_code_seed` AS seed
WHERE NOT EXISTS (SELECT 1 FROM `wcb_bp_code` AS existing WHERE existing.`code` = seed.`code`)
  AND NOT EXISTS (SELECT 1 FROM `wcb_bp_code` AS existing WHERE existing.`wcb_bp_code_no` = seed.`seed_no`)
ORDER BY seed.`seed_no`;

-- ... then any code still missing (its number was taken by another row) under a new number.
INSERT INTO `wcb_bp_code` (`level1`, `level2`, `code`, `level3`, `usagenote`)
SELECT seed.`level1`, seed.`level2`, seed.`code`, seed.`level3`, seed.`usagenote`
FROM `_carlos_wcb_bp_code_seed` AS seed
WHERE NOT EXISTS (SELECT 1 FROM `wcb_bp_code` AS existing WHERE existing.`code` = seed.`code`)
ORDER BY seed.`seed_no`;

-- Natures of injury, the same way.
INSERT INTO `wcb_noi_code` (`wcb_noi_code_no`, `level1`, `level2`, `code`, `level3`, `usagenote`)
SELECT seed.`seed_no`, seed.`level1`, seed.`level2`, seed.`code`, seed.`level3`, seed.`usagenote`
FROM `_carlos_wcb_noi_code_seed` AS seed
WHERE NOT EXISTS (SELECT 1 FROM `wcb_noi_code` AS existing WHERE existing.`code` = seed.`code`)
  AND NOT EXISTS (SELECT 1 FROM `wcb_noi_code` AS existing WHERE existing.`wcb_noi_code_no` = seed.`seed_no`)
ORDER BY seed.`seed_no`;

INSERT INTO `wcb_noi_code` (`level1`, `level2`, `code`, `level3`, `usagenote`)
SELECT seed.`level1`, seed.`level2`, seed.`code`, seed.`level3`, seed.`usagenote`
FROM `_carlos_wcb_noi_code_seed` AS seed
WHERE NOT EXISTS (SELECT 1 FROM `wcb_noi_code` AS existing WHERE existing.`code` = seed.`code`)
ORDER BY seed.`seed_no`;

-- Sides, in the legacy order (N, L, R, B), which is the order the form lists them in.
INSERT INTO `wcb_side` (`sidetype`, `sidedesc`, `usagenote`)
SELECT seed.`sidetype`, seed.`sidedesc`, seed.`usagenote`
FROM `_carlos_wcb_side_seed` AS seed
WHERE NOT EXISTS (SELECT 1 FROM `wcb_side` AS existing WHERE existing.`sidetype` = seed.`sidetype`)
ORDER BY FIELD(seed.`sidetype`, 'N', 'L', 'R', 'B');

DROP TEMPORARY TABLE `_carlos_wcb_bp_code_seed`;
DROP TEMPORARY TABLE `_carlos_wcb_noi_code_seed`;
DROP TEMPORARY TABLE `_carlos_wcb_side_seed`;
