# CAISI Integrator Removal Plan

> **Companion document**: `docs/archive/caisi-integrator-architecture.md`
> (full architectural documentation of the integrator as it existed before removal)

---

## Guiding Principles

1. **Build must pass after every phase** - each phase is independently committable
2. **No functional regression** - local EMR features must work identically
3. **Preserve PMModule** - CAISI PMModule (program management) is NOT the integrator
4. **Preserve non-integrator beans** - `applicationContextCaisi.xml` has shared beans
5. **Outside-in removal** - disconnect coupling first, then delete bulk code last
6. **Test after each phase** - `make install --run-tests` must pass

---

## Phase Overview

| Phase | Description | Risk | Files Changed | Files Deleted |
|-------|-------------|------|---------------|---------------|
| 0 | Preparation and safety net | None | 0 | 0 |
| 1 | Relocate shared beans from applicationContextCaisi.xml | Medium | 2-3 | 0 |
| 2 | Remove integrator imports from encounter display actions | Medium | ~15 | 0 |
| 3 | Remove integrator imports from managers and services | High | ~12 | 0 |
| 4 | Remove integrator imports from data/utility classes | Medium | ~15 | 0 |
| 5 | Remove integrator imports from JSPs | Low | ~25 | ~8 |
| 6 | Remove integrator struts mappings and admin pages | Low | 2 | ~5 |
| 7 | Delete NoteDisplayIntegrator and messenger integrator | Low | 3 | 2 |
| 8 | Delete core integrator packages | Low | 0 | ~376 |
| 9 | Remove integrator DAO/model classes from common | Low | 0 | ~15 |
| 10 | Clean up Facility model and properties | Low | ~10 | 0 |
| 11 | Clean up tests and documentation | Low | ~10 | ~7 |

**Estimated total**: ~90 files modified, ~413 files deleted

---

## Phase 0: Preparation and Safety Net

**Goal**: Ensure a clean baseline and understand the current state.

### Steps

1. **Verify build is green**:
   ```bash
   make clean && make install --run-tests
   ```

2. **Verify integrator is disabled** (it should be by default):
   ```bash
   # Connect to database and confirm no facility has integrator enabled
   db-connect
   SELECT id, name, integratorEnabled FROM Facility;
   ```

3. **Create a tracking issue** for the removal work, referencing this plan.

4. **Tag the commit before starting** (for easy revert):
   ```bash
   git tag pre-integrator-removal
   ```

### Verification
- Build passes
- All tests pass
- No facility has `integratorEnabled = true`

---

## Phase 1: Relocate Shared Beans from applicationContextCaisi.xml

**Goal**: Separate integrator-specific content from shared beans so the file can
eventually be deleted.

**Risk**: Medium - incorrect bean relocation could break Spring context loading.

### Why This Is First

`applicationContextCaisi.xml` is loaded via the glob `classpath:applicationContext*.xml`
in `web.xml`. It contains beans for both the integrator AND for general PMModule/core
functionality. We need to move the non-integrator beans to the main
`applicationContext.xml` before we can safely delete this file.

### Non-Integrator Beans to Relocate

These beans MUST be moved to `applicationContext.xml`:

```xml
<!-- Measurement flowsheets (core clinical functionality) -->
<bean id="measurementTemplateFlowSheet"
      class="...MeasurementTemplateFlowSheetConfig">
    <!-- 11 flowsheet XML configurations -->
</bean>

<!-- DAOs -->
<bean id="formsDAO" class="...FormsDAOImpl" autowire="byName" />
<bean id="populationReportDao" class="...PopulationReportDaoImpl" autowire="byName" />

<!-- Managers -->
<bean id="issueAdminManager" class="...IssueAdminManager" autowire="byName" />
<bean id="oscarSecurityManager" class="...OscarSecurityManagerImpl" autowire="byName" />
<bean id="formsManagerCaisi" class="...FormsManagerImpl" autowire="byName" />
<bean id="populationReportManager" class="...PopulationReportManager" autowire="byName">
    <property name="issueDAO" ref="IssueDAO" />
    <property name="populationReportDao" ref="populationReportDao" />
</bean>

<!-- Scheduled tasks (PMModule, NOT integrator) -->
<bean id="scheduledErProgramDischargeTask" class="...ErProgramDischargeTask">
    <property name="admissionManager" ref="admissionManager" />
    <property name="providerManager" ref="providerManager" />
</bean>
<!-- + ScheduledExecutorFactoryBean for ER discharge (every 5 min) -->

<bean id="scheduledAnonymousClientDischargeTask"
      class="...AnonymousClientDischargeTask" />
<!-- + ScheduledExecutorFactoryBean for anonymous discharge (every hour) -->
<!-- + schedulerCaisi bean -->

<!-- Component scans (PMModule) -->
<context:component-scan base-package="io.github.carlos_emr.carlos.PMmodule.dao" />
<context:component-scan base-package="io.github.carlos_emr.carlos.PMmodule.service" />
<context:component-scan base-package="io.github.carlos_emr.carlos.PMmodule.web.admin" />
```

