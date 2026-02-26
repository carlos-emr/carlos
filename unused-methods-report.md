# CARLOS EMR - Unused Methods Report

**Generated**: 2026-02-26
**Analysis scope**: `src/main/java/` (3,638 Java files, 4,853 candidate methods extracted)
**Cross-referenced against**: 6,227 files (Java, JSP, XML, properties, JS, HBM, Struts config, Spring config)

## Methodology

1. Extracted all `public`/`protected` non-private method declarations from production Java source
2. Filtered out: constructors, getters/setters (`get*/set*/is*/has*`+uppercase), `@Override`, standard Object methods, Servlet/Struts lifecycle methods (`execute`, `doGet`, `doPost`, `validate`, etc.), JPA annotations (`@PrePersist`, `@PostLoad`, etc.), Spring/JUnit annotations, interface methods, abstract methods, enum methods
3. Built a global identifier index across all source, JSP, XML, config, and JS files
4. Flagged methods whose name appears ONLY in their declaring file (no external references AND no internal calls)
5. Deep verification pass: manually verified against Struts method-routing, SOAP/CXF endpoints, Drools convention-based invocation, iText PDF callbacks, JPA lifecycle annotations, Hibernate HBM mappings, Jackson/BeanUtils reflection, `<jsp:useBean>` directives, and builder patterns

## Summary

| Category | Count | Confidence |
|----------|-------|------------|
| HIGH confidence unused | 113 | Method name found nowhere else in codebase |
| MEDIUM confidence unused | 24 | Entity/form getters - may be used by Hibernate/Jackson reflection |
| Verified FALSE POSITIVES removed | 140 | JPA callbacks, Struts routing, SOAP endpoints, Drools, iText, REST |

---

## HIGH Confidence - Truly Unused Methods (113)

These methods have no references anywhere in the codebase (Java, JSP, XML, config files).

### Utility / Core Classes

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `io/github/carlos_emr/Misc.java` | Misc | `public Hashtable hashDefs(String[] names, String[] values)` | 78 |
| `io/github/carlos_emr/Misc.java` | Misc | `public String hashAttribString(Hashtable H)` | 547 |
| `io/github/carlos_emr/Misc.java` | Misc | `public Hashtable attribStringHash(String S)` | 569 |
| `io/github/carlos_emr/Misc.java` | Misc | `public String insertDecimalPoint(String input)` | 620 |
| `io/github/carlos_emr/MyDateFormat.java` | MyDateFormat | `public String formatMonthOrDay(String value)` | 95 |
| `io/github/carlos_emr/MyDateFormat.java` | MyDateFormat | `public String formatMonthDay(String pValue)` | 635 |
| `io/github/carlos_emr/SxmlMisc.java` | SxmlMisc | `public String replaceXmlContent(String str, String sTag, String eTag, String newVal)` | 190 |
| `io/github/carlos_emr/SxmlMisc.java` | SxmlMisc | `public String replaceOrAddXmlContent(String str, String sTag, String eTag, String newVal)` | 209 |
| `carlos/utility/AccumulatorMap.java` | AccumulatorMap | `public void addAccumulator(AccumulatorMap<K> accumulatorMap)` | 63 |
| `carlos/utility/LoggedInInfo.java` | LoggedInInfo | `public void removeLoggedInInfoFromSession(HttpSession session)` | 137 |
| `carlos/utility/OntarioMD.java` | OntarioMD | `public boolean showOntarioMDLink()` | 66 |
| `carlos/util/plugin/IsPropertiesOn.java` | IsPropertiesOn | `public boolean propertiesOff(String proName)` | 43 |

