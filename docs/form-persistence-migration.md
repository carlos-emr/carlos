# Form persistence migration (#5693)

Inventory: `release/2026.08` at `d55d02055ce`, checked against Java callers, JSPs,
the form factory allowlist, entity mappings and the common/ON/BC Flyway schema.
Rechecked for the second slice at `e61dfdd3`. Two reviewable implementation slices
permitted by #5693 are complete: Growth 0–36 (#5705) and the eighteen 2004
rehabilitation-study questionnaires (below). The parent issue and #2249 remain open
until every remaining path below is migrated and verified.

## Historical list reconciliation

The historical list has 46 names. Three names are still present verbatim at the
inventory baseline (Growth0_36, MentalHealth and Rourke); three Rourke names now
use four-digit years. The other forty names have no current Java class or
direct caller. Their absence is not evidence that an old deployment's stored
clinical data can be deleted. No schema or published migration is changed here.

| Historical class | Current disposition |
| --- | --- |
| `FrmALSRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmANERecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmAntenatalRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmCDMRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmCML1Record` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmCONSRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmCPP14Record` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmCdmDiabetesRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmCdmIhdRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmCdmSmokerRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmCdmYRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmD5Record` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmDMRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmDPRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmEpisodeRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmFURecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmGrowth0_36Record` | Migrated in this slice: `Growth0To36Form` → Spring `Growth0To36FormService` → `FormGrowth0To36Dao` / `FormGrowth0To36`; old class removed |
| `FrmHPRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmHaitiRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmINRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmINTRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmINT2Record` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmKitchenerRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmMHRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmMHPRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmMSRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmMSPRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmMentalHealthRecord` | Outstanding: [FrmMentalHealthRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthRecord.java) |
| `FrmOBRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmONARRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmONMDRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmONMD2Record` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmONMD3Record` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmPCRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmPRRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmRSRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmRourkeRecord` | Outstanding: [FrmRourkeRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourkeRecord.java) |
| `FrmRourke09Record` | Current name: [FrmRourke2009Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2009Record.java); see current inventory |
| `FrmRourke17Record` | Current name: [FrmRourke2017Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2017Record.java); see current inventory |
| `FrmRourke20Record` | Current name: [FrmRourke2020Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2020Record.java); see current inventory |
| `FrmSPRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmSTRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmSOAPRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmSkinRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmWHRecord` | Absent from current production sources and factory allowlist; no replacement claimed |
| `FrmXTRecord` | Absent from current production sources and factory allowlist; no replacement claimed |

## Current record paths

There were 62 concrete `Frm*Record` classes at the baseline; 61 remained after
the Growth slice and 43 remain after the rehabilitation-study slice. The eighteen
retired rehabilitation-study rows are listed under that slice below. Generic factory-created saves pass through `Frm2Action`;
BCAR 2020 also has its specialized action and save service.
The caller column lists additional static/direct and suffix-string callers;
`encounterForm` registrations also select forms dynamically at runtime.

Tables are cross-checked against Flyway definitions and JPA table mappings;
measurement type codes such as `Ht` are not table names. Existing entities and
typed DAO replacements are listed even when the record still uses JDBC. Called
DAOs are distinguished from available replacements. A navigation-only use of
`FrmRecordHelp` does not classify a typed path as JDBC persistence. Helper-backed
writes still use updatable result sets without establishing a service transaction.
The operation column describes the record's methods; dynamic `encounterForm`
registrations remain another runtime caller.