### Steps

1. Read `src/main/resources/applicationContext.xml` to understand its structure
2. Add all non-integrator beans from `applicationContextCaisi.xml` to `applicationContext.xml`
3. Remove those beans from `applicationContextCaisi.xml`, leaving it empty (or with a
   comment that it will be deleted)
4. **Alternatively**: Rename `applicationContextCaisi.xml` to
   `applicationContextPMModule.xml` and remove integrator-specific content
5. Build and test

### Verification
```bash
make clean && make install --run-tests
server restart && server log  # Check for Spring context errors
```

---

## Phase 2: Remove Integrator Imports from Encounter Display Actions

**Goal**: Clean up the ~15 encounter display actions that conditionally fetch remote data.

**Risk**: Medium - these are core clinical display paths.

### Pattern to Remove

All these actions follow the same pattern:

```java
// REMOVE THIS ENTIRE BLOCK:
import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.CaisiIntegratorManager;
import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.IntegratorFallBackManager;
import io.github.carlos_emr.carlos.caisi_integrator.ws.CachedDemographic*;

// Inside the action method, REMOVE the integrator if-block:
if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
    try {
        if (!CaisiIntegratorManager.isIntegratorOffline(session)) {
            remoteData = CaisiIntegratorManager.getDemographicWs(...)
                .getLinkedCachedDemographic*(...);
        }
    } catch (Exception e) {
        CaisiIntegratorManager.checkForConnectionError(session, e);
    }
    if (CaisiIntegratorManager.isIntegratorOffline(session)) {
        remoteData = IntegratorFallBackManager.getRemote*(...);
    }
}
// ALSO REMOVE any code that processes remoteData after the block
```

### Files to Modify (in order)

1. `encounter/pageUtil/EctDisplayAllergy2Action.java`
2. `encounter/pageUtil/EctDisplayRx2Action.java`
3. `encounter/pageUtil/EctDisplayPrevention2Action.java`
4. `encounter/pageUtil/EctDisplayIssues2Action.java`
5. `encounter/pageUtil/EctDisplayIssuesAction.java`
6. `encounter/pageUtil/EctDisplayResolvedIssues2Action.java`
7. `encounter/pageUtil/EctDisplayResolvedIssuesAction.java`
8. `encounter/pageUtil/EctDisplayLabAction2.java`
9. `encounter/pageUtil/EctDisplayLabAction22Action.java`
10. `encounter/pageUtil/EctDisplayDocs2Action.java`
11. `encounter/pageUtil/EctDisplayMeasurements2Action.java`
12. `encounter/data/EctFormData.java`
13. `encounter/oscarMeasurements/bean/EctMeasurementsDataBeanHandler.java`
14. `encounter/oscarMeasurements/pageUtil/EctSetupDisplayHistory2Action.java`
15. `encounter/oscarMeasurements/pageUtil/EctSetupHistoryIndex2Action.java`

### Approach for Each File

1. Read the file
2. Remove all `import io.github.carlos_emr.carlos.caisi_integrator.*` lines
3. Remove all `import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.*` lines
4. Find the `isIntegratorEnabled()` block and delete the entire conditional
5. If the `remoteData` variable was used later (e.g., merged into a list), ensure
   the local-only list is still returned correctly
6. Remove any now-unused variables (`remoteAllergies`, `remoteDrugs`, etc.)
7. Remove any now-unused imports (e.g., `CachedFacility` if only used for integrator)

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 3: Remove Integrator Imports from Managers and Services

**Goal**: Clean integrator code from core business logic managers.

**Risk**: High - these are central business logic classes. Extra care required.

### Files to Modify