### PM Module / Case Management

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/PMmodule/model/ProgramAccess.java` | ProgramAccess | `public void addToRoles(Secrole role)` | 180 |
| `carlos/PMmodule/model/ProgramProvider.java` | ProgramProvider | `public void addToTeams(Object obj)` | 208 |
| `carlos/PMmodule/web/CdsForm4.java` | CdsForm4 | `public String renderSelectQuestion(boolean multiple, boolean dropDown, boolean forPrint, Integer cdsClientFormId, String question, List<CdsFormOption> options)` | 183 |
| `carlos/PMmodule/web/CdsForm4.java` | CdsForm4 | `public String renderNumbersAsSelectOptions(Integer cdsClientFormId, String question, int maxNumber)` | 239 |
| `carlos/PMmodule/web/CdsForm4.java` | CdsForm4 | `public String renderAsRadioOptions(Integer cdsClientFormId, String question, List<CdsFormOption> options, String defaultSelected)` | 268 |
| `carlos/PMmodule/web/CdsForm4.java` | CdsForm4 | `public String renderAsCheckBoxOptions(Integer cdsClientFormId, String question, List<CdsFormOption> options)` | 294 |
| `carlos/PMmodule/web/CdsForm4.java` | CdsForm4 | `public void addHospitalisationDay(Integer clientId, Calendar admissionDate, Calendar dischargeDate)` | 328 |
| `carlos/PMmodule/web/CdsForm4.java` | CdsForm4 | `public void deleteHospitalisationDay(Integer hospitalisationDayId)` | 336 |
| `carlos/PMmodule/web/CdsForm4Action.java` | CdsForm4Action | `public CdsClientForm createCdsClientForm(LoggedInInfo loggedInInfo, Integer admissionId, Integer clientId, Date initialContactDate, Date assessmentDate, boolean signed)` | 45 |
| `carlos/PMmodule/web/CdsForm4Action.java` | CdsForm4Action | `public void addCdsClientFormData(Integer cdsClientFormId, String question, String answer)` | 60 |
| `carlos/PMmodule/web/CreateAnonymousClientAction.java` | CreateAnonymousClientAction | `public Demographic generatePEClient(String creatorProviderNo, int programId)` | 76 |
| `carlos/casemgmt/web/CaseManagementEntry2Action.java` | CaseManagementEntry2Action | `protected boolean inCaseIssue(Issue iss, List<CaseManagementIssue> issues)` | 3273 |

### Appointment / Scheduling

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/appointment/search/SearchConfig.java` | SearchConfig | `public void moveBooking2Provider()` | 101 |
| `carlos/appointment/search/SearchConfig.java` | SearchConfig | `public void genSecKey()` | 614 |
| `carlos/appointment/search/filters/MultiUnitFilter.java` | MultiUnitFilter | `public List<TimeSlot> filterAvailableTimeSlots2(SearchConfig clinic, String mrp, String providerId, Long appointmentTypeId, DayWorkSchedule dayWorkScheduleTransfer, List<TimeSlot> currentlyAllowedTimeSlots, Calendar date, Map<String, String> params)` | 75 |

