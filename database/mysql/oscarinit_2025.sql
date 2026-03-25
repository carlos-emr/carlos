--
-- Table structure for table `LookupCodeValue`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `LookupCodeValue` (
  `code` varchar(255) NOT NULL,
  `prefix` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `parentCode` varchar(255) DEFAULT NULL,
  `active` bit(1) DEFAULT NULL,
  `buf1` varchar(255) DEFAULT NULL,
  `codeTree` varchar(255) DEFAULT NULL,
  `codecsv` varchar(255) DEFAULT NULL,
  `orderByIndex` int(11) DEFAULT NULL,
  PRIMARY KEY (`code`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `formBPMH`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `formBPMH` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `demographic_no` int(10) NOT NULL,
  `provider_no` int(10) NOT NULL,
  `formCreated` date DEFAULT NULL,
  `formEdited` date DEFAULT NULL,
  `familyDrName` varchar(55) DEFAULT NULL,
  `familyDrPhone` varchar(15) DEFAULT NULL,
  `familyDrFax` varchar(15) DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  `allergies` blob DEFAULT NULL,
  `drugs` blob DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `formintakea`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `formintakea` (
  `ID` bigint(11) NOT NULL AUTO_INCREMENT,
  `demographic_no` bigint(11) NOT NULL DEFAULT 0,
  `provider_no` bigint(11) DEFAULT 0,
  `formCreated` date DEFAULT NULL,
  `formEdited` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `assessDate` varchar(24) DEFAULT '',
  `assessStartTime` varchar(24) DEFAULT '',
  `enterSeatonDate` varchar(24) DEFAULT '',
  `cbox_newClient` char(1) DEFAULT '',
  `cbox_dateOfReadmission` char(1) DEFAULT '',
  `dateOfReadmission` varchar(24) DEFAULT '',
  `cbox_isStatementRead` char(1) DEFAULT '',
  `clientSurname` varchar(50) DEFAULT '',
  `clientFirstName` varchar(50) DEFAULT '',
  `month` char(2) DEFAULT '',
  `day` char(2) DEFAULT '',
  `year` varchar(4) DEFAULT '',
  `cbox_speakEnglish` char(1) DEFAULT '',
  `cbox_speakFrench` char(1) DEFAULT '',
  `cbox_speakOther` char(1) DEFAULT '',
  `speakOther` varchar(36) DEFAULT '',
  `reasonToSeaton` varchar(255) DEFAULT '',
  `everAtSeatonBefore` varchar(24) DEFAULT NULL,
  `datesAtSeaton` varchar(70) DEFAULT '',
  `cbox_assistInHealth` char(1) DEFAULT '',
  `cbox_assistInIdentification` char(1) DEFAULT '',
  `cbox_assistInAddictions` char(1) DEFAULT '',
  `cbox_assistInHousing` char(1) DEFAULT '',
  `cbox_assistInEducation` char(1) DEFAULT '',
  `cbox_assistInEmployment` char(1) DEFAULT '',
  `cbox_assistInFinance` char(1) DEFAULT '',
  `cbox_assistInLegal` char(1) DEFAULT '',
  `cbox_assistInImmigration` char(1) DEFAULT '',
  `hasWhatID` varchar(120) DEFAULT '',
  `cbox_noID` char(1) DEFAULT '',
  `cbox_sinCard` char(1) DEFAULT '',
  `sinNum` varchar(24) DEFAULT '',
  `cbox_healthCard` char(1) DEFAULT '',
  `healthCardNum` varchar(24) DEFAULT '',
  `healthCardVer` char(2) DEFAULT '',
  `cbox_birthCertificate` char(1) DEFAULT '',
  `birthCertificateNum` varchar(24) DEFAULT '',
  `cbox_citzenshipCard` char(1) DEFAULT '',
  `citzenshipCardNum` varchar(24) DEFAULT '',
  `cbox_immigrant` char(1) DEFAULT '',
  `immigrantNum` varchar(24) DEFAULT '',
  `cbox_refugee` char(1) DEFAULT '',
  `refugeeNum` varchar(24) DEFAULT '',
  `cbox_otherID` char(1) DEFAULT '',
  `otherIdentification` varchar(70) DEFAULT '',
  `cbox_idFiled` char(1) DEFAULT '',
  `cbox_idNone` char(1) DEFAULT '',
  `commentsOnID` varchar(255) DEFAULT '',
  `cbox_OW` char(1) DEFAULT '',
  `cbox_ODSP` char(1) DEFAULT '',
  `cbox_WSIB` char(1) DEFAULT '',
  `cbox_Employment` char(1) DEFAULT '',
  `cbox_EI` char(1) DEFAULT '',
  `cbox_OAS` char(1) DEFAULT '',
  `cbox_CPP` char(1) DEFAULT '',
  `cbox_OtherIncome` char(1) DEFAULT '',
  `radio_onlineCheck` varchar(36) DEFAULT '',
  `radio_active` varchar(36) DEFAULT '',
  `cbox_noRecord` char(1) DEFAULT '',
  `lastIssueDate` varchar(24) DEFAULT '',
  `office` varchar(50) DEFAULT '',
  `workerNum` varchar(36) DEFAULT '',
  `amtReceived` varchar(9) DEFAULT '',
  `radio_hasDoctor` varchar(36) DEFAULT '',
  `doctorName` varchar(50) DEFAULT '',
  `doctorName2` varchar(50) DEFAULT '',
  `doctorPhone` varchar(24) DEFAULT '',
  `doctorPhoneExt` varchar(8) DEFAULT '',
  `doctorAddress` varchar(120) DEFAULT '',
  `radio_seeDoctor` varchar(36) DEFAULT '',
  `radio_healthIssue` varchar(36) DEFAULT '',
  `healthIssueDetails` varchar(255) DEFAULT '',
  `cbox_hasDiabetes` char(1) DEFAULT '',
  `cbox_insulin` char(1) DEFAULT '',
  `cbox_epilepsy` char(1) DEFAULT '',
  `cbox_bleeding` char(1) DEFAULT '',
  `cbox_hearingImpair` char(1) DEFAULT '',
  `cbox_visualImpair` char(1) DEFAULT '',
  `cbox_mobilityImpair` char(1) DEFAULT '',
  `mobilityImpair` varchar(255) DEFAULT '',
  `radio_otherHealthConcern` varchar(36) DEFAULT '',
  `otherHealthConerns` varchar(255) DEFAULT '',
  `radio_takeMedication` varchar(36) DEFAULT '',
  `namesOfMedication` varchar(120) DEFAULT '',
  `radio_helpObtainMedication` varchar(36) DEFAULT '',
  `helpObtainMedication` varchar(255) DEFAULT '',
  `radio_allergicToMedication` varchar(36) DEFAULT '',
  `allergicToMedicationName` varchar(255) DEFAULT '',
  `reactionToMedication` varchar(255) DEFAULT '',
  `radio_mentalHealthConcerns` varchar(36) DEFAULT '',
  `mentalHealthConcerns` varchar(255) DEFAULT '',
  `cbox_isStatement6Read` char(1) DEFAULT '',
  `frequencyOfSeeingDoctor` varchar(8) DEFAULT '',
  `cbox_visitWalkInClinic` char(1) DEFAULT '',
  `cbox_visitHealthCenter` char(1) DEFAULT '',
  `cbox_visitEmergencyRoom` char(1) DEFAULT '',
  `cbox_visitOthers` char(1) DEFAULT '',
  `otherSpecify` varchar(120) DEFAULT '',
  `cbox_visitHealthOffice` char(1) DEFAULT '',
  `radio_seeSameDoctor` varchar(36) DEFAULT '',
  `frequencyOfSeeingEmergencyRoomDoctor` varchar(8) DEFAULT '',
  `radio_didNotReceiveHealthCare` varchar(36) DEFAULT '',
  `cbox_treatPhysicalHealth` char(1) DEFAULT '',
  `cbox_treatMentalHealth` char(1) DEFAULT '',
  `cbox_regularCheckup` char(1) DEFAULT '',
  `cbox_treatOtherReasons` char(1) DEFAULT '',
  `treatOtherReasons` varchar(255) DEFAULT '',
  `cbox_treatInjury` char(1) DEFAULT '',
  `cbox_goToWalkInClinic` char(1) DEFAULT '',
  `cbox_goToHealthCenter` char(1) DEFAULT '',
  `cbox_goToEmergencyRoom` char(1) DEFAULT '',
  `cbox_goToOthers` char(1) DEFAULT '',
  `goToOthers` varchar(255) DEFAULT '',
  `cbox_HealthOffice` char(1) DEFAULT '',
  `radio_appmtSeeDoctorIn3Mths` varchar(36) DEFAULT '',
  `radio_needRegularDoctor` varchar(36) DEFAULT '',
  `radio_objectToRegularDoctorIn4Wks` varchar(36) DEFAULT '',
  `radio_rateOverallHealth` varchar(36) DEFAULT '',
  `radio_speakToResearcher` varchar(36) DEFAULT '',
  `contactName` varchar(70) DEFAULT '',
  `contactPhone` varchar(24) DEFAULT '',
  `contactAddress` varchar(255) DEFAULT '',
  `contactRelationship` varchar(120) DEFAULT '',
  `radio_hasMentalIllness` varchar(36) DEFAULT '',
  `radio_hasDrinkingProblem` varchar(36) DEFAULT '',
  `radio_hasDrugProblem` varchar(36) DEFAULT '',
  `radio_hasHealthProblem` varchar(36) DEFAULT '',
  `radio_hasBehaviorProblem` varchar(36) DEFAULT '',
  `radio_needSeatonService` varchar(36) DEFAULT '',
  `radio_seatonTour` varchar(36) DEFAULT '',
  `seatonNotToured` varchar(255) DEFAULT '',
  `radio_pamphletIssued` varchar(36) DEFAULT '',
  `pamphletNotIssued` varchar(255) DEFAULT '',
  `summary` varchar(255) DEFAULT '',
  `completedBy` varchar(120) DEFAULT '',
  `assessCompleteTime` varchar(36) DEFAULT '',
  `cbox_pamphletIssued` char(1) DEFAULT '',
  `cbox_hostel` char(1) DEFAULT '',
  `cbox_hostel_fusion_care` char(1) DEFAULT '',
  `cbox_rotaryClub` char(1) DEFAULT '',
  `cbox_annexHarm` char(1) DEFAULT '',
  `cbox_longTermProgram` char(1) DEFAULT '',
  `cbox_birchmountResidence` char(1) DEFAULT '',
  `cbox_oNeillHouse` char(1) DEFAULT '',
  `cbox_fortYork` char(1) DEFAULT '',
  `cbox_downsviewDells` char(1) DEFAULT '',
  `cbox_sharing` char(1) DEFAULT '',
  `radio_sex` varchar(36) DEFAULT NULL,
  `effDate` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table `formintakeb` DEPRECATED as it causes ERROR 1118
--


--
-- Table structure for table `formintakec`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `formintakec` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `cboxReferralByPolice` char(2) DEFAULT NULL,
  `radioGender` varchar(16) DEFAULT NULL,
  `cboxSocialServiceIssueOther` char(2) DEFAULT NULL,
  `cboxBase2ndIncomeNone` char(2) DEFAULT NULL,
  `cboxCurrHasSIn` char(2) DEFAULT NULL,
  `cboxReferralByFredVictorCentreOther` char(2) DEFAULT NULL,
  `cboxReferralByDetentionCenter` char(2) DEFAULT NULL,
  `currDescriptionOfHousing` varchar(255) DEFAULT NULL,
  `cboxBaseUseHealthBus` char(2) DEFAULT NULL,
  `dateOfHospitalization` varchar(10) DEFAULT NULL,
  `cboxHousingIssue` char(2) DEFAULT NULL,
  `cboxBaseHasCommunity` char(2) DEFAULT NULL,
  `cboxBaseHasFriends` char(2) DEFAULT NULL,
  `cboxRelocationExit` char(2) DEFAULT NULL,
  `cbox2ndPersonalityDisorder` char(2) DEFAULT NULL,
  `cboxBase2ndIncomeSocialAssistance` char(2) DEFAULT NULL,
  `cbox2ndMedicalMentalDisorder` char(2) DEFAULT NULL,
  `cboxReferralByCriminalJusticeSystem` char(2) DEFAULT NULL,
  `cboxThreatIssue` char(2) DEFAULT NULL,
  `cboxSubstanceAnxietyDisorder` char(2) DEFAULT NULL,
  `referralComment` varchar(255) DEFAULT NULL,
  `cboxBase2ndIncomeEi` char(2) DEFAULT NULL,
  `cboxCurrHasSupportUnknown` char(2) DEFAULT NULL,
  `cboxReferralBySelf` char(2) DEFAULT NULL,
  `cboxPhysicalIssueOther` char(2) DEFAULT NULL,
  `cboxCurrLivingWithSpouse` char(2) DEFAULT NULL,
  `cboxReferralByOtherInstitution` char(2) DEFAULT NULL,
  `cboxPhysicalHospitalization` char(2) DEFAULT NULL,
  `currWhyClientDoNotAccessSocialServices` varchar(255) DEFAULT NULL,
  `cboxFinancialIssue` char(2) DEFAULT NULL,
  `cboxIsolationIssueOther` char(2) DEFAULT NULL,
  `cboxCurrLivingWithUnknown` char(2) DEFAULT NULL,
  `cbox2ndIncomeOther` char(2) DEFAULT NULL,
  `cboxCurrHasUnknown` char(2) DEFAULT NULL,
  `cboxReferralByPhysician` char(2) DEFAULT NULL,
  `cbox2ndDisassociativeDisorder` char(2) DEFAULT NULL,
  `cboxImmigrationIssueOther` char(2) DEFAULT NULL,
  `entryDate` varchar(10) DEFAULT NULL,
  `admissionDate` varchar(10) DEFAULT NULL,
  `cboxCurrHasCommunity` char(2) DEFAULT NULL,
  `cboxDailyActivityIssue` char(2) DEFAULT NULL,
  `cboxCurrLivingWithParents` char(2) DEFAULT NULL,
  `cboxIdentificationIssueOther` char(2) DEFAULT NULL,
  `radioBaseEmploymentStatus` varchar(16) DEFAULT NULL,
  `radioCanadianBorn` varchar(16) DEFAULT NULL,
  `cboxBaseLivingWithNonrelatives` char(2) DEFAULT NULL,
  `cboxCurrIncomeMgmentDoNotNeedTrustee` char(2) DEFAULT NULL,
  `cboxBase2ndIncomeFamily` char(2) DEFAULT NULL,
  `radioCurrEmploymentStatus` varchar(16) DEFAULT NULL,
  `cboxSuicideExit` char(2) DEFAULT NULL,
  `cboxReferralByStreetNurseOther` char(2) DEFAULT NULL,
  `dayOfBirth` char(2) DEFAULT NULL,
  `cboxDualDisorder` char(2) DEFAULT NULL,
  `cbox2ndIncomeODSp` char(2) DEFAULT NULL,
  `cboxPsychiatricHospitalization` char(2) DEFAULT NULL,
  `cbox2ndSomatoformDisorder` char(2) DEFAULT NULL,
  `cboxPreAdmission` char(2) DEFAULT NULL,
  `cboxReferralByStreetHealthReceptionOther` char(2) DEFAULT NULL,
  `cbox2ndAnxietyDisorder` char(2) DEFAULT NULL,
  `radioCurrParticipateInEduction` varchar(16) DEFAULT NULL,
  `cbox2ndChildhoodDisorder` char(2) DEFAULT NULL,
  `cboxCurrIncomeMgmentHasTrustee` char(2) DEFAULT NULL,
  `radioCountryOfOrigin` varchar(16) DEFAULT NULL,
  `cboxOtherChronicIllness` char(2) DEFAULT NULL,
  `cboxMOHLTCDisorder` char(2) DEFAULT NULL,
  `cboxPTSd` char(2) DEFAULT NULL,
  `cboxBase2ndIncomeDisabilityAssistance` char(2) DEFAULT NULL,
  `lengthOfHospitalization` varchar(25) DEFAULT NULL,
  `radioCurrPrimaryIncomeSource` varchar(16) DEFAULT NULL,
  `baseWhyClientDoNotAccessSocialServices` varchar(255) DEFAULT NULL,
  `cbox2ndIncomeInformalOther` char(2) DEFAULT NULL,
  `cbox2ndIncomeEi` char(2) DEFAULT NULL,
  `cbox2ndGenderIdentityDisorder` char(2) DEFAULT NULL,
  `cboxBaseDoNotAccessHealthCare` char(2) DEFAULT NULL,
  `currSocialServiceClientAccesses` varchar(255) DEFAULT NULL,
  `cbox2ndIncomeUnknown` char(2) DEFAULT NULL,
  `radioBaseHealthCareAccess` varchar(16) DEFAULT NULL,
  `cboxBase2ndIncomeODSp` char(2) DEFAULT NULL,
  `cboxCurrUseWalkinClinic` char(2) DEFAULT NULL,
  `cboxReferralByPsychiatrists` char(2) DEFAULT NULL,
  `cboxReferralByMentalOrg` char(2) DEFAULT NULL,
  `radioBasePrimaryResidenceType` varchar(16) DEFAULT NULL,
  `cbox2ndIncomeDisabilityAssistance` char(2) DEFAULT NULL,
  `cboxAddictionIssue` char(2) DEFAULT NULL,
  `cboxBase2ndIncomePanhandlingOther` char(2) DEFAULT NULL,
  `cboxCurrIncomeMgmentUnknown` char(2) DEFAULT NULL,
  `cboxLegalIssue` char(2) DEFAULT NULL,
  `radioResistTreatment` varchar(16) DEFAULT NULL,
  `cbox2ndSchizophrenia` char(2) DEFAULT NULL,
  `radioBaseSocialServiceAccess` varchar(16) DEFAULT NULL,
  `radioCurrSocialServiceAccess` varchar(16) DEFAULT NULL,
  `radioBaseResidenceStatus` varchar(16) DEFAULT NULL,
  `cbox2ndIncomeEmployment` char(2) DEFAULT NULL,
  `cbox2ndAnxietyDisorderFromSubstance` char(2) DEFAULT NULL,
  `radioCurrPrimaryResidenceType` varchar(16) DEFAULT NULL,
  `radioCurrPrimaryIncomeSourceOther` varchar(16) DEFAULT NULL,
  `cboxReferralByProbation` char(2) DEFAULT NULL,
  `radioCurrLegalStatus` varchar(16) DEFAULT NULL,
  `cboxProblemsWithPolice2` char(2) DEFAULT NULL,
  `formCreated` date DEFAULT NULL,
  `cbox2ndIncomeSocialAssistance` char(2) DEFAULT NULL,
  `cboxBaseUseHospitalEmergency` char(2) DEFAULT NULL,
  `cboxReferralByPublic` char(2) DEFAULT NULL,
  `cboxReferralByHospital` char(2) DEFAULT NULL,
  `cboxBaseLivingWithSpousePlus` char(2) DEFAULT NULL,
  `cboxWithdrawalExit` char(2) DEFAULT NULL,
  `cboxCurrLivingWithSelf` char(2) DEFAULT NULL,
  `cbox2ndCognitiveDisorder` char(2) DEFAULT NULL,
  `cboxBaseIncomeMgmentUnknown` char(2) DEFAULT NULL,
  `cboxBaseHasHealthCard` char(2) DEFAULT NULL,
  `radioCurrHighestEductionLevel` varchar(16) DEFAULT NULL,
  `cboxReferralByPsychiatricHospital` char(2) DEFAULT NULL,
  `cboxCurrLivingWithNonrelatives` char(2) DEFAULT NULL,
  `cboxFamilyLawIssues1` char(2) DEFAULT NULL,
  `cboxProblemsWithPolice1` char(2) DEFAULT NULL,
  `cboxBaseHasRegularHealthProvider` char(2) DEFAULT NULL,
  `cboxFamilyLawIssues2` char(2) DEFAULT NULL,
  `cboxCurrUseShelterClinic` char(2) DEFAULT NULL,
  `clientNum` varchar(16) DEFAULT NULL,
  `cbox2ndImpulsiveDisorder` char(2) DEFAULT NULL,
  `radioIsAboriginal` varchar(16) DEFAULT NULL,
  `baseSocialServiceClientAccesses` varchar(255) DEFAULT NULL,
  `cboxBase2ndIncomeOther` char(2) DEFAULT NULL,
  `cboxBaseIncomeMgmentNeedsTrusteeButDoNotWant` char(2) DEFAULT NULL,
  `cbox2ndSleepDisorder` char(2) DEFAULT NULL,
  `cboxBaseHasNonStatus` char(2) DEFAULT NULL,
  `cboxHealthCareIssueOther` char(2) DEFAULT NULL,
  `radioCurrHealthCareAccess` varchar(16) DEFAULT NULL,
  `cboxBaseHasCertificate` char(2) DEFAULT NULL,
  `cboxBaseLivingWithSelf` char(2) DEFAULT NULL,
  `cboxCurrIncomeMgmentNeedsTrustee` char(2) DEFAULT NULL,
  `cboxBase2ndIncomeUnknown` char(2) DEFAULT NULL,
  `radioLanguageEnglish` varchar(16) DEFAULT NULL,
  `yearArrivedInCanada` varchar(16) DEFAULT NULL,
  `cboxDateOfBirthUnknown` char(2) DEFAULT NULL,
  `cboxOtherIssue` char(2) DEFAULT NULL,
  `cboxCurrHasHealthCard` char(2) DEFAULT NULL,
  `cboxCurrHasSomeone` char(2) DEFAULT NULL,
  `cboxCurrHasRegularHealthProvider` char(2) DEFAULT NULL,
  `cbox2ndDevelopmentalDisorder` char(2) DEFAULT NULL,
  `cboxBase2ndIncomeInformalOther` char(2) DEFAULT NULL,
  `cboxOCd` char(2) DEFAULT NULL,
  `cboxCurrUseHospitalEmergency` char(2) DEFAULT NULL,
  `cboxBaseLivingWithUnknown` char(2) DEFAULT NULL,
  `cbox2ndFactitiousDisorder` char(2) DEFAULT NULL,
  `cboxBaseHasSomeone` char(2) DEFAULT NULL,
  `cboxRelationalIssue` char(2) DEFAULT NULL,
  `cboxBaseHasNativeCard` char(2) DEFAULT NULL,
  `radioBaseLegalStatus` varchar(16) DEFAULT NULL,
  `cbox2ndIncomeFamily` char(2) DEFAULT NULL,
  `cboxCurrHasRelatives` char(2) DEFAULT NULL,
  `cboxCurrHasCertificate` char(2) DEFAULT NULL,
  `monthOfBirth` char(2) DEFAULT NULL,
  `cboxBaseHasSIn` char(2) DEFAULT NULL,
  `clientSurname` varchar(40) DEFAULT NULL,
  `cbox2ndAnxietyDisorderOCd` char(2) DEFAULT NULL,
  `staffName` varchar(60) DEFAULT NULL,
  `cboxReferralByStreetIDWorkerOther` char(2) DEFAULT NULL,
  `cbox2ndSubstanceDisorder` char(2) DEFAULT NULL,
  `radioCurrNeedSocialServices` varchar(16) DEFAULT NULL,
  `radioBaseHighestEductionLevel` varchar(16) DEFAULT NULL,
  `cboxBaseHasSupportUnknown` char(2) DEFAULT NULL,
  `radioCurrResidenceStatus` varchar(16) DEFAULT NULL,
  `cboxBaseIncomeMgmentDoNotNeedTrustee` char(2) DEFAULT NULL,
  `countryOfOrigin` varchar(60) DEFAULT NULL,
  `cboxCurrHasFriends` char(2) DEFAULT NULL,
  `radioBasePrimaryIncomeSource` varchar(16) DEFAULT NULL,
  `cbox2ndAnxietyDisorderOther` char(2) DEFAULT NULL,
  `cboxEducationalIssue` char(2) DEFAULT NULL,
  `radioBasePrimaryIncomeSourceOther` varchar(16) DEFAULT NULL,
  `radioBaseParticipateInEduction` varchar(16) DEFAULT NULL,
  `cboxCurrLivingWithRelatives` char(2) DEFAULT NULL,
  `baseDescriptionOfHousing` varchar(255) DEFAULT NULL,
  `cboxReferralByCourt` char(2) DEFAULT NULL,
  `cbox2ndIncomePanhandlingOther` char(2) DEFAULT NULL,
  `cboxReferralByOtherAgency` char(2) DEFAULT NULL,
  `cboxCaseFile` char(2) DEFAULT NULL,
  `cboxCurrLivingWithChildren` char(2) DEFAULT NULL,
  `cboxReferralByMentalHealthWorker` char(2) DEFAULT NULL,
  `cboxCurrIncomeMgmentNeedsTrusteeButDoNotWant` char(2) DEFAULT NULL,
  `clientFirstName` varchar(40) DEFAULT NULL,
  `cboxBankingIssueOther` char(2) DEFAULT NULL,
  `cboxBase2ndIncomeEmployment` char(2) DEFAULT NULL,
  `cboxNAExit` char(2) DEFAULT NULL,
  `cbox2ndIncomeNone` char(2) DEFAULT NULL,
  `cboxNa` char(2) DEFAULT NULL,
  `cboxBaseHasRelatives` char(2) DEFAULT NULL,
  `cboxCurrHasNonStatus` char(2) DEFAULT NULL,
  `cboxEmploymentIssue` char(2) DEFAULT NULL,
  `yearOfBirth` varchar(4) DEFAULT NULL,
  `cboxBase2ndIncomePension` char(2) DEFAULT NULL,
  `cboxCurrLivingWithSpousePlus` char(2) DEFAULT NULL,
  `cboxPreferredLanguageUnknown` char(2) DEFAULT NULL,
  `cbox2ndAnxietyDisorderPSd` char(2) DEFAULT NULL,
  `cboxSexualAbuseIssue` char(2) DEFAULT NULL,
  `formEdited` datetime DEFAULT NULL,
  `cboxBaseUseShelterClinic` char(2) DEFAULT NULL,
  `cboxCurrAccessHealthCareUnknown` char(2) DEFAULT NULL,
  `cbox2ndIncomePension` char(2) DEFAULT NULL,
  `provider_no` bigint(20) DEFAULT NULL,
  `cboxCurrDoNotAccessHealthCare` char(2) DEFAULT NULL,
  `cboxCurrUseHealthBus` char(2) DEFAULT NULL,
  `cboxBaseLivingWithParents` char(2) DEFAULT NULL,
  `cboxBaseHasUnknown` char(2) DEFAULT NULL,
  `radioBaseNeedSocialServices` varchar(16) DEFAULT NULL,
  `cboxNoneListedIssue` char(2) DEFAULT NULL,
  `cbox2ndUnknown` char(2) DEFAULT NULL,
  `cboxReferralBySafeBeds` char(2) DEFAULT NULL,
  `cbox2ndAdjustDisorder` char(2) DEFAULT NULL,
  `cboxHospitalizationUnknown` char(2) DEFAULT NULL,
  `cboxConcurrentDisorder` char(2) DEFAULT NULL,
  `cbox2ndEatingDisorder` char(2) DEFAULT NULL,
  `cboxCompleteWithReferral` char(2) DEFAULT NULL,
  `cboxBaseUseWalkinClinic` char(2) DEFAULT NULL,
  `monthlyProgressReport` varchar(255) DEFAULT NULL,
  `cboxOtherAnxietyDisorder` char(2) DEFAULT NULL,
  `cboxBaseIncomeMgmentHasTrustee` char(2) DEFAULT NULL,
  `demographic_no` bigint(20) NOT NULL DEFAULT 0,
  `cboxBaseIncomeMgmentNeedsTrustee` char(2) DEFAULT NULL,
  `radioRaceCaucasian` varchar(16) DEFAULT NULL,
  `cboxMentalIssue` char(2) DEFAULT NULL,
  `preferredLanguage` varchar(30) DEFAULT NULL,
  `cbox2ndMoodDisorder` char(2) DEFAULT NULL,
  `radioPrimaryDiagnosis` varchar(16) DEFAULT NULL,
  `cboxCurrHasNativeCard` char(2) DEFAULT NULL,
  `cboxBaseLivingWithChildren` char(2) DEFAULT NULL,
  `cboxBaseLivingWithSpouse` char(2) DEFAULT NULL,
  `radioTreatmentOrders` varchar(16) DEFAULT NULL,
  `cboxReferralByOtherPeople` char(2) DEFAULT NULL,
  `cboxBaseAccessHealthCareUnknown` char(2) DEFAULT NULL,
  `cboxCompleteWithoutReferral` char(2) DEFAULT NULL,
  `cboxDeathExit` char(2) DEFAULT NULL,
  `cboxBaseLivingWithRelatives` char(2) DEFAULT NULL,
  `currAddress` varchar(255) DEFAULT NULL,
  `currPhone` varchar(255) DEFAULT NULL,
  `pastAddresses` text DEFAULT NULL,
  `contactsInfo` text DEFAULT NULL,
  `ids` text DEFAULT NULL,
  `hospitalizations` text DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `phr_actions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `phr_actions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `datetime_queued` datetime DEFAULT NULL,
  `datetime_sent` datetime DEFAULT NULL,
  `sender_oscar` varchar(11) DEFAULT NULL,
  `sender_type` int(11) DEFAULT NULL,
  `sender_phr` varchar(255) DEFAULT NULL,
  `receiver_oscar` varchar(11) DEFAULT NULL,
  `receiver_type` int(11) DEFAULT NULL,
  `receiver_phr` varchar(255) DEFAULT NULL,
  `action_type` int(11) DEFAULT NULL,
  `phr_classification` varchar(250) DEFAULT NULL,
  `oscar_id` varchar(100) DEFAULT NULL,
  `phr_index` varchar(70) DEFAULT NULL,
  `doc_content` longtext DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `phr_type` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `phr_document_ext`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `phr_document_ext` (
  `phr_document_id` int(11) NOT NULL,
  `key` varchar(255) NOT NULL,
  `value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`phr_document_id`,`key`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `phr_documents`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `phr_documents` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `phr_classification` varchar(255) NOT NULL,
  `phr_index` varchar(70) DEFAULT NULL,
  `datetime_sent` datetime DEFAULT NULL,
  `datetime_exchanged` datetime DEFAULT NULL,
  `sender_oscar` varchar(11) DEFAULT NULL,
  `sender_type` int(11) DEFAULT NULL,
  `sender_phr` varchar(255) DEFAULT NULL,
  `receiver_oscar` varchar(11) DEFAULT NULL,
  `receiver_type` int(11) DEFAULT NULL,
  `receiver_phr` varchar(255) DEFAULT NULL,
  `doc_subject` varchar(255) DEFAULT NULL,
  `doc_content` longtext DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `sent` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Added constraints
--

ALTER TABLE `admission`
ADD CONSTRAINT `FK9uhg2nwhjhwrpr4nh21ucexyg`
FOREIGN KEY (`clientstatus_id`) REFERENCES `program_clientstatus` (`clientstatus_id`);


ALTER TABLE `casemgmt_issue`
ADD CONSTRAINT `FK9eqe58ou1bf43kamxcwh8dquh`
FOREIGN KEY (`issue_id`) REFERENCES `issue` (`issue_id`);


ALTER TABLE `casemgmt_issue_notes`
ADD CONSTRAINT `FKjaxhqj7ritpbh2p9ov3nk54t2` 
FOREIGN KEY (`id`) REFERENCES `casemgmt_issue` (`id`),
ADD CONSTRAINT `FKstesao78srug1a650gu46i33o` 
FOREIGN KEY (`note_id`) REFERENCES `casemgmt_note` (`note_id`);


ALTER TABLE `casemgmt_note_ext`
ADD CONSTRAINT `FKmgvgpkoky8rte7i7igeawga0y` 
FOREIGN KEY (`note_id`) REFERENCES `casemgmt_note` (`note_id`);


ALTER TABLE `default_role_access`
ADD KEY `FKin3oxi92mkmuf0we80ivhcd8a` (`role_id`),
ADD KEY `FKhs2yix1qvq9oqmbwsj2w2xgmu` (`access_id`),
ADD CONSTRAINT `FKhs2yix1qvq9oqmbwsj2w2xgmu` 
FOREIGN KEY (`access_id`) REFERENCES `access_type` (`access_id`),
ADD CONSTRAINT `FKin3oxi92mkmuf0we80ivhcd8a` 
FOREIGN KEY (`role_id`) REFERENCES `secRole` (`role_no`);


ALTER TABLE `demographic_merged`
ADD CONSTRAINT `FKqev6qw9c8jc2f3w40p524h5xd` 
FOREIGN KEY (`merged_to`) REFERENCES `demographic` (`demographic_no`);


ALTER TABLE `program_access_roles`
ADD CONSTRAINT `FKmwoclnncdj2il9ular8rro7nk` 
FOREIGN KEY (`id`) REFERENCES `program_access` (`id`);


ALTER TABLE `program_functional_user`
ADD CONSTRAINT `FK288fkfagl2hr1bns9b3qbf8fw` 
FOREIGN KEY (`user_type_id`) REFERENCES `functional_user_type` (`id`);


ALTER TABLE `program_provider`
ADD CONSTRAINT `FKfg27qvr98onqjiqn6wi3r5obw` 
FOREIGN KEY (`provider_no`) REFERENCES `provider` (`provider_no`);


ALTER TABLE `program_provider_team`
ADD CONSTRAINT `FKdnvemm0tthasq3eahbiuuefh` 
FOREIGN KEY (`elt`) REFERENCES `program_team` (`team_id`),
ADD CONSTRAINT `FKt1tgve2mege16gusn7prwq552` FOREIGN KEY (`id`) 
REFERENCES `program_provider` (`id`);