1. **`managers/DemographicManagerImpl.java`** (+ interface `DemographicManager.java`)
   - Remove: `getRemoteDemographic()`, `copyRemoteDemographic()`,
     `getLinkedDemographics()`, `linkDemographicToRemoteDemographic()`
   - These methods should be removed from both interface and implementation
   - Check for callers of these methods (JSPs, other actions) and remove those calls

2. **`managers/PrescriptionManagerImpl.java`**
   - Remove integrator-conditional blocks for remote drug/allergy fetching

3. **`managers/MessagingManagerImpl.java`**
   - Remove integrator-conditional messaging blocks

4. **`casemgmt/service/CaseManagementManagerImpl.java`**
   - Remove remote note/drug/issue fetching blocks
   - Remove `NoteDisplayIntegrator` instantiation

5. **`casemgmt/service/impl/DefaultNoteService.java`**
   - Remove integrator note integration code

6. **`casemgmt/service/CaseManagementPrint.java`**
   - Remove integrator references in print service

7. **`casemgmt/web/CaseManagementView2Action.java`**
   - Remove remote issue/note display code

8. **`prevention/PreventionData.java`** (10+ integrator imports)
   - Remove `getLinkedRemotePreventionData()` method
   - Remove integrator blocks in other methods
   - Remove `RemotePreventionHelper` usage

9. **`prescript/data/RxPatientData.java`**
   - Remove remote drug/allergy data fetching

10. **`prevention/reports/FluReport.java`**
    - Remove integrator-conditional flu reporting

### Approach

For each file:
1. Read the full file to understand context
2. Identify all integrator imports and trace their usage
3. Remove integrator conditional blocks
4. Remove now-dead methods (especially in DemographicManager interface)
5. Remove unused imports
6. Verify the remaining code logic is correct

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 4: Remove Integrator Imports from Data/Utility Classes

**Goal**: Clean up remaining utility, helper, and data classes.

**Risk**: Medium - these are helper classes used across the application.

### Files to Modify

1. **`rx/StaticScriptBean.java`** - Remove remote drug display
2. **`prescript/pageUtil/AllergyHelperBean.java`** - Remove remote allergy display
3. **`prescript/pageUtil/RxShowAllergy2Action.java`** - Remove integrator conditionals
4. **`prescription/util/DrugrefUtil.java`** - Remove integrator drug reference
5. **`documentManager/EDocUtil.java`** - Remove remote document utility
6. **`documentManager/actions/ManageDocument2Action.java`** - Remove remote documents
7. **`documentManager/actions/AddEditDocument2Action.java`** - Remove ConformanceTestHelper
8. **`web/DemographicSearchHelper.java`** - Remove remote demographic search
9. **`form/FrmLabReq07Record.java`** - Remove integrator facility lookup
10. **`form/FrmLabReq10Record.java`** - Remove integrator facility lookup
11. **`lab/ca/all/web/LabDisplayHelper.java`** - Remove remote lab display
12. **`lab/ca/on/CommonLabResultData.java`** - Remove Ontario lab integration
13. **`messenger/pageUtil/ImportDemographic2Action.java`** - Remove integrator imports
14. **`messenger/pageUtil/MsgViewMessage2Action.java`** - Remove integrator message view
15. **`messenger/tld/MsgNewMessageTag.java`** - Remove integrator message check
16. **`webserv/rest/DemographicService.java`** - Remove remote demographic REST endpoints
17. **`webserv/rest/PatientDetailStatusService.java`** - Remove integrator status
18. **`webserv/rest/conversion/summary/LabsDocsSummary.java`** - Remove integrator check
19. **`utility/ContextStartupListener.java`** - Remove integrator initialization

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 5: Remove Integrator Code from JSPs

**Goal**: Clean up all JSP files that reference integrator classes.

**Risk**: Low - JSPs are presentation-only; integrator blocks are dead code since
`integratorEnabled` is false.

### Integrator-Specific JSPs to DELETE

These JSPs are ONLY used for integrator functionality:

1. `admin/integratorStatus.jsp`
2. `admin/integratorPushStatus.jsp` (if it exists as separate file)
3. `admin/IntegratorStatus.jspf`
4. `admin/viewIntegratedCommunity.jsp`
5. `admin/setIntegratorProperties.jsp`
6. `oscarPrevention/display_remote_prevention.jsp`
7. `appointment/copyRemoteDemographic.jsp`
8. `demographic/copyLinkedDemographicInfoAction.jsp`
9. `demographic/DiffRemoteDemographics.jsp`