### Billing (BC + ON)

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/billing/CA/BC/dao/Hl7LinkDao.java` | Hl7LinkDao | `public List<Object[]> findProvidersWithReports()` | 89 |
| `carlos/billing/CA/BC/dao/Hl7LinkDao.java` | Hl7LinkDao | `public List<Object[]> findReportsByProvider(String providerNo)` | 98 |
| `carlos/billing/CA/BC/model/BillingPrivateTransactions.java` | BillingPrivateTransactions | `public PrivateBillTransaction toTx(PrivateBillTransaction target)` | 119 |
| `carlos/billing/Clinicaid/util/ClinicaidCommunication.java` | ClinicaidCommunication | `public String buildClinicaidURL(HttpServletRequest request, String action)` | 63 |
| `carlos/billings/MSP/dbExtract.java` | dbExtract | `public ResultSet executeQuery3(String sql)` | 101 |
| `carlos/billings/ca/bc/MSP/MSPReconcile.java` | MSPReconcile | `public void updateBillType(String billingNo, String type)` | 973 |
| `carlos/billings/ca/bc/MSP/MSPReconcile.java` | MSPReconcile | `public boolean patientHasOutstandingPrivateBill(String demographicNo)` | 1971 |
| `carlos/billings/ca/bc/MSP/ServiceCodeValidationLogic.java` | ServiceCodeValidationLogic | `public int daysSinceLast13050(String demoNo)` | 154 |
| `carlos/billings/ca/bc/MSP/TeleplanSubmission.java` | TeleplanSubmission | `public void commitLog()` | 190 |
| `carlos/billings/ca/bc/MSP/WcbSb.java` | WcbSb | `public String ForDatabase()` | 296 |
| `carlos/billings/ca/bc/MSP/WcbSb.java` | WcbSb | `public boolean HasSecondFeeItem()` | 300 |
| `carlos/billings/ca/bc/MSP/WcbSb.java` | WcbSb | `public void SetSecondFeeAmount(String sfa)` | 308 |
| `carlos/billings/ca/bc/Teleplan/TeleplanMessagesDAO.java` | TeleplanMessagesDAO | `public void saveUpdateMessage(String sequenceNum)` | 76 |
| `carlos/billings/ca/bc/data/BillingHistoryDAO.java` | BillingHistoryDAO | `public void createBillingHistoryArchiveByBillNo(String billingNo)` | 178 |
| `carlos/billings/ca/bc/data/BillingmasterDAO.java` | BillingmasterDAO | `public List<Billingmaster> findBillingsByChronicCodes(Integer demographic_no, String chroniccodes)` | 314 |
| `carlos/billings/ca/bc/data/PrivateBillTransactionsDAO.java` | PrivateBillTransactionsDAO | `public BillingPrivateTransactions savePrivateBillTransaction(int billingmaster_no, double amount, int paymentType)` | 78 |
| `carlos/billings/ca/bc/pageUtil/BillingReProcessBill2Action.java` | BillingReProcessBill2Action | `public BillingReProcessBill2Form createBillingReProcessBillForm(String billingMasterNo, BillingmasterDAO billingmasterDAO, HttpServletRequest request)` | 500 |
| `carlos/billings/ca/bc/pageUtil/BillingViewBean.java` | BillingViewBean | `public double calculateGstTotal()` | 622 |
| `carlos/billings/ca/on/data/BillingCodeData.java` | BillingCodeData | `public boolean editBillingCodeByServiceCode(String val, String codeId, String date)` | 126 |
| `carlos/billings/ca/on/data/JdbcBillingCorrection.java` | JdbcBillingCorrection | `public boolean updateBillingPaid(String fee, String id)` | 403 |

### Forms / DAO

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/commn/dao/forms/FormsDao.java` | FormsDao | `public List<Object[]> selectBcFormAr2007(String beginEdd, String endEdd, int limit, int offset)` | 129 |
| `carlos/commn/model/IncomingLabRules.java` | IncomingLabRules | `public boolean addForwardType(String newType)` | 122 |
| `carlos/commn/model/Prevention.java` | Prevention | `public PreventionExt removePreventionExt(PreventionExt preventionExt)` | 261 |
| `carlos/commn/model/ProfessionalSpecialist.java` | ProfessionalSpecialist | `public String createContactString()` | 420 |

### PDF / Record Printing

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/commn/service/PdfRecordPrinter.java` | PdfRecordPrinter | `public void printPhotos(String contextPath, List<Document> photos)` | 753 |
| `carlos/commn/service/PdfRecordPrinter.java` | PdfRecordPrinter | `public void printDiagrams(List<EFormValue> diagrams)` | 790 |

### Database Access

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/db/DBHandler.java` | DBHandler | `public ResultSet GetPreSQL(String sql, Object... params)` | 97 |

### Decision Support

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/decisionSupport/service/DSService.java` | DSService | `public void fetchGuidelinesFromServiceInBackground(LoggedInInfo loggedInInfo)` | 84 |

### Demographics

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/demographic/data/DemographicAddResult.java` | DemographicAddResult | `public boolean wasAdded()` | 71 |

