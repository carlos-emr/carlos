# Exhaustive reference audit for candidate unused files

Method: `rg -n --no-ignore -uu -F` run for each candidate over path, class name, and fully-qualified class name. Excluded generated/documentation noise (`docs/static/javadoc/**`, prior unused reports, `analysis/**`, `.git/**`, `target/**`). Self-file matches removed.

| Candidate file | External textual references | Status |
|---|---:|---|
| `src/main/java/io/github/carlos_emr/carlos/ar2005/impl/ARRecordDocumentImpl.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/ar2005/impl/ARRecordSetDocumentImpl.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/casemgmt/service/impl/DefaultNoteService.java` | 5 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/casemgmt/web/ProviderAccessRight.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/commn/model/MsgDemoMapPK.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/commn/model/inbox/OscarInboxQueryParameters.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/consultations/ConsultationData.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/dashboard/admin/ManageDashboard2Action.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/decisionSupport/web/TestActionW2Action.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/demographic/pageUtil/HRMCreateFile.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/documentManager/ExternalEDocConverter.java` | 1 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/documentManager/data/DocumentUpload2Form.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/jobs/OscarMsgReviewSender.java` | 3 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/jobs/OscarOnCallClinic.java` | 3 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/PathNetController.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/PathNetInfo.java` | 1 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/lab/ca/on/LabResultImport.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/listeners/MyDemographicEventListener.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/managers/DrugLookUpManager.java` | 18 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/managers/WaitListManager.java` | 10 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/match/MatchManagerScheduler.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/messenger/pageUtil/MsgCreateMessageBean.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/model/DefaultCustomFilter.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/prescript/data/RxAllergyData.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/prescript/util/ShowAllSorter.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/prescript/util/TimingOutCallback.java` | 1 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/report/data/VisitReportData.java` | 2 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/RptDrugRecord.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/services/ProviderManagerTickler.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/tickler/AutoTickler.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/ticklers/service/TicklerService.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/web/AppointmentProviderAdminDayUIBean.java` | 6 | REFERENCED |
| `src/main/java/io/github/carlos_emr/carlos/web/Cds4FunctionCode.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/web/CdsManualLineEntry.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/wl/WaitListService_Service.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/wl/prepared/runtime/AbstractPreparedTickler.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/ConsultationsConfigBean.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/NotifyConsultationBean.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/ProcessConsultationBean.java` | 0 | NO EXTERNAL REFERENCES FOUND |
| `src/main/java/io/github/carlos_emr/carlos/www/admin/UserSearchFormBean.java` | 0 | NO EXTERNAL REFERENCES FOUND |

## Evidence (up to 8 hits per file)

### `src/main/java/io/github/carlos_emr/carlos/ar2005/impl/ARRecordDocumentImpl.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/ar2005/impl/ARRecordSetDocumentImpl.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/casemgmt/service/impl/DefaultNoteService.java`
- `DefaultNoteService` → `./src/test/java/io/github/carlos_emr/carlos/casemgmt/service/DefaultNoteServiceTest.java:46:import io.github.carlos_emr.carlos.casemgmt.service.impl.DefaultNoteService;`
- `DefaultNoteService` → `./src/test/java/io/github/carlos_emr/carlos/casemgmt/service/DefaultNoteServiceTest.java:55:public class DefaultNoteServiceTest extends DaoTestFixtures {`
- `DefaultNoteService` → `./src/test/java/io/github/carlos_emr/carlos/casemgmt/service/DefaultNoteServiceTest.java:58:    private NoteService service = SpringUtils.getBean(DefaultNoteService.class);`
- `DefaultNoteService` → `./docs/archive/caisi-integrator-removal-plan.md:189:4. **`casemgmt/service/impl/DefaultNoteService.java`**`
- `DefaultNoteService` → `./docs/archive/caisi-integrator-architecture.md:723:| `casemgmt/service/impl/DefaultNoteService.java` | Remote note integration | Note display |`

### `src/main/java/io/github/carlos_emr/carlos/casemgmt/web/ProviderAccessRight.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/commn/model/MsgDemoMapPK.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/commn/model/inbox/OscarInboxQueryParameters.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/consultations/ConsultationData.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/dashboard/admin/ManageDashboard2Action.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/decisionSupport/web/TestActionW2Action.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/demographic/pageUtil/HRMCreateFile.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/documentManager/ExternalEDocConverter.java`
- `ExternalEDocConverter` → `./pom.xml:1181:        <!-- Apache Commons Exec - used by ExternalEDocConverter -->`

### `src/main/java/io/github/carlos_emr/carlos/documentManager/data/DocumentUpload2Form.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/jobs/OscarMsgReviewSender.java`
- `OscarMsgReviewSender` → `./database/mysql/oscardata.sql:2573:INSERT INTO `OscarJobType` VALUES (null,'OSCAR MSG REVIEW','Sends OSCAR Messages to Residents Supervisors when charts need to be reviewed','io.github.carlos_emr.carlos.jobs.OscarMsgReviewSender',0,now());`
- `OscarMsgReviewSender` → `./database/mysql/updates/update-2016-06-06.sql:44:INSERT INTO `OscarJobType` VALUES (null,'OSCAR MSG REVIEW','Sends OSCAR Messages to Residents Supervisors when charts need to be reviewed','io.github.carlos_emr.carlos.jobs.OscarMsgReviewSender',0,now());`
- `OscarMsgReviewSender` → `./.devcontainer/db/scripts/development.sql:877:INSERT INTO `OscarJobType` VALUES (1,'OSCAR MSG REVIEW','Sends OSCAR Messages to Residents Supervisors when charts need to be reviewed','io.github.carlos_emr.carlos.jobs.OscarMsgReviewSender',0,'2023-07-25 15:54:30'),(2,'OSCAR ON CALL CLINIC','Notifies MRP if patient seen during on-call clinic','io.github.carlos_emr.carlos.jobs.OscarOnCallClinic',0,'2023-07-25 15:54:30');`

### `src/main/java/io/github/carlos_emr/carlos/jobs/OscarOnCallClinic.java`
- `OscarOnCallClinic` → `./.devcontainer/db/scripts/development.sql:877:INSERT INTO `OscarJobType` VALUES (1,'OSCAR MSG REVIEW','Sends OSCAR Messages to Residents Supervisors when charts need to be reviewed','io.github.carlos_emr.carlos.jobs.OscarMsgReviewSender',0,'2023-07-25 15:54:30'),(2,'OSCAR ON CALL CLINIC','Notifies MRP if patient seen during on-call clinic','io.github.carlos_emr.carlos.jobs.OscarOnCallClinic',0,'2023-07-25 15:54:30');`
- `OscarOnCallClinic` → `./database/mysql/oscardata.sql:2587:insert into OscarJobType Values(null,'OSCAR ON CALL CLINIC', 'Notifies MRP if patient seen during on-call clinic','io.github.carlos_emr.carlos.jobs.OscarOnCallClinic',false,now());`
- `OscarOnCallClinic` → `./database/mysql/updates/update-2017-01-31.sql:5:insert into OscarJobType Values(null,'OSCAR ON CALL CLINIC', 'Notifies MRP if patient seen during on-call clinic','io.github.carlos_emr.carlos.jobs.OscarOnCallClinic',false,now());`

### `src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/PathNetController.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/PathNetInfo.java`
- `PathNetInfo` → `./utils/TrackingRegexpCheck_java_jsp.data.xml:957:        <ViolationValue>/src/main/java/ca/openosp/openo/oscarLab/ca/bc/PathNet/PathNetInfo.java:ResultSet rsLab = DBHandler.GetSQL(select_not_signed);</ViolationValue>`

### `src/main/java/io/github/carlos_emr/carlos/lab/ca/on/LabResultImport.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/listeners/MyDemographicEventListener.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/managers/DrugLookUpManager.java`
- `DrugLookUpManager` → `./src/test/java/io/github/carlos_emr/carlos/webserv/conversion/DrugConverterTest.java:31:import io.github.carlos_emr.carlos.managers.DrugLookUpManager;`
- `DrugLookUpManager` → `./src/test/java/io/github/carlos_emr/carlos/webserv/conversion/DrugConverterTest.java:49:        this.drugLookUpManager = new MockDrugLookUpManager();`
- `DrugLookUpManager` → `./src/test/java/io/github/carlos_emr/carlos/webserv/conversion/DrugConverterTest.java:285:        // aspirin will trigger response from MockDrugLookUpManager`
- `DrugLookUpManager` → `./src/test/java/io/github/carlos_emr/carlos/webserv/conversion/DrugConverterTest.java:304:        // aspirin will trigger response from MockDrugLookUpManager`
- `DrugLookUpManager` → `./src/test/java/io/github/carlos_emr/carlos/webserv/conversion/DrugConverterTest.java:322:        // aspirin will trigger response from MockDrugLookUpManager`
- `DrugLookUpManager` → `./src/test/java/io/github/carlos_emr/carlos/webserv/conversion/DrugConverterTest.java:357:    private class MockDrugLookUpManager extends DrugLookUpManager {`
- `DrugLookUpManager` → `./src/test/java/io/github/carlos_emr/carlos/webserv/conversion/DrugConverterTest.java:359:        public MockDrugLookUpManager() {`
- `DrugLookUpManager` → `./utils/TrackingRegexpCheck_java_jsp.data.xml:1156:        <ViolationValue>/src/main/java/ca/openosp/openo/managers/DrugLookUpManager.java:Hashtable drug = dr.getDrug2(id, true);</ViolationValue>`

### `src/main/java/io/github/carlos_emr/carlos/managers/WaitListManager.java`
- `WaitListManager` → `./src/test/java/io/github/carlos_emr/carlos/managers/WaitListManagerTest.java:40:import io.github.carlos_emr.carlos.managers.WaitListManager.AdmissionDemographicPair;`
- `WaitListManager` → `./src/test/java/io/github/carlos_emr/carlos/managers/WaitListManagerTest.java:43:public class WaitListManagerTest {`
- `WaitListManager` → `./src/test/java/io/github/carlos_emr/carlos/managers/WaitListManagerTest.java:47:        String fromAddress = WaitListManager.waitListProperties.getProperty("from_address");`
- `WaitListManager` → `./src/test/java/io/github/carlos_emr/carlos/managers/WaitListManagerTest.java:79:        VelocityContext velocityContext = WaitListManager.getAdmissionVelocityContext(program, notes, startCal.getTime(), endCal.getTime(), admissionDemographicPairs);`
- `WaitListManager` → `./src/test/java/io/github/carlos_emr/carlos/managers/WaitListManagerTest.java:81:        InputStream is = WaitListManagerTest.class.getResourceAsStream("/wait_list_velocity_template.txt");`
- `WaitListManager` → `./src/test/java/io/github/carlos_emr/carlos/managers/WaitListManagerTest.java:86:        is = WaitListManagerTest.class.getResourceAsStream("/wait_list_velocity_template_results.txt");`
- `WaitListManager` → `./src/test/java/io/github/carlos_emr/carlos/managers/WaitListManagerTest.java:101:        VelocityContext velocityContext = WaitListManager.getVacancyVelocityContext(vacancy, "test notes", cal.getTime());`
- `WaitListManager` → `./src/test/java/io/github/carlos_emr/carlos/managers/WaitListManagerTest.java:103:        InputStream is = WaitListManagerTest.class.getResourceAsStream("/wait_list_immediate_vacancy_email_template.txt");`

### `src/main/java/io/github/carlos_emr/carlos/match/MatchManagerScheduler.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/messenger/pageUtil/MsgCreateMessageBean.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/model/DefaultCustomFilter.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/prescript/data/RxAllergyData.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/prescript/util/ShowAllSorter.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/prescript/util/TimingOutCallback.java`
- `TimingOutCallback` → `./utils/TrackingRegexpCheck_java_jsp.data.xml:2490:        <ViolationValue>/src/main/java/ca/openosp/openo/oscarRx/util/TimingOutCallback.java:*   client.executeAsync(methodName, aVector, callback);</ViolationValue>`

### `src/main/java/io/github/carlos_emr/carlos/report/data/VisitReportData.java`
- `VisitReportData` → `./src/main/webapp/oscarReport/oscarReportVisit_vr.jspf:48:<%@ page import="io.github.carlos_emr.carlos.report.data.VisitReportData" %>`
- `VisitReportData` → `./src/main/webapp/oscarReport/oscarReportVisit_vr.jspf:149:    VisitReportData vrd = new VisitReportData();`

### `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/RptDrugRecord.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/services/ProviderManagerTickler.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/tickler/AutoTickler.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/ticklers/service/TicklerService.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/web/AppointmentProviderAdminDayUIBean.java`
- `AppointmentProviderAdminDayUIBean` → `./src/main/webapp/provider/appointmentFormsLinks.jspf:1:<%@page import="io.github.carlos_emr.carlos.web.AppointmentProviderAdminDayUIBean"%>`
- `AppointmentProviderAdminDayUIBean` → `./src/main/webapp/provider/appointmentFormsLinks.jspf:21:			String trimmedEscapedLinkName=StringEscapeUtils.escapeHtml4(AppointmentProviderAdminDayUIBean.getLengthLimitedLinkName(loggedInInfo3, formNameTemp));`
- `AppointmentProviderAdminDayUIBean` → `./src/main/webapp/provider/appointmentFormsLinks.jspf:32:			EForm eForm=AppointmentProviderAdminDayUIBean.getEForms(eFormIdTemp);`
- `AppointmentProviderAdminDayUIBean` → `./src/main/webapp/provider/appointmentFormsLinks.jspf:33:			String trimmedEscapedLinkName=StringEscapeUtils.escapeHtml4(AppointmentProviderAdminDayUIBean.getLengthLimitedLinkName(loggedInInfo3, eForm.getFormName()));`
- `AppointmentProviderAdminDayUIBean` → `./src/main/webapp/provider/appointmentFormsLinks.jspf:43:			String trimmedEscapedLinkName=StringEscapeUtils.escapeHtml4(AppointmentProviderAdminDayUIBean.getLengthLimitedLinkName(loggedInInfo3, quickLink.getName()));`
- `AppointmentProviderAdminDayUIBean` → `./src/main/webapp/provider/appointmentPregnancy.jspf:1:<%@page import="io.github.carlos_emr.carlos.web.AppointmentProviderAdminDayUIBean"%>`

### `src/main/java/io/github/carlos_emr/carlos/web/Cds4FunctionCode.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/web/CdsManualLineEntry.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/wl/WaitListService_Service.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/wl/prepared/runtime/AbstractPreparedTickler.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/ConsultationsConfigBean.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/NotifyConsultationBean.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/ProcessConsultationBean.java`
- No matches found outside the file itself for path/class/FQN patterns.

### `src/main/java/io/github/carlos_emr/carlos/www/admin/UserSearchFormBean.java`
- No matches found outside the file itself for path/class/FQN patterns.