### JSPs to MODIFY (Remove Integrator Conditional Blocks)

For each JSP, find and remove the `isIntegratorEnabled()` blocks:

```jsp
<%-- REMOVE blocks like this: --%>
<% if (currentFacility.isIntegratorEnabled()) { %>
    <%-- integrator-specific UI elements --%>
<% } %>
```

Files:
1. `demographic/demographicsearchresults.jsp`
2. `demographic/demographicsearch2apptresults.jsp`
3. `demographic/demographicappthistory.jsp`
4. `demographic/demographiccontrol.jsp`
5. `demographic/demographiceditdemographic.jsp`
6. `demographic/followUp.jsp`
7. `demographic/followUpSelection.jsp`
8. `demographic/zdemographicfulltitlesearch.jsp`
9. `casemgmt/newEncounterHeader.jsp`
10. `casemgmt/prescriptions.jsp`
11. `casemgmt/ChartNotesAjax.jsp`
12. `oscarRx/SearchDrug.jsp`
13. `oscarRx/ListDrugs.jsp`
14. `oscarRx/StaticScript.jsp`
15. `oscarRx/StaticScript2.jsp`
16. `oscarRx/ShowAllergies.jsp`
17. `oscarRx/DisplayRxRecord.jsp`
18. `lab/DemographicLab.jsp`
19. `lab/CA/ALL/labDisplay.jsp`
20. `lab/CA/ON/labValues.jsp`
21. `form/formlabreq07.jsp`
22. `form/formlabreq10.jsp`
23. `documentManager/documentReport.jsp`
24. `oscarEncounter/formlist.jsp`
25. `oscarEncounter/oscarMeasurements/DisplayHistory.jsp`
26. `oscarEncounter/oscarMeasurements/TemplateFlowSheetPage.jspf`
27. `messenger/ViewMessage.jsp`

### Rourke Growth Chart JSPs

Check and clean:
1. `form/formrourke2009p1-4.jsp`
2. `form/formrourke2017p1-4.jsp`
3. `form/formRourke2020p1-4.jsp`

### Verification
```bash
make clean && make install
server restart && server log  # Check for JSP compilation errors
```

---

## Phase 6: Remove Integrator Struts Mappings and Admin Pages

**Goal**: Clean up struts.xml and relocate caisicore JSPs.

**Risk**: Low.

### Steps

1. **Remove from struts.xml**:
   ```xml
   <!-- DELETE this action -->
   <action name="integrator/IntegratorPush"
           class="io.github.carlos_emr.carlos.commn.web.IntegratorPush2Action"/>
   ```

2. **Relocate caisicore/ JSPs** (used by non-integrator actions):
   - Move `caisicore/*.jsp` to `admin/` (or appropriate location)
   - Update struts.xml result paths:
     ```xml
     <!-- Change /caisicore/SystemMessage.jsp to /admin/SystemMessage.jsp -->
     <!-- etc. for all caisicore references -->
     ```

3. **Remove CSS**: Delete `css/caisi_css.jsp` if integrator-only.

4. **Keep login flow**: The `caisiPMM` result in Login2Action is PMModule, not integrator.

### Verification
```bash
make clean && make install
```

---

## Phase 7: Delete NoteDisplayIntegrator and Messenger Integrator

**Goal**: Remove the two classes that exist solely for integrator functionality but live
outside the integrator packages.

### Files to Delete

1. **`casemgmt/web/NoteDisplayIntegrator.java`**
   - Implements `NoteDisplay` interface
   - Only instantiated when integrator data exists
   - After Phase 3 removes all callers, this class has no references

2. **`managers/MessengerIntegratorManager.java`**
   - Entire class is integrator messaging bridge
   - All methods are integrator-specific
   - After Phase 3/4 removes all callers, this class has no references

3. **`commn/web/IntegratorPush2Action.java`**
   - Admin action for push operations (struts mapping removed in Phase 6)

### Files to Modify

1. **`managers/IntegratorPushManager.java`** - Delete entire class
2. **`PMmodule/web/forms/IntegratorPushItem.java`** - Delete if exists
3. **`PMmodule/web/forms/IntegratorPushResponse.java`** - Delete if exists

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 8: Delete Core Integrator Packages

**Goal**: Delete the bulk integrator code now that all external references are removed.

**Risk**: Low - by this phase, no code should reference these packages.

### Directories to Delete