### Document Manager

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/documentManager/actions/AddEditDocument2Action.java` | AddEditDocument2Action | `public int storeDocumentInDatabase(File file, Integer documentNo)` | 504 |

### Encounter / Form Records (entire class likely dead - superseded by Frm*Record)

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/encounter/data/EctARRecord.java` | EctARRecord | `public int saveARRecord(Properties properties, String formId)` | 93 |
| `carlos/encounter/data/EctAlphaRecord.java` | EctAlphaRecord | `public int saveAlphaRecord(Properties props)` | 86 |
| `carlos/encounter/data/EctAnnualRecord.java` | EctAnnualRecord | `public int saveAnnualRecord(Properties props)` | 118 |
| `carlos/encounter/data/EctMMSERecord.java` | EctMMSERecord | `public int saveMMSERecord(Properties props)` | 109 |
| `carlos/encounter/data/EctMentalHealthRecord.java` | EctMentalHealthRecord | `public int saveMentalHealthRecord(Properties props, String formId)` | 125 |
| `carlos/encounter/data/EctPalliativeCareRecord.java` | EctPalliativeCareRecord | `public int savePalliativeCareRecord(Properties props)` | 104 |
| `carlos/encounter/data/EctPeriMenopausalRecord.java` | EctPeriMenopausalRecord | `public int savePeriMenopausalRecord(Properties props)` | 119 |
| `carlos/encounter/data/EctRourkeRecord.java` | EctRourkeRecord | `public int saveRourkeRecord(Properties props, String formId)` | 115 |
| `carlos/encounter/data/EctType2DiabetesRecord.java` | EctType2DiabetesRecord | `public int saveType2DiabetesRecord(Properties props)` | 88 |

### Encounter / Measurements / Session

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/encounter/oscarMeasurements/MeasurementFlowSheet.java` | MeasurementFlowSheet | `public void addFlowSheetItem(int i, FlowSheetItem item)` | 436 |
| `carlos/encounter/oscarMeasurements/MeasurementFlowSheet.java` | MeasurementFlowSheet | `public void loadRuleBase2(String string)` | 821 |
| `carlos/encounter/pageUtil/EctSessionBean.java` | EctSessionBean | `public void unsetConsultationRequestId()` | 429 |
| `carlos/encounter/pageUtil/EctSessionBean.java` | EctSessionBean | `public void unsetCurrentTeam()` | 444 |

### Legacy Entities (entire classes likely dead)

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/entities/WCB.java` | WCB | `public List verifyOnForm()` | 865 |

### Form Records

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/form/FrmRecordHelp.java` | FrmRecordHelp | `public void updateFormRecord(Properties props, String sql)` | 236 |
| `carlos/form/FrmRecordHelp.java` | FrmRecordHelp | `public void convertBooleanToChecked(Properties p)` | 344 |

### Hospital Report Manager

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/hospitalReportManager/HRMReportParser.java` | HRMReportParser | `public void addReportToInbox(LoggedInInfo loggedInInfo, HRMReport report)` | 168 |
| `carlos/hospitalReportManager/HRMReportParser.java` | HRMReportParser | `public void signOffOnReport(String providerRoutingId, Integer signOffStatus)` | 598 |
| `carlos/hospitalReportManager/dao/HRMDocumentToProviderDao.java` | HRMDocumentToProviderDao | `public List<HRMDocumentToProvider> findAllUnsigned(Integer page, Integer pageSize)` | 37 |
| `carlos/hospitalReportManager/dao/HRMSubClassDao.java` | HRMSubClassDao | `public List<HRMSubClass> findBySendingFacilityId(String sendingFacilityId)` | 51 |