| Record | Tables / data sources named by the record | Operations | Existing mappings / boundary | Additional callers |
| --- | --- | --- | --- | --- |
| [FrmAdfV2Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmAdfV2Record.java) | `demographic`, `formAdfV2` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formadfv2](../src/main/webapp/WEB-INF/jsp/form/formadfv2.jsp) |
| [FrmAlphaRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmAlphaRecord.java) | `demographic`, `formAlpha` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [Frm2Action](../src/main/java/io/github/carlos_emr/carlos/form/Frm2Action.java), [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formalpha](../src/main/webapp/WEB-INF/jsp/form/formalpha.jsp) |
| [FrmAnnualRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmAnnualRecord.java) | `demographic`, `formAnnual` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [FormsManagerImpl](../src/main/java/io/github/carlos_emr/carlos/managers/FormsManagerImpl.java), [formannualfemale](../src/main/webapp/WEB-INF/jsp/form/formannualfemale.jsp), [formannualfemaleV2](../src/main/webapp/WEB-INF/jsp/form/formannualfemaleV2.jsp), [formannualfemaleprint](../src/main/webapp/WEB-INF/jsp/form/formannualfemaleprint.jsp), [formannualmale](../src/main/webapp/WEB-INF/jsp/form/formannualmale.jsp), [formannualmaleV2](../src/main/webapp/WEB-INF/jsp/form/formannualmaleV2.jsp) |
| [FrmAnnualV2Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmAnnualV2Record.java) | `demographic`, `formAnnualV2` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formannualfemaleV2](../src/main/webapp/WEB-INF/jsp/form/formannualfemaleV2.jsp), [formannualmaleV2](../src/main/webapp/WEB-INF/jsp/form/formannualmaleV2.jsp) |
| [FrmBCAR2007Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2007Record.java) | `demographic`, `demographicExt`, `formBCAR2007` | load/prefill, version save, print | Mixed JDBC/helper and typed DAO paths; `Demographic`, `DemographicExt`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `DemographicExtDao`, `DemographicExtDaoImpl`; called DAOs: `DemographicExtDao` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [Pregnancy2Action](../src/main/java/io/github/carlos_emr/carlos/commn/web/Pregnancy2Action.java), [RptDownloadCSVServlet](../src/main/java/io/github/carlos_emr/carlos/report/pageUtil/RptDownloadCSVServlet.java), [formbcar2007](../src/main/webapp/WEB-INF/jsp/form/formbcar2007.jsp), [formbcar2007pg1](../src/main/webapp/WEB-INF/jsp/form/formbcar2007pg1.jsp), [formbcar2007pg2](../src/main/webapp/WEB-INF/jsp/form/formbcar2007pg2.jsp), [formbcar2007pg3](../src/main/webapp/WEB-INF/jsp/form/formbcar2007pg3.jsp) |
| [FrmBCAR2012Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2012Record.java) | `demographic`, `demographicExt`, `formBCAR2012` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `DemographicExt`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `DemographicExtDao`, `DemographicExtDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [Pregnancy2Action](../src/main/java/io/github/carlos_emr/carlos/commn/web/Pregnancy2Action.java), [formbcar2012](../src/main/webapp/WEB-INF/jsp/form/formbcar2012.jsp), [formbcar2012pg1](../src/main/webapp/WEB-INF/jsp/form/formbcar2012pg1.jsp), [formbcar2012pg2](../src/main/webapp/WEB-INF/jsp/form/formbcar2012pg2.jsp), [formbcar2012pg3](../src/main/webapp/WEB-INF/jsp/form/formbcar2012pg3.jsp) |
| [FrmBCAR2020Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2020Record.java) | `demographic`, `demographicExt`, `formBCAR2020`, `formBCAR2020Data`, `formBCAR2020Text`, `provider` | load/prefill, version save | BCAR2020FormSaveService transaction for header/data/text; `Demographic`, `DemographicExt`, `FormBCAR2020`, `FormBCAR2020Data`, `FormBCAR2020Text`, `Provider`, `ProviderData`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `DemographicExtDao`, `DemographicExtDaoImpl`, `FormBCAR2020Dao`, `FormBCAR2020DataDao`, `FormBCAR2020TextDao`, `ProviderDataDao`, `ProviderDataDaoImpl`; called DAOs: `FormBCAR2020Dao`, `FormBCAR2020DataDao`, `FormBCAR2020TextDao` | [FrmBCAR20202Action](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR20202Action.java), [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [BCAR2020FormSaveService](../src/main/java/io/github/carlos_emr/carlos/form/BCAR2020FormSaveService.java), [Pregnancy2Action](../src/main/java/io/github/carlos_emr/carlos/commn/web/Pregnancy2Action.java), [formBCAR2020Attachments](../src/main/webapp/WEB-INF/jsp/form/formBCAR2020Attachments.jsp), [formBCAR2020pg1](../src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg1.jsp), [formBCAR2020pg2](../src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg2.jsp), [formBCAR2020pg3](../src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg3.jsp), [formBCAR2020pg4](../src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg4.jsp), [formBCAR2020pg5](../src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg5.jsp) |
| [FrmBCARRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCARRecord.java) | `demographic`, `demographicExt`, `formBCAR` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `DemographicExt`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `DemographicExtDao`, `DemographicExtDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [Pregnancy2Action](../src/main/java/io/github/carlos_emr/carlos/commn/web/Pregnancy2Action.java), [formbcar](../src/main/webapp/WEB-INF/jsp/form/formbcar.jsp), [formbcarpg1](../src/main/webapp/WEB-INF/jsp/form/formbcarpg1.jsp), [formbcarpg2](../src/main/webapp/WEB-INF/jsp/form/formbcarpg2.jsp), [formbcarpg3](../src/main/webapp/WEB-INF/jsp/form/formbcarpg3.jsp) |
| [FrmBCBirthSumMo2008Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCBirthSumMo2008Record.java) | `demographic`, `demographicExt`, `formBCBirthSumMo2008` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `DemographicExt`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `DemographicExtDao`, `DemographicExtDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formbcbirthsummo2008](../src/main/webapp/WEB-INF/jsp/form/formbcbirthsummo2008.jsp) |
| [FrmBCBrithSumMoRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCBrithSumMoRecord.java) | `demographic`, `demographicExt`, `formBCBirthSumMo` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `DemographicExt`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `DemographicExtDao`, `DemographicExtDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formbcbirthsummo](../src/main/webapp/WEB-INF/jsp/form/formbcbirthsummo.jsp) |
| [FrmBCClientChartChecklistRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCClientChartChecklistRecord.java) | `clinic`, `demographic`, `formBCClientChartChecklist` | load/prefill, version save, print | Mixed JDBC/helper and typed DAO paths; `Clinic`, `Demographic`; existing DAOs: `ClinicDAO`, `ClinicDAOImpl`, `DemographicDao`, `DemographicDaoImpl`; called DAOs: `ClinicDAO` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formbcclientchartchecklist](../src/main/webapp/WEB-INF/jsp/form/formbcclientchartchecklist.jsp) |
| [FrmBCHPRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCHPRecord.java) | `demographic`, `formBCHP`, `provider` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `Provider`, `ProviderData`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formbchp](../src/main/webapp/WEB-INF/jsp/form/formbchp.jsp), [formbchppg1](../src/main/webapp/WEB-INF/jsp/form/formbchppg1.jsp), [formbchppg2](../src/main/webapp/WEB-INF/jsp/form/formbchppg2.jsp) |
| [FrmBCINRRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCINRRecord.java) | `demographic`, `formBCINR`, `hl7_obr`, `hl7_obx`, `patientLabRouting` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `Hl7Obr`, `Hl7Obx`, `PatientLabRouting`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `PatientLabRoutingDao`, `PatientLabRoutingDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formbcinr](../src/main/webapp/WEB-INF/jsp/form/formbcinr.jsp) |
| [FrmBCNewBorn2008Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCNewBorn2008Record.java) | `demographic`, `demographicExt`, `formBCNewBorn2008` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `DemographicExt`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `DemographicExtDao`, `DemographicExtDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formBCNewBorn2008](../src/main/webapp/WEB-INF/jsp/form/formBCNewBorn2008.jsp), [formBCNewBorn2008pg1](../src/main/webapp/WEB-INF/jsp/form/formBCNewBorn2008pg1.jsp), [formBCNewBorn2008pg2](../src/main/webapp/WEB-INF/jsp/form/formBCNewBorn2008pg2.jsp), [formBCNewBorn2008pg3](../src/main/webapp/WEB-INF/jsp/form/formBCNewBorn2008pg3.jsp) |
| [FrmBCNewBornRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCNewBornRecord.java) | `demographic`, `demographicExt`, `formBCNewBorn` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `DemographicExt`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `DemographicExtDao`, `DemographicExtDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formbcnewborn](../src/main/webapp/WEB-INF/jsp/form/formbcnewborn.jsp), [formbcnewbornpg1](../src/main/webapp/WEB-INF/jsp/form/formbcnewbornpg1.jsp), [formbcnewbornpg2](../src/main/webapp/WEB-INF/jsp/form/formbcnewbornpg2.jsp), [formbcnewbornpg3](../src/main/webapp/WEB-INF/jsp/form/formbcnewbornpg3.jsp) |
| [FrmConsultantRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmConsultantRecord.java) | `clinic`, `demographic`, `formConsult`, `professionalSpecialists`, `provider` | load/prefill, version save, print | Mixed JDBC/helper and typed DAO paths; `Clinic`, `Demographic`, `ProfessionalSpecialist`, `Provider`, `ProviderData`; existing DAOs: `ClinicDAO`, `ClinicDAOImpl`, `DemographicDao`, `DemographicDaoImpl`, `ProfessionalSpecialistDao`, `ProfessionalSpecialistDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl`; called DAOs: `ClinicDAO`, `ProfessionalSpecialistDao` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formConsultant](../src/main/webapp/WEB-INF/jsp/form/formConsultant.jsp) |
| [FrmCounselingRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmCounselingRecord.java) | `clinic`, `demographic`, `formCounseling` | load/prefill, version save | Mixed JDBC/helper and typed DAO paths; `Clinic`, `Demographic`; existing DAOs: `ClinicDAO`, `ClinicDAOImpl`, `DemographicDao`, `DemographicDaoImpl`; called DAOs: `ClinicDAO` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formCounseling](../src/main/webapp/WEB-INF/jsp/form/formCounseling.jsp) |
| [FrmCounsellorAssessmentRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmCounsellorAssessmentRecord.java) | `demographic` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formcounsellorassessment](../src/main/webapp/WEB-INF/jsp/form/formcounsellorassessment.jsp) |
| [FrmDischargeSummaryRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmDischargeSummaryRecord.java) | `admission`, `allergies`, `casemgmt_issue`, `demographic`, `drugs`, `formDischargeSummary`, `issue`, `program`, `provider` | load/prefill, version save | Mixed JDBC/helper and typed DAO paths; `Admission`, `Allergy`, `Demographic`, `Drug`, `Program`, `Provider`, `ProviderData`; existing DAOs: `AdmissionDao`, `AdmissionDaoImpl`, `AllergyDao`, `AllergyDaoImpl`, `AllergyMergedDemographicDao`, `DemographicDao`, `DemographicDaoImpl`, `DrugDao`, `DrugDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl`; called DAOs: `AllergyDao` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formDischargeSummary](../src/main/webapp/WEB-INF/jsp/form/formDischargeSummary.jsp), [formDischargeSummaryPrint](../src/main/webapp/WEB-INF/jsp/form/formDischargeSummaryPrint.jsp) |
| [FrmGrowthChartRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmGrowthChartRecord.java) | `demographic`, `formGrowthChart` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formGrowthChart](../src/main/webapp/WEB-INF/jsp/form/formGrowthChart.jsp) |
| [FrmImmunAllergyRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmImmunAllergyRecord.java) | `demographic`, `formImmunAllergy` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formimmunallergy](../src/main/webapp/WEB-INF/jsp/form/formimmunallergy.jsp) |
| [FrmInvoiceRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmInvoiceRecord.java) | `demographic` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formInvoice](../src/main/webapp/WEB-INF/jsp/form/formInvoice.jsp) |
| [FrmLabReq07Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmLabReq07Record.java) | `clinic`, `demographic`, `formLabReq07`, `provider` | load/prefill, version save, print | Mixed JDBC/helper and typed DAO paths; `Clinic`, `Demographic`, `FormLabReq07`, `Provider`, `ProviderData`; existing DAOs: `ClinicDAO`, `ClinicDAOImpl`, `DemographicDao`, `DemographicDaoImpl`, `FormLabReq07Dao`, `FormLabReq07DaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl`; called DAOs: `ClinicDAO` | [Frm2Action](../src/main/java/io/github/carlos_emr/carlos/form/Frm2Action.java), [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [Pregnancy2Action](../src/main/java/io/github/carlos_emr/carlos/commn/web/Pregnancy2Action.java), [formMentalHealthForm1](../src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm1.jsp), [formMentalHealthForm14](../src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm14.jsp), [formMentalHealthForm42](../src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm42.jsp), [formlabreq07](../src/main/webapp/WEB-INF/jsp/form/formlabreq07.jsp) |
| [FrmLabReq10Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmLabReq10Record.java) | `clinic`, `demographic`, `formLabReq10`, `provider` | load/prefill, version save, print | Typed DAO path; legacy record orchestration remains; `Clinic`, `Demographic`, `Provider`, `ProviderData`; existing DAOs: `ClinicDAO`, `ClinicDAOImpl`, `DemographicDao`, `DemographicDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl`; called DAOs: `ClinicDAO`, `ProviderDao` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [Pregnancy2Action](../src/main/java/io/github/carlos_emr/carlos/commn/web/Pregnancy2Action.java), [formlabreq10](../src/main/webapp/WEB-INF/jsp/form/formlabreq10.jsp) |
| [FrmLabReqRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmLabReqRecord.java) | `clinic`, `demographic`, `formLabReq`, `provider` | load/prefill, version save, print | Mixed JDBC/helper and typed DAO paths; `Clinic`, `Demographic`, `Provider`, `ProviderData`; existing DAOs: `ClinicDAO`, `ClinicDAOImpl`, `DemographicDao`, `DemographicDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl`; called DAOs: `ClinicDAO` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formlabreq](../src/main/webapp/WEB-INF/jsp/form/formlabreq.jsp) |
| [FrmMMSERecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmMMSERecord.java) | `demographic`, `formMMSE` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formmmse](../src/main/webapp/WEB-INF/jsp/form/formmmse.jsp) |
| [FrmMentalHealthForm14Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthForm14Record.java) | `demographic`, `formMentalHealthForm14`, `provider` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `Provider`, `ProviderData`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formMentalHealthForm14](../src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm14.jsp) |
| [FrmMentalHealthForm1Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthForm1Record.java) | `demographic`, `formMentalHealthForm1`, `provider` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `Provider`, `ProviderData`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formMentalHealthForm1](../src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm1.jsp) |
| [FrmMentalHealthForm42Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthForm42Record.java) | `demographic`, `formMentalHealthForm42`, `provider` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `Provider`, `ProviderData`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formMentalHealthForm42](../src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm42.jsp) |
| [FrmMentalHealthRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthRecord.java) | `demographic`, `formMentalHealth`, `provider` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`, `Provider`, `ProviderData`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `ProviderDataDao`, `ProviderDataDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formmentalhealth](../src/main/webapp/WEB-INF/jsp/form/formmentalhealth.jsp), [formmhassessment](../src/main/webapp/WEB-INF/jsp/form/formmhassessment.jsp), [formmhoutcome](../src/main/webapp/WEB-INF/jsp/form/formmhoutcome.jsp), [formmhreferral](../src/main/webapp/WEB-INF/jsp/form/formmhreferral.jsp) |
| [FrmPalliativeCareRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmPalliativeCareRecord.java) | `demographic`, `formPalliativeCare` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [OntarioMD](../src/main/java/io/github/carlos_emr/carlos/utility/OntarioMD.java), [formpalliativecare](../src/main/webapp/WEB-INF/jsp/form/formpalliativecare.jsp) |
| [FrmPeriMenopausalRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmPeriMenopausalRecord.java) | `demographic`, `formPeriMenopausal` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formperimenopausal](../src/main/webapp/WEB-INF/jsp/form/formperimenopausal.jsp) |
| [FrmPolicyRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmPolicyRecord.java) | `demographic`, `formNoShowPolicy` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java) |
| [FrmPositionHazardRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmPositionHazardRecord.java) | `clinic`, `demographic`, `formPositionHazard` | load/prefill, version save, print | Typed DAO path; legacy record orchestration remains; `Clinic`, `Demographic`; existing DAOs: `ClinicDAO`, `ClinicDAOImpl`, `DemographicDao`, `DemographicDaoImpl`; called DAOs: `ClinicDAO` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formPositionHazard](../src/main/webapp/WEB-INF/jsp/form/formPositionHazard.jsp) |
| [FrmReceptionAssessmentRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmReceptionAssessmentRecord.java) | `demographic`, `formreceptionassessment` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formreceptionassessment](../src/main/webapp/WEB-INF/jsp/form/formreceptionassessment.jsp) |
| [FrmRhImmuneGlobulinRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmRhImmuneGlobulinRecord.java) | `demographic`, `formRhImmuneGlobulin` | load/prefill, version save, print | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [FrmFormRHPrevention2Action](../src/main/java/io/github/carlos_emr/carlos/form/pageUtil/FrmFormRHPrevention2Action.java), [addRhInjection](../src/main/webapp/WEB-INF/jsp/form/addRhInjection.jsp), [formRhImmuneGlobulin](../src/main/webapp/WEB-INF/jsp/form/formRhImmuneGlobulin.jsp) |
| [FrmRourke2006Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2006Record.java) | `demographic`, `formRourke2006` | load/prefill, version save, graph | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formrourke2006](../src/main/webapp/WEB-INF/jsp/form/formrourke2006.jsp), [formrourke2006p1](../src/main/webapp/WEB-INF/jsp/form/formrourke2006p1.jsp), [formrourke2006p2](../src/main/webapp/WEB-INF/jsp/form/formrourke2006p2.jsp), [formrourke2006p3](../src/main/webapp/WEB-INF/jsp/form/formrourke2006p3.jsp), [formrourke2006p4](../src/main/webapp/WEB-INF/jsp/form/formrourke2006p4.jsp) |
| [FrmRourke2009Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2009Record.java) | `demographic`, `formGrowth0_36`, `formRourke2009`, `measurements` | load/prefill, version save, graph | Mixed JDBC/helper and typed DAO paths; `Demographic`, `FormGrowth0To36`, `FormRourke2009`, `Measurement`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `FormGrowth0To36Dao`, `MeasurementDao`, `MeasurementDaoImpl`, `Rourke2009DAO`; called DAOs: `MeasurementDao`, `Rourke2009DAO` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formRourke2020p2](../src/main/webapp/WEB-INF/jsp/form/formRourke2020p2.jsp), [formrourke2009complete](../src/main/webapp/WEB-INF/jsp/form/formrourke2009complete.jsp), [formrourke2009p1](../src/main/webapp/WEB-INF/jsp/form/formrourke2009p1.jsp), [formrourke2009p2](../src/main/webapp/WEB-INF/jsp/form/formrourke2009p2.jsp), [formrourke2009p3](../src/main/webapp/WEB-INF/jsp/form/formrourke2009p3.jsp), [formrourke2009p4](../src/main/webapp/WEB-INF/jsp/form/formrourke2009p4.jsp), [formrourke2017p2](../src/main/webapp/WEB-INF/jsp/form/formrourke2017p2.jsp) |
| [FrmRourke2017Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2017Record.java) | `demographic`, `formGrowth0_36`, `formRourke2017`, `form_boolean_value`, `measurements` | load/prefill, version save, graph | Typed DAO path; legacy record orchestration remains; `Demographic`, `FormBooleanValue`, `FormGrowth0To36`, `FormRourke2017`, `Measurement`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `FormBooleanValueDao`, `FormGrowth0To36Dao`, `MeasurementDao`, `MeasurementDaoImpl`, `Rourke2017Dao`; called DAOs: `MeasurementDao`, `Rourke2017Dao` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formrourke2017complete](../src/main/webapp/WEB-INF/jsp/form/formrourke2017complete.jsp), [formrourke2017p1](../src/main/webapp/WEB-INF/jsp/form/formrourke2017p1.jsp), [formrourke2017p2](../src/main/webapp/WEB-INF/jsp/form/formrourke2017p2.jsp), [formrourke2017p3](../src/main/webapp/WEB-INF/jsp/form/formrourke2017p3.jsp), [formrourke2017p4](../src/main/webapp/WEB-INF/jsp/form/formrourke2017p4.jsp) |
| [FrmRourke2020Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2020Record.java) | `demographic`, `formGrowth0_36`, `formRourke2020`, `form_boolean_value`, `measurements` | load/prefill, version save, graph | Typed DAO path; legacy record orchestration remains; `Demographic`, `FormBooleanValue`, `FormGrowth0To36`, `FormRourke2020`, `Measurement`; existing DAOs: `DemographicDao`, `DemographicDaoImpl`, `FormBooleanValueDao`, `FormGrowth0To36Dao`, `MeasurementDao`, `MeasurementDaoImpl`, `Rourke2020Dao`; called DAOs: `MeasurementDao`, `Rourke2020Dao` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formRourke2020p1](../src/main/webapp/WEB-INF/jsp/form/formRourke2020p1.jsp), [formRourke2020p2](../src/main/webapp/WEB-INF/jsp/form/formRourke2020p2.jsp), [formRourke2020p3](../src/main/webapp/WEB-INF/jsp/form/formRourke2020p3.jsp), [formRourke2020p4](../src/main/webapp/WEB-INF/jsp/form/formRourke2020p4.jsp), [formrourke2020complete](../src/main/webapp/WEB-INF/jsp/form/formrourke2020complete.jsp) |
| [FrmRourkeRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourkeRecord.java) | `demographic`, `formRourke` | load/prefill, version save, graph | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [DataExportDao](../src/main/java/io/github/carlos_emr/carlos/commn/dao/DataExportDao.java), [formrourke](../src/main/webapp/WEB-INF/jsp/form/formrourke.jsp), [formrourke1](../src/main/webapp/WEB-INF/jsp/form/formrourke1.jsp), [formrourke2](../src/main/webapp/WEB-INF/jsp/form/formrourke2.jsp), [formrourke3](../src/main/webapp/WEB-INF/jsp/form/formrourke3.jsp), [formrourkep1](../src/main/webapp/WEB-INF/jsp/form/formrourkep1.jsp), [formrourkep2](../src/main/webapp/WEB-INF/jsp/form/formrourkep2.jsp), [formrourkep3](../src/main/webapp/WEB-INF/jsp/form/formrourkep3.jsp), [graphHeadCirc](../src/main/webapp/WEB-INF/jsp/form/graphHeadCirc.jsp), [graphLengthWeight](../src/main/webapp/WEB-INF/jsp/form/graphLengthWeight.jsp) |
| [FrmSelfAssessmentRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmSelfAssessmentRecord.java) | `demographic`, `formSelfAssessment` | load/prefill, version save | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [formSelfAssessment](../src/main/webapp/WEB-INF/jsp/form/formSelfAssessment.jsp) |
| [FrmchfRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmchfRecord.java) | `demographic`, `formchf` | load/prefill, version save | JDBC/helper persistence; `Demographic`; existing DAOs: `DemographicDao`, `DemographicDaoImpl` | [FrmRecordFactory](../src/main/java/io/github/carlos_emr/carlos/form/FrmRecordFactory.java), [FormViewRoutes](../src/main/java/io/github/carlos_emr/carlos/form/gate/FormViewRoutes.java), [formchf](../src/main/webapp/WEB-INF/jsp/form/formchf.jsp) |

## Shared persistence and transaction boundaries

- `FrmRecordHelp`: metadata-based load/print, insert, update and demographic-ID
  queries. `saveFormRecord` opens a connection, inserts an updatable result-set
  row and obtains the generated ID on that same connection. It optionally
  archives XML. No service transaction is established by this helper.
- `FrmRecord`: shared demographic access through `DemographicManager` and
  `DemographicExtDao`; the actor-aware save overload preserves existing records
  while carrying the actor into migrated services.
- `Frm2Action`: patient/form authorization, form version dispatch, then audit
  and navigation. Its update, ordinary save and autosave paths all pass the
  actor to the record. Existing audit and navigation occur after save returns.
- `FrmForm2Action` / `FrmSetupForm2Action`: XML-defined measurement forms use
  `FrmRecordHelp`; the save's TransactionTemplate also writes measurements.
  This is not a completed JPA migration.
- `FrmFormRHPrevention2Action` / `FrmFormAddRHWorkFlow2Action`: existing
  TransactionTemplate boundaries combine RH form, prevention and workflow
  writes. Their record/helper portions remain to be migrated together.
- `Rourke2017Dao` / `FormRourke2017` and `Rourke2020Dao` /
  `FormRourke2020` already map the form headers. Their transient boolean maps
  use `FormBooleanValueDao` / `FormBooleanValue` (`form_boolean_value`), including
  model callback writes. Reuse these mappings and verify the full DAO/callback
  transaction when moving the legacy record orchestration; do not add duplicate
  entities. Rourke 2009 has an existing `FormRourke2009` mapping while its record
  retains JDBC paths.
- `FrmBCAR2020Record`, `BCAR2020FormSaveService`, `FormBCAR2020Dao`,
  `FormBCAR2020DataDao`, `FormBCAR2020TextDao`: existing JPA implementation and
  multi-table atomic save from #5605; retain its ownership/rollback coverage.
- `form/data/FrmData`, `FrmRecordFactory`, `LastVisitedPageResolver`: form registry,
  shortcut resolution, dispatch and page selection also require review
  when their dependent persistence paths move. A class rename alone does not
  migrate a persistence boundary. Growth history, current-study-form and latest
  shortcut reads in `FrmData` now delegate to the same typed history projection
  as `EctFormData`; registry lookups and other forms remain unchanged.
- `EctFormData`: the Growth list and grouped-list routes now use the typed DAO;
  `INTERNAL_FORM_TABLES` is removed. Generic encounter/eForm JDBC paths belong
  to #5547. Grouped Growth history uses the maximum ID and edit timestamp per
  edit day and takes `formCreated` from that selected ID, removing the old
  query's arbitrary non-grouped creation date.
- `JDBCUtil`: XML import remains shared legacy JDBC and retains its independent
  import allowlist for Growth. Do not remove it or shared JDBC lifecycle cleanup
  while these and other modules still depend on them.

## Growth slice

`FormGrowth0To36` maps all 131 existing columns: ID, patient/provider, dates,
six header strings, and twenty measurement slots with six columns each. Bound DAO reads
include both patient and record ID. Each save creates a generated-ID version;
the source remains untouched, the actor supplies the provider, and the service
checks form/patient access and source ownership before writing.

The JSP, Rourke 2009/2017/2020 graphs and eForm growth overlay retain their
`FrmRecordFactory("Growth0_36")`/Properties contract through `Growth0To36Form`.
`Growth0To36FormValues` preserves slash/ISO dates, nullable and empty strings,
the legacy reader's display trimming without trimming stored text,
all twenty measurement slots and the existing edit timestamp display. Optional
XML archive publication runs only after commit and uses a new patient-bound
DAO read with the existing interchange encoding/path validation. Archive
failure retains the already committed form, matching existing behavior.
Archive basenames retain the existing form/patient/timestamp contract required
by `JDBCUtil.parseImportFileName`. Column names and encoded values retain the
existing XML interchange contract.

The integration suite runs with H2 and the physical MariaDB/Flyway harness,
including CI's generated, Ontario Flyway and BC Flyway lanes.
It compares all persisted properties with the legacy reader, proves fresh IDs
and preserved source versions, checks patient isolation and metadata history,
and verifies commit/rollback from an independent connection. A later refused
write must roll back an earlier successful form insert; rollback must publish
no XML archive. Additional cases cover a later database constraint failure,
failure after flushing an identity insert, all 131 physical columns and JDBC
timestamp metadata, distinct values in all twenty slots, fully null legacy
slots, coherent grouped-history creation dates and the XML filename/content
contract consumed by the existing importer.
Post-commit archive read and filesystem failures retain the committed form,
emit an error without clinical values or exception details, and leave no XML copy.
Loading either a saved or new form allows the demographic manager's synchronous
health-care-team audit to commit. Regression cases run the real demographic
manager's MRP and health-care-team audit path and the real audit DAO (its demographic,
contact and appointment collaborators are test doubles) and verify the committed audit
through an independent connection.
CI's evidence verifier requires nineteen cases in each MariaDB lane, including
both archive-failure and both demographic-audit invocations by their Surefire identities.
The existing synthetic Growth/Rourke browser suite covers the
packaged save/load/graph/print callers.

## Rehabilitation-study questionnaire slice

The eighteen questionnaires of the 2004 rehabilitation study shared one record
shape: a new form read `demographic` and `rehabStudy2004` through raw JDBC, a saved
version was read with the same metadata loop, and every save was a
`FrmRecordHelp` updatable-result-set insert with no service transaction. Their
`getPrintRecord` methods had no caller.

| Retired record | Table | Prefill besides patient, date and study ID |
| --- | --- | --- |
| `Frm2MinWalkRecord` | `form2MinWalk` | birth date, sex, phone |
| `FrmCESDRecord` | `formCESD` | none |
| `FrmCaregiverRecord` | `formCaregiver` | none |
| `FrmCostQuestionnaireRecord` | `formCostQuestionnaire` | birth date, sex, phone |
| `FrmFallsRecord` | `formFalls` | none |
| `FrmGripStrengthRecord` | `formGripStrength` | none |
| `FrmHomeFallsRecord` | `formHomeFalls` | birth date, sex, phone |
| `FrmIntakeInfoRecord` | `formIntakeInfo` | birth date, sex, phone (stored in its own columns) |
| `FrmInternetAccessRecord` | `formInternetAccess` | birth date, sex, phone |
| `FrmLateLifeFDIDisabilityRecord` | `formLateLifeFDIDisability` | birth date, sex, phone |
| `FrmLateLifeFDIFunctionRecord` | `formLateLifeFDIFunction` | birth date, sex, phone |
| `FrmSF36Record` | `formSF36` | birth date, sex, phone |
| `FrmSF36CaregiverRecord` | `formSF36Caregiver` | birth date, sex, phone |
| `FrmSatisfactionScaleRecord` | `formSatisfactionScale` | birth date, sex, phone |
| `FrmSelfAdministeredRecord` | `formSelfAdministered` | birth date, sex (stored in its own columns) |
| `FrmSelfEfficacyRecord` | `formSelfEfficacy` | birth date, sex, phone |
| `FrmSelfManagementRecord` | `formSelfManagement` | birth date, sex, phone |
| `FrmTreatmentPrefRecord` | `formTreatmentPref` | birth date, sex, phone |

All eighteen are replaced by one router entry, [RehabStudyForm](../src/main/java/io/github/carlos_emr/carlos/form/RehabStudyForm.java),
keyed by the unchanged `form_class` names in [RehabStudyForms](../src/main/java/io/github/carlos_emr/carlos/form/RehabStudyForms.java);
the JSPs, `encounterForm` registrations, view gate and XML import allowlist are unchanged.

- **Entities.** [RehabStudyAssessment](../src/main/java/io/github/carlos_emr/carlos/form/model/rehab/RehabStudyAssessment.java)
  maps the shared header (ID, patient, provider, `formCreated`, `formEdited`,
  `studyID`). One entity per table maps every answer column, 1,364 in all,
  generated from the Flyway baseline by
  [`scripts/forms/generate_rehab_study_entities.py`](../scripts/forms/generate_rehab_study_entities.py)
  (`--check` reports drift). Each entity lists its columns as typed
  [RehabStudyColumn](../src/main/java/io/github/carlos_emr/carlos/form/model/rehab/RehabStudyColumn.java)
  records bound to the field, so the mapper needs no reflection. `tinyint(1)`
  answers are `Byte`, `date` answers `Date`, and every `varchar`, `char`, `int` and
  `double` answer stays `String`: the retired writer bound them all as strings and
  the packaged server's empty `sql_mode` converted them, so the entity keeps that
  conversion on the server instead of reimplementing it. `rehabStudy2004` is mapped
  read-only by `RehabStudyEnrolment`.
  `RehabStudyEntitySchemaContractUnitTest` fails the build when an entity and its
  baseline table disagree (column set, order, type, nullability, field binding) or
  a later migration changes one of these tables.
- **DAO and service.** [RehabStudyFormDao](../src/main/java/io/github/carlos_emr/carlos/commn/dao/forms/RehabStudyFormDao.java)
  is typed by entity class and binds patient and version in every read; the one
  native statement (the optional XML copy) names the entity's own `@Table`.
  [RehabStudyFormService](../src/main/java/io/github/carlos_emr/carlos/form/RehabStudyFormService.java)
  is reached through its Spring proxy. Like Growth, it requires `_form` and
  `_demographic` read access to the patient's record, refuses a missing or
  foreign source version, never reads ID, patient or provider from the post,
  attributes the version to the authenticated provider and writes the optional XML
  copy only after commit with the existing filename and content contract.
- **Values.** [RehabStudyFormValues](../src/main/java/io/github/carlos_emr/carlos/form/RehabStudyFormValues.java)
  keeps the retired contract column by column: a stored 1 is shown as
  `checked='checked'`, anything else as empty; `on` or `checked='checked'` (any
  case) is stored as 1 and any other post, or none, as 0; text is stored as posted
  (NULL when absent) and shown trimmed; dates accept `yyyy/MM/dd` and
  `yyyy-MM-dd`, show `yyyy/MM/dd` and store NULL when unreadable; `formEdited` is
  the save time at whole-second precision, shown as `yyyy-MM-dd HH:mm:ss`. A new
  form's study ID is the patient's lowest `rehabStudy2004` ID (the retired
  unordered query returned InnoDB's primary-key order), otherwise `N/A`.
- **Changed on purpose.** A new form's prefill now reads the master record through
  `DemographicManager` (with its access check and health-care-team audit) instead
  of raw JDBC. Reopening a missing or another patient's version is refused instead
  of showing an empty form, and a post without `studyID` is refused before any
  write instead of failing on the `NOT NULL` column. A non-numeric provider number,
  which the retired writer stored as 0, is refused as in the Growth slice.

`RehabStudyFormDaoIntegrationTest` (67 cases) runs on H2 and on the physical
MariaDB generated, Ontario Flyway and BC Flyway schemas. For every table it saves a
post that gives every column a value through the service and through the retired
writer (`FrmRecordHelp.saveFormRecord`, still used by other forms) and requires
identical stored rows; it then requires the service's read of every column to equal
a frozen copy of the retired reader. It also covers tick round trips through the
router, trusted identifiers, foreign and cross-questionnaire sources, rollback of
earlier writes across questionnaires when a later version is refused, a later
database failure and a failure after flush, the committed demographic audit, study
ID prefill, physical column metadata, and the XML copy (committed rows only,
import filename contract, read and filesystem failures logged without values).
Commits and rollbacks are checked from an independent connection. CI's generated
MariaDB lane selects the suite through `*Dao*IntegrationTest`, and the evidence
verifier requires all 67 cases there; the Flyway lanes of
`.github/workflows/maven-project.yml` list their suites explicitly and need
`RehabStudyFormDaoIntegrationTest` added to run it (it is in the release
qualification lanes of `docs/testing/release-2026.08-verification.json`).

## Consolidated review of #5696 and #5697

This implementation combines the two duplicate Growth slices on the current
`release/2026.08` branch:

| Review finding | Resolution |
| --- | --- |
| #5696 did not validate a submitted source version on save, bypassed locked-patient checks, and required demographic write permission for a clinical form save. | Retain #5697's patient-record access guard, patient-bound source lookup, authenticated provider attribution and demographic read permission. |
| #5697 left `FrmData` Growth reads on generic JDBC and moved shortcut/REST fixtures away from the migrated table. | Retain #5696's typed `FrmData` dispatch and real Growth-DAO shortcut/REST fixtures. |
| #5697 returned untrimmed text and opened an unclosed query result stream. | Preserve the legacy display trim and fully consume the primary-key lookup result list. |
| #5696's generated-schema mapping omitted the physical `TIMESTAMP` column definition; its grouped creation-date aggregate could come from another version. | Retain #5697's explicit timestamp definition and selected-version grouped projection, with physical metadata and differing-creation-date tests. |
| #5697 did not require Growth results in the MariaDB evidence verifier; each PR covered different rollback failures. | Require all nineteen combined persistence cases in generated, ON Flyway and BC Flyway CI, including source rejection, database failure, failure after flush, post-commit archive failures and demographic audit writes. |

Both original PRs remain separate proposals. This Growth slice does not close
#5693 or #2249; the remaining form migrations below are still required.

## Remaining slices

1. The remaining simple single-table records above: retain each form's exact
   prefill, string, checkbox, date and print mappings; add typed entities/DAOs and
   proxy-reached service transactions. Remove a legacy class only after its callers
   move. The rehabilitation-study generator, column records and parity suite are
   the pattern for wide tables.
2. Rourke 2006/2009/2017/2020, BCAR variants, RH and measurement forms: migrate
   their auxiliary tables and related writes atomically, with failure after an
   earlier write and independent-connection evidence on real MariaDB.
3. Finish shared `FrmRecordHelp` users and the additional helpers, then prove
   zero production `LegacyJdbcQuery` callers in `form/`. Coordinate generic
   encounter/eForm/XML-import paths with #5547 and report progress to #2249.