```bash
# Main integrator packages (~340 files)
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/util/
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/  # remaining files

# PMModule integrator classes (~16 files)
src/main/java/io/github/carlos_emr/carlos/PMmodule/caisi_integrator/

# CAISI utility classes (3 files - evaluate individually)
src/main/java/io/github/carlos_emr/carlos/caisi/CaisiUtil.java
src/main/java/io/github/carlos_emr/carlos/caisi/IsModuleLoadTag.java
src/main/java/io/github/carlos_emr/carlos/caisi/OscarMenuExtension.java
```

### Pre-Delete Check

Before deleting, verify no remaining references:

```bash
# Search for any remaining imports - this MUST return zero results
grep -r "caisi_integrator" src/main/java/ --include="*.java" | \
  grep -v "caisi_integrator/" | wc -l

grep -r "PMmodule.caisi_integrator" src/main/java/ --include="*.java" | \
  grep -v "caisi_integrator/" | wc -l
```

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 9: Remove Integrator DAO/Model Classes from Common Packages

**Goal**: Remove integrator-specific classes that live in `commn/` packages.

### Files to Delete

```
commn/model/RemoteIntegratedDataCopy.java
commn/model/FacilityDemographicPrimaryKey.java
commn/model/FacilityMessage.java
commn/model/IntegratorProgress.java
commn/model/IntegratorProgressItem.java
commn/model/CachedAppointmentComparator.java

commn/dao/RemoteIntegratedDataCopyDao.java
commn/dao/RemoteIntegratedDataCopyDaoImpl.java
commn/dao/FacilityMessageDao.java
commn/dao/FacilityMessageDaoImpl.java
commn/dao/IntegratorConsentDao.java
commn/dao/IntegratorConsentDaoImpl.java
commn/dao/IntegratorControlDao.java
commn/dao/IntegratorControlDaoImpl.java
commn/dao/IntegratorProgressDao.java
commn/dao/IntegratorProgressDaoImpl.java
commn/dao/IntegratorProgressItemDao.java
commn/dao/IntegratorProgressItemDaoImpl.java

utility/ObjectMarshalUtil.java
```

### Files to Modify

- **`commn/model/OscarMsgType.java`** - Remove `INTEGRATOR_TYPE` constant
- **`commn/model/UserProperty.java`** - Remove integrator-related property constants
- **`commn/model/Drug.java`** - Remove integrator display properties if any
- **`commons/KeyConstants.java`** - Remove integrator feature flag constants
- **`utility/SessionConstants.java`** - Remove integrator session constants

### HBM/JPA Cleanup

- Remove any HBM XML mappings for deleted entities
- Remove entities from `persistence.xml` if listed
- Check `applicationContext.xml` for any bean definitions referencing deleted classes

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 10: Clean Up Facility Model and Properties

**Goal**: Remove integrator fields from the Facility model and clean up properties.

### Facility Model

**File**: `commn/model/Facility.java`

Remove these fields and their getters/setters:

```java
private boolean integratorEnabled = false;        // REMOVE
private String integratorUrl = null;              // REMOVE
private String integratorUser = null;             // REMOVE
private String integratorPassword = null;         // REMOVE
private boolean enableIntegratedReferrals = true; // REMOVE
```

**CAUTION**: The Facility table has database columns for these fields. Removing the Java
fields is safe (Hibernate will ignore unmapped columns). The database columns can be
dropped in a later migration, or left as-is since they're all null/false.

### Facility HBM/JPA Mapping

Update `Facility.hbm.xml` (or JPA annotations) to remove the property mappings for the
deleted fields.

### Properties Files

**Files to modify**:
- `oscarResources_en.properties` - Remove integrator-related keys
- `oscarResources_es.properties` - Remove integrator-related keys
- `oscarResources_fr.properties` - Remove integrator-related keys
- `oscarResources_pl.properties` - Remove integrator-related keys
- `oscarResources_pt_BR.properties` - Remove integrator-related keys
- `oscar_mcmaster.properties` - Remove `INTEGRATOR_*` keys
- `caisi_issues_dx.properties` - Evaluate if still needed (may be PMModule)

### Configuration Files

- **`.devcontainer/development/config/shared/volumes/oscar.properties`** - Remove
  integrator configuration entries

### Admin UI

- **`PMmodule/Admin/Facility/EditFacility.jsp`** - Remove integrator configuration fields
- **`PMmodule/Admin/Facility/ViewFacility.jsp`** - Remove integrator status display

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 11: Clean Up Tests and Documentation