### Lab / HL7

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/lab/ca/all/pageUtil/LabPDFCreator.java` | LabPDFCreator | `public void closeOs()` | 1277 |
| `carlos/lab/ca/all/parsers/CLSHandler.java` | CLSHandler | `public void insertOBR(ORU_R01_ORDER_OBSERVATION newOBR)` | 134 |
| `carlos/lab/ca/all/spireHapiExt/v23/segment/ZDS.java` | ZDS | `protected Type createNewTypeWithoutReflection(int field)` | 85 |
| `carlos/lab/ca/all/upload/handlers/DefaultHandler.java` | DefaultHandler | `public String readTextFile(String fullPathFilename)` | 158 |
| `carlos/lab/ca/all/util/HL7VersionFixer.java` | HL7VersionFixer | `public boolean needsVersionFix(String hl7Body)` | 115 |
| `carlos/lab/ca/bc/PathNet/HL7/Node.java` | Node | `public int booleanConvert(boolean b)` | 73 |
| `carlos/lab/ca/bc/PathNet/PathnetResultsData.java` | PathnetResultsData | `public int findNumOfFinalResults(String labId)` | 233 |
| `carlos/lab/ca/on/CommonLabResultData.java` | CommonLabResultData | `public ArrayList<LabResultData> populateLabResultsDataInboxIndexPage(...)` | 238 |
| `carlos/lab/ca/on/CommonLabResultData.java` | CommonLabResultData | `public ArrayList<LabResultData> populateLabResultsData2(...)` | 288 |
| `carlos/lab/ca/on/CommonLabResultData.java` | CommonLabResultData | `public ArrayList<LabResultData> populateDocumentData(...)` | 306 |

### Login / Auth / OAuth

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/login/OscarOAuthDataProvider.java` | OscarOAuthDataProvider | `public void removeToken(String tokenId)` | 197 |
| `carlos/login/UAgentInfo.java` | UAgentInfo | `public boolean detectIphone()` | 197 |
| `carlos/login/UAgentInfo.java` | UAgentInfo | `public boolean detectBlackBerryLow()` | 364 |
| `carlos/login/UAgentInfo.java` | UAgentInfo | `public boolean detectKindle()` | 481 |
| `carlos/login/UAgentInfo.java` | UAgentInfo | `public boolean detectMobileLong()` | 647 |
| `carlos/webserv/oauth/util/OAuthRequestParser.java` | OAuthRequestParser | `public String extractSignatureFromHeader(String authzHeader)` | 101 |
| `carlos/model/security/LdapSecurity.java` | LdapSecurity | `public Security toSecurity()` | 63 |
| `carlos/sec/CookieSecurity.java` | CookieSecurity | `public Cookie GiveMeACookie(String cookieName)` | 41 |
| `carlos/sec/CookieSecurity.java` | CookieSecurity | `public boolean FindThisCookie(Cookie[] cookies, String cookieName)` | 58 |

### Managers

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/managers/LookupListManager.java` | LookupListManager | `public LookupList findLookupListById(LoggedInInfo loggedInInfo, int id)` | 57 |
| `carlos/managers/LookupListManager.java` | LookupListManager | `public List<LookupListItem> findLookupListItemsByLookupListName(LoggedInInfo loggedInInfo, String lookupListName)` | 109 |
| `carlos/managers/MessengerGroupManager.java` | MessengerGroupManager | `public boolean checkProviderStatus(String providerNo)` | 484 |
| `carlos/managers/ProviderManager2.java` | ProviderManager2 | `public boolean updateAutoLinkToMrpProperty(LoggedInInfo loggedInInfo, String value)` | 712 |
| `carlos/managers/ProviderManager2.java` | ProviderManager2 | `public boolean viewAutoLinkToMrpPropertyStatus(LoggedInInfo loggedInInfo)` | 729 |
| `carlos/managers/SecurityManager.java` | SecurityManager | `public List<Security> findAllOrderByUserName(LoggedInInfo loggedInInfo)` | 228 |
| `carlos/managers/WaitListManager.java` | WaitListManager | `public void sendImmediateAdmissionNotification(Program program, Demographic demographic, Admission admission, String notes)` | 119 |
| `carlos/managers/WaitListManager.java` | WaitListManager | `public void checkAndSendAdmissionIntervalNotification(Program program)` | 156 |
| `carlos/managers/WaitListManager.java` | WaitListManager | `public void sendImmediateVacancyNotification(Vacancy vacancy, String notes)` | 250 |

### MDS / Messenger

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/mds/data/MDSResultsData.java` | MDSResultsData | `public String findCMLAccessionNumber(String labId)` | 630 |
| `carlos/messenger/config/data/MsgMessengerGroupData.java` | MsgMessengerGroupData | `public String parentDirectory(String grpNo)` | 123 |
| `carlos/messenger/config/data/MsgMessengerGroupData.java` | MsgMessengerGroupData | `public void printAllProvidersWithMembers(Locale locale, String grpNo, JspWriter out)` | 209 |
| `carlos/messenger/config/data/MsgMessengerGroupData.java` | MsgMessengerGroupData | `public String printAllBelowGroups(String grpNo)` | 263 |
| `carlos/messenger/data/MsgAddressBook.java` | MsgAddressBook | `public String myAddressBook()` | 124 |
| `carlos/messenger/data/MsgReplyMessageData.java` | MsgReplyMessageData | `public void estLists()` | 80 |
| `carlos/messenger/pageUtil/MsgSessionBean.java` | MsgSessionBean | `public void nullMessageId()` | 283 |

### Prescription / Drug Reference (MyDrugRef removed per project docs)

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/prescript/data/RxPatientData.java` | RxPatientData | `public Diseases addDisease(String ICD9, java.util.Date entryDate)` | 246 |
| `carlos/prescript/data/RxDrugData.java` | RxDrugData | `public DrugSearch listDrug2(String searchStr)` | 561 |
| `carlos/prescript/pageUtil/RxSessionBean.java` | RxSessionBean | `public void addReRxDrugIdList(String s)` | 84 |
| `carlos/prescript/pageUtil/RxSessionBean.java` | RxSessionBean | `public void addNewRandomIdToMap(Integer newId, Long newRandomId)` | 275 |
| `carlos/prescript/pageUtil/RxSessionBean.java` | RxSessionBean | `public void clearAllergyWarnings()` | 299 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector atcFromDIN(String din)` | 125 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector atcFromBrand(String drug)` | 141 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector atc2text(String code)` | 155 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector drug2atclist(String drug)` | 251 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector druglist2atclist(Vector druglist)` | 258 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector interaction_by_drugnames(Vector druglist, int minimum_significance)` | 266 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector list_sources(String searchexpr, Hashtable tags)` | 514 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Hashtable get_source_tag(int pkey)` | 525 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Base64 get_drug_html(int pkeye4, Base64 css)` | 653 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector list_products(int pkey, Hashtable tags)` | 672 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Hashtable get_product(int pkey, Hashtable tags)` | 699 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Base64 get_product_CPI(int pkey, Base64 css)` | 712 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector list_interactions(Vector drugs, Hashtable tags)` | 735 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector list_conditions(String searchexpr, Hashtable tags)` | 744 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector list_drugs_for_indication(int indication, Hashtable tags)` | 752 |
| `carlos/prescript/util/RxDrugRef.java` | RxDrugRef | `public Vector list_references(Hashtable tags)` | 768 |

### Reports

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/report/ClinicalReports/SQLNumerator.java` | SQLNumerator | `public boolean evaluateOLD(String demographicNo)` | 98 |
| `carlos/report/pageUtil/RptDemographicReport2Form.java` | RptDemographicReport2Form | `public void copyConstructor(RptDemographicReport2Form drf)` | 64 |
| `carlos/report/pageUtil/RptDemographicReport2Form.java` | RptDemographicReport2Form | `public boolean validateYear(String str)` | 280 |
| `carlos/report/reportByTemplate/ReportManager.java` | ReportManager | `public String updateTemplateXml(String xmltext)` | 211 |

### PDF / Document Conversion

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/util/Doc2PDF.java` | Doc2PDF | `public void parseJSP2PDF(HttpServletRequest request, HttpServletResponse response, String uri, String jsessionid)` | 94 |
| `carlos/util/Doc2PDF.java` | Doc2PDF | `public void HTMLDOC(HttpServletRequest request, HttpServletResponse response, String url)` | 165 |
| `carlos/util/Doc2PDF.java` | Doc2PDF | `public void SavePDF2File(String fileName, String docBin)` | 250 |