**Goal**: Remove integrator-related tests and update documentation.

### Test Files to Delete

```
src/test/java/.../PMmodule/web/forms/IntegratorPushItemTest.java
src/test/java/.../commn/dao/IntegratorConsentComplexExitInterviewDaoTest.java
src/test/java/.../commn/dao/IntegratorConsentDaoTest.java
src/test/java/.../commn/dao/IntegratorControlDaoTest.java
src/test/java/.../commn/dao/RemoteIntegratedDataCopyDaoTest.java
src/test/java/.../commn/dao/CaisiAccessTypeDaoTest.java
src/test/java/.../commn/dao/CaisiFormDataDaoTest.java
src/test/java/.../commn/dao/CaisiFormQuestionDaoTest.java
```

### Modern Test Files to Modify

Update test base classes that mock `isIntegratorEnabled()`:

```
src/test-modern/java/.../managers/DemographicManagerUnitTest.java
src/test-modern/java/.../managers/DemographicUnitTestBase.java
src/test-modern/java/.../managers/PrescriptionManagerUnitTest.java
src/test-modern/java/.../managers/PrescriptionUnitTestBase.java
src/test-modern/java/.../managers/AllergyUnitTestBase.java
src/test-modern/java/.../managers/AppointmentUnitTestBase.java
src/test-modern/java/.../managers/LabUnitTestBase.java
src/test-modern/java/.../managers/MeasurementUnitTestBase.java
src/test-modern/java/.../managers/PreventionUnitTestBase.java
src/test-modern/java/.../managers/ScheduleUnitTestBase.java
```

In these files, remove:
- `when(facility.isIntegratorEnabled()).thenReturn(false)` mock setups
- Any test methods that test integrator-specific behavior

### Documentation Updates

1. **`CLAUDE.md`** - Remove references to `applicationContextCaisi.xml` as containing
   integrator beans (update the Spring Configuration section)
2. **`docs/integrator-system-architecture.md`** - Add deprecation notice pointing to
   `docs/archive/caisi-integrator-architecture.md`
3. **`docs/test/test-writing-best-practices.md`** - Remove integrator mock examples

### Delete applicationContextCaisi.xml

If all beans were relocated in Phase 1, delete:
```
src/main/resources/applicationContextCaisi.xml
```

Or if it was renamed to `applicationContextPMModule.xml`, keep the renamed version.

### Final Verification
```bash
make clean && make install --run-tests
# Run full test suite including legacy tests
make install --run-legacy-tests
```

---

## Post-Removal Verification Checklist

After all phases are complete:

- [ ] `make clean && make install --run-tests` passes
- [ ] `make install --run-legacy-tests` passes
- [ ] `grep -r "caisi_integrator" src/main/ --include="*.java" | wc -l` returns 0
- [ ] `grep -r "IntegratorFallBack" src/main/ | wc -l` returns 0
- [ ] `grep -r "CaisiIntegratorManager" src/main/ | wc -l` returns 0
- [ ] Server starts without Spring context errors: `server restart && server log`
- [ ] Login works (both regular and PMM mode)
- [ ] Patient search works
- [ ] Encounter view loads (allergies, medications, issues, notes, labs, preventions)
- [ ] Prescription module works
- [ ] Case management notes load
- [ ] Document management works
- [ ] Lab results display correctly
- [ ] Prevention/immunization tracking works
- [ ] Admin facility edit page loads (without integrator fields)

---

## Database Migration (Separate PR, After Code Removal)

The database columns and tables can be dropped in a future migration. This is intentionally
NOT part of the code removal to minimize risk.

### Future Migration Script

```sql
-- database/mysql/updates/update-YYYY-MM-DD-remove-integrator-tables.sql

-- Drop integrator columns from Facility table
ALTER TABLE Facility DROP COLUMN integratorEnabled;
ALTER TABLE Facility DROP COLUMN integratorUrl;
ALTER TABLE Facility DROP COLUMN integratorUser;
ALTER TABLE Facility DROP COLUMN integratorPassword;
ALTER TABLE Facility DROP COLUMN enableIntegratedReferrals;

-- Drop integrator-specific tables (evaluate each carefully)
DROP TABLE IF EXISTS remote_integrated_data_copy;
DROP TABLE IF EXISTS integrator_progress_item;
DROP TABLE IF EXISTS integrator_progress;
DROP TABLE IF EXISTS IntegratorConsentShareDataMap;
DROP TABLE IF EXISTS IntegratorConsentComplexExitInterview;
DROP TABLE IF EXISTS IntegratorConsent;
DROP TABLE IF EXISTS IntegratorControl;
DROP TABLE IF EXISTS facility_message;
DROP TABLE IF EXISTS cached_demographic;
DROP TABLE IF EXISTS cached_demographic_allergy;
-- ... (all cached_* tables)
DROP TABLE IF EXISTS DemographicLink;
DROP TABLE IF EXISTS ClientLink;
DROP TABLE IF EXISTS DigitalSignature;
DROP TABLE IF EXISTS EventLog;
DROP TABLE IF EXISTS ImportLog;
DROP TABLE IF EXISTS ProviderCommunication;
DROP TABLE IF EXISTS SiteUser;
DROP TABLE IF EXISTS SystemProperties;
DROP TABLE IF EXISTS HomelessPopulationReport;
DROP TABLE IF EXISTS HnrDataValidation;
```

**NOTE**: Do NOT drop tables that are used by PMModule (admission, program, casemgmt, etc.).
Carefully verify each table before dropping.

---

## Risk Mitigation

### If Something Breaks

1. Check `server log` for Spring context errors
2. Check build output for compilation errors
3. Use `git diff` to review what changed in the current phase
4. Revert the current phase: `git checkout -- .` (if uncommitted)
5. If committed, revert the commit: `git revert HEAD`
6. As a last resort: `git checkout pre-integrator-removal`

### Common Gotchas

1. **Spring bean not found**: A non-integrator class depends on a bean defined in
   `applicationContextCaisi.xml` that wasn't relocated. Check Phase 1.

2. **JSP compilation error**: A JSP imports a deleted class. Search JSPs for remaining
   integrator imports.

3. **Test failure**: A test mocks `isIntegratorEnabled()` but the method no longer exists.
   Update the test base class.

4. **HBM mapping error**: An HBM file references a deleted entity. Check all `.hbm.xml`
   files for integrator entity references.

5. **caisicore JSP 404**: A struts mapping still points to `/caisicore/*.jsp` after
   relocation. Update struts.xml.

---

## Appendix: Quick Reference - What to Delete vs Keep

### DELETE (Integrator-Specific)

```
src/main/java/.../caisi_integrator/          # 340+ files
src/main/java/.../PMmodule/caisi_integrator/  # 16 files
src/main/java/.../casemgmt/web/NoteDisplayIntegrator.java
src/main/java/.../managers/MessengerIntegratorManager.java
src/main/java/.../managers/IntegratorPushManager.java
src/main/java/.../commn/web/IntegratorPush2Action.java
src/main/java/.../commn/model/RemoteIntegratedDataCopy.java
src/main/java/.../commn/dao/RemoteIntegratedDataCopy*
src/main/java/.../commn/dao/IntegratorConsent*
src/main/java/.../commn/dao/IntegratorControl*
src/main/java/.../commn/dao/IntegratorProgress*
src/main/java/.../commn/dao/FacilityMessage*
src/main/java/.../utility/ObjectMarshalUtil.java
src/main/webapp/admin/integratorStatus.jsp
src/main/webapp/admin/viewIntegratedCommunity.jsp
src/main/webapp/oscarPrevention/display_remote_prevention.jsp
src/main/webapp/demographic/DiffRemoteDemographics.jsp
src/main/webapp/demographic/copyLinkedDemographicInfoAction.jsp
src/main/webapp/appointment/copyRemoteDemographic.jsp
```

### KEEP (NOT Integrator-Specific)

```
src/main/java/.../PMmodule/dao/             # PMModule DAOs
src/main/java/.../PMmodule/service/         # PMModule services
src/main/java/.../PMmodule/web/admin/       # PMModule admin
src/main/java/.../PMmodule/task/            # PMModule scheduled tasks
src/main/java/.../www/SystemMessage2Action.java
src/main/java/.../www/DefaultEncounterIssue2Action.java
src/main/java/.../www/OrganizationMessage2Action.java
src/main/java/.../www/IssueAdmin2Action.java
src/main/webapp/caisicore/                  # RELOCATE to admin/
database/mysql/caisi/initcaisi.sql          # Contains PMModule tables too
Login2Action caisiPMM result                # PMModule login flow
```