### Tags / Workflow

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/tags/TagObject.java` | TagObject | `public void assignTag(String tagName)` | 69 |
| `carlos/workflow/WorkFlowState.java` | WorkFlowState | `public String rhState(Object s)` | 146 |

### REST Transfer Objects / Web Services

| File | Class | Method Signature | Line |
|------|-------|-----------------|------|
| `carlos/webserv/rest/to/DocumentResponse.java` | DocumentResponse | `public void mergeAll(DocumentResponse documentResponse)` | 71 |
| `carlos/webserv/rest/to/model/FilterDefinitionTransfer.java` | FilterDefinitionTransfer | `public FilterDefinitionTransfer fromFilterDefinition(FilterDefinition f)` | 60 |

---

## MEDIUM Confidence - Entity/Form Getters/Setters (24)

These are getters/setters with unusual naming patterns (lowercase after prefix, underscore-based). They escaped the standard getter/setter filter. While no explicit Java code calls them, they MAY be invoked by:
- **Hibernate/JPA**: property access for @Entity classes
- **Jackson**: JSON serialization/deserialization for transfer objects
- **Struts**: form binding for form beans
- **BeanUtils**: reflection-based property copying

Review carefully before removing -- removing a JPA entity getter will break persistence.

### JPA Entity Properties

| File | Class | Method Signature | Line | Risk |
|------|-------|-----------------|------|------|
| `carlos/commn/model/MdsPV1.java` | MdsPV1 | `public String getvNumber()` | 113 | JPA entity |
| `carlos/commn/model/MdsPV1.java` | MdsPV1 | `public void setvNumber(String vNumber)` | 117 | JPA entity |
| `carlos/commn/model/EReferAttachmentData.java` | EReferAttachmentData | `public void seteReferAttachment(EReferAttachment eReferAttachment)` | 45 | JPA entity |
| `carlos/commn/model/ProviderPreference.java` | ProviderPreference | `public String geteFormName()` | 106 | JPA entity |
| `carlos/commn/model/ProviderPreference.java` | ProviderPreference | `public void seteFormName(String eFormName)` | 110 | JPA entity |

### Legacy Entity Properties (non-JPA, manual mapping)

| File | Class | Method Signature | Line | Risk |
|------|-------|-----------------|------|------|
| `carlos/entities/Ichppccode.java` | Ichppccode | `public String get_ichppccode()` | 65 | Legacy POJO |
| `carlos/entities/Ichppccode.java` | Ichppccode | `public void set_ichppccode(String _ichppccode)` | 92 | Legacy POJO |
| `carlos/entities/Immunizations.java` | Immunizations | `public String get_immunizations()` | 105 | Legacy POJO |
| `carlos/entities/Immunizations.java` | Immunizations | `public void set_immunizations(String _immunizations)` | 159 | Legacy POJO |
| `carlos/entities/LoincCodes.java` | LoincCodes | `public String get_final()` | 682 | Legacy POJO |
| `carlos/entities/LoincCodes.java` | LoincCodes | `public void set_final(String _final)` | 1231 | Legacy POJO |

### Transfer Object / Form Properties

| File | Class | Method Signature | Line | Risk |
|------|-------|-----------------|------|------|
| `carlos/prescript/pageUtil/RxSearchAllergy2Form.java` | RxSearchAllergy2Form | `public String getiNKDA()` | 106 | JSON deser |
| `carlos/prescript/pageUtil/RxSearchAllergy2Form.java` | RxSearchAllergy2Form | `public void setiNKDA(String iNKDA)` | 110 | JSON deser |
| `carlos/webserv/rest/to/model/AppointmentExtTo.java` | AppointmentExtTo | `public String gethPhoneExt()` | 215 | JSON deser |
| `carlos/webserv/rest/to/model/AppointmentExtTo.java` | AppointmentExtTo | `public void sethPhoneExt(String hPhoneExt)` | 219 | JSON deser |
| `carlos/webserv/rest/to/model/AppointmentExtTo.java` | AppointmentExtTo | `public String getwPhoneExt()` | 223 | JSON deser |
| `carlos/webserv/rest/to/model/AppointmentExtTo.java` | AppointmentExtTo | `public void setwPhoneExt(String wPhoneExt)` | 227 | JSON deser |
| `carlos/webserv/transfer_objects/DrugTransfer.java` | DrugTransfer | `public String geteTreatmentType()` | 459 | BeanUtils copy |
| `carlos/webserv/transfer_objects/DrugTransfer.java` | DrugTransfer | `public void seteTreatmentType(String eTreatmentType)` | 463 | BeanUtils copy |
| `carlos/webserv/rest/to/model/PrintRxTo1.java` | PrintRxTo1 | `public void setxPolygonCoords(float[] xPolygonCoords)` | 98 | Setter only |
| `carlos/webserv/rest/to/model/PrintRxTo1.java` | PrintRxTo1 | `public void setyPolygonCoords(float[] yPolygonCoords)` | 106 | Setter only |
| `carlos/prescript/data/RxDrugData.java` | RxDrugData | `public String getpKey()` | 339 | Inner class |

### Form Model Setters (getters ARE used, setters may be used by form binding)

| File | Class | Method Signature | Line | Risk |
|------|-------|-----------------|------|------|
| `carlos/form/model/FormRourke2017.java` | FormRourke2017 | `public void setcMale(String cMale)` | 645 | Form binding |
| `carlos/form/model/FormRourke2017.java` | FormRourke2017 | `public void setcFemale(String cFemale)` | 653 | Form binding |
| `carlos/form/model/FormRourke2020.java` | FormRourke2020 | `public void setcFsa(String cFsa)` | 1966 | Form binding |
| `carlos/form/model/FormRourke2020.java` | FormRourke2020 | `public void setcLength(String cLength)` | 1998 | Form binding |

---

## Verified False Positives Removed (~140 methods)

These were in the raw 277 results but were confirmed as used via framework patterns:

| Category | Count | Invocation Pattern |
|----------|-------|--------------------|
| JPA lifecycle callbacks (`@PrePersist`, `@PostLoad`, `@PreUpdate`, etc.) | 16 | Hibernate persistence framework |
| ProviderProperty2Action methods | 48 | Struts method-based routing via `methodMap` |
| DSDemographicAccess methods | 12 | Drools DRL convention-based dynamic invocation |
| InboxQueryParameters builder methods | 18 | Builder pattern, getters consumed by repository layer |
| SOAP web service endpoints (LoginWs, ScheduleWs, BookingWs, LabUploadWs) | 7 | CXF SOAP framework via `spring_ws.xml` |
| PageNumberStamper callbacks | 2 | iText `PdfPageEventHelper` framework |
| ScheduleService REST endpoint | 1 | JAX-RS `@GET` / `@Path` annotation |
| ManageDocument2Action.downloadCDS() | 1 | Struts mapping (deprecated, returns HTTP 503) |
| **Total removed** | **~140** | |

---

## Candidates for Entire Class Removal

These classes appear to have ALL or most public methods unused, suggesting the entire class may be dead code:

1. **`CdsForm4.java`** + **`CdsForm4Action.java`** - Orphaned CDS form utility classes
2. **`EctARRecord.java`**, **`EctAlphaRecord.java`**, **`EctAnnualRecord.java`**, **`EctMMSERecord.java`**, **`EctMentalHealthRecord.java`**, **`EctPalliativeCareRecord.java`**, **`EctPeriMenopausalRecord.java`**, **`EctRourkeRecord.java`**, **`EctType2DiabetesRecord.java`** - Superseded by `Frm*Record` classes
3. **`RxDrugRef.java`** - 15 of its public methods are unused; wraps the removed MyDrugRef service
4. **`CookieSecurity.java`** - Both public methods unused
5. **`Doc2PDF.java`** - All 3 public static methods unused (HTMLDOC-based PDF conversion)
6. **`UAgentInfo.java`** - 4 device-detection methods unused (legacy mobile detection)
7. **`MsgMessengerGroupData.java`** - 3 methods unused

---

## Caveats

1. **Reflection not caught by string search**: Methods invoked via `Method.invoke()` with dynamically constructed method names (beyond what Drools/Spring patterns were verified) may be false positives
2. **External callers**: SOAP/REST endpoints are callable by external systems not in this codebase
3. **Database-stored references**: Some method names may be stored in database tables (e.g., `dsGuidelines`) and referenced at runtime
4. **Build-time code generation**: Any generated code not in the source tree was not analyzed
5. **Test code**: Test-only usage was deliberately excluded; these methods may be tested but not used in production
