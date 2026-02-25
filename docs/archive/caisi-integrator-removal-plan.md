# Integrator Removal Plan

> **Scope**: Remove the inter-EMR **integrator** subsystem only.
> CAISI (PMModule, caisicore JSPs, caisi utilities, forms DAOs) is **out of scope**.
>
> **Companion document**: `docs/archive/caisi-integrator-architecture.md`

---

## Scope Definition

### What IS the integrator (REMOVE)

The integrator is the SOAP-based inter-EMR data sharing system. It consists of:

- **`caisi_integrator/` packages** — WS stubs, cached entity DAOs, utilities
- **`PMmodule/caisi_integrator/` package** — Manager/helper classes that call the WS
- **`isIntegratorEnabled()` conditional blocks** — in 58 Java files and 25+ JSPs
- **Integrator-specific models/DAOs** — `RemoteIntegratedDataCopy`, `IntegratorConsent*`,
  `IntegratorControl*`, `IntegratorProgress*` in `commn/` packages
- **Integrator-specific actions** — `IntegratorPush2Action`
- **Integrator-specific JSPs** — admin status pages, remote demographic pages
- **Integrator fields on Facility model** — `integratorEnabled`, `integratorUrl`, etc.

### What is NOT the integrator (KEEP)

| Item | Why it stays |
|------|-------------|
| `applicationContextCaisi.xml` | Contains zero integrator beans — all PMModule/core |
| `caisicore/*.jsp` | Used by `SystemMessage2Action`, `IssueAdmin2Action`, etc. |
| `caisi/CaisiUtil.java` | Generic query-string utility |
| `caisi/IsModuleLoadTag.java` | Generic JSP module-load tag |
| `caisi/OscarMenuExtension.java` | Generic menu extension interface |
| `FacilityMessageDao` / `FacilityMessage` | Used by `OrganizationMessage2Action` |
| `CaisiAccessTypeDao` / `CaisiAccessType` | Used by `RoleCache`, admin JSPs |
| `CaisiFormDataDao`, `CaisiFormQuestionDao` | CAISI forms, not integrator |
| `caisiPMM` login result | PMModule login flow |
| `PMmodule/dao/`, `PMmodule/service/` | PMModule core |
| `database/mysql/caisi/` | Contains PMModule tables mixed with integrator |

---

## Guiding Principles

1. **Build must pass after every phase** — each phase is independently committable
2. **No functional regression** — local EMR features must work identically
3. **Integrator only** — do not touch CAISI/PMModule code that isn't integrator
4. **Outside-in removal** — disconnect coupling first, then delete bulk code last
5. **Test after each phase** — `make install --run-tests` must pass

---

## Phase Overview

| Phase | Description | Risk | Files Changed | Files Deleted |
|-------|-------------|------|---------------|---------------|
| 0 | Preparation and safety net | None | 0 | 0 |
| 1 | Remove integrator code from encounter display actions | Medium | ~15 | 0 |
| 2 | Remove integrator code from managers and services | High | ~12 | 0 |
| 3 | Remove integrator code from data/utility classes | Medium | ~18 | 0 |
| 4 | Remove integrator code from JSPs | Low | ~25 | ~8 |
| 5 | Remove integrator struts mapping and standalone classes | Low | ~3 | ~5 |
| 6 | Delete core integrator packages | Low | 0 | ~360 |
| 7 | Remove integrator DAO/model classes from common | Low | ~5 | ~12 |
| 8 | Clean up Facility model and properties | Low | ~10 | 0 |
| 9 | Clean up tests | Low | ~10 | ~5 |

**Estimated total**: ~95 files modified, ~390 files deleted

---

## Phase 0: Preparation and Safety Net

**Goal**: Ensure a clean baseline.

### Steps

1. **Verify build is green**:
   ```bash
   make clean && make install --run-tests
   ```

2. **Verify integrator is disabled** (it should be by default):
   ```bash
   db-connect
   SELECT id, name, integratorEnabled FROM Facility;
   ```

3. **Tag the commit before starting** (for easy revert):
   ```bash
   git tag pre-integrator-removal
   ```

### Verification
- Build passes
- All tests pass
- No facility has `integratorEnabled = true`

---

## Phase 1: Remove Integrator Code from Encounter Display Actions

**Goal**: Clean up ~15 encounter display actions that conditionally fetch remote data.

**Risk**: Medium — these are core clinical display paths, but the integrator blocks are
dead code guarded by `isIntegratorEnabled()` which defaults to `false`.

### Pattern to Remove

All these actions follow the same pattern:

```java
// REMOVE these imports:
import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.CaisiIntegratorManager;
import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.IntegratorFallBackManager;
import io.github.carlos_emr.carlos.caisi_integrator.ws.CachedDemographic*;

// REMOVE this entire if-block (and any post-processing of remoteData):
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
```

### Files to Modify

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
2. Remove all `import ...caisi_integrator.*` lines
3. Find the `isIntegratorEnabled()` block and delete the entire conditional
4. If `remoteData` was merged into a list, ensure the local-only list still works
5. Remove now-unused variables (`remoteAllergies`, `remoteDrugs`, etc.)
6. Remove any now-unused imports (e.g., `CachedFacility`)

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 2: Remove Integrator Code from Managers and Services

**Goal**: Clean integrator code from core business logic managers.

**Risk**: High — central business logic. Extra care required.

### Files to Modify

1. **`managers/DemographicManagerImpl.java`** (+ interface `DemographicManager.java`)
   - Remove methods: `getRemoteDemographic()`, `copyRemoteDemographic()`,
     `getLinkedDemographics()`, `linkDemographicToRemoteDemographic()`
   - Remove from both interface and implementation
   - Check for callers and remove those calls too

2. **`managers/PrescriptionManagerImpl.java`**
   - Remove integrator-conditional blocks for remote drug/allergy fetching

3. **`casemgmt/service/CaseManagementManagerImpl.java`**
   - Remove remote note/drug/issue fetching blocks
   - Remove `NoteDisplayIntegrator` instantiation

4. **`casemgmt/service/impl/DefaultNoteService.java`**
   - Remove integrator note integration code

5. **`casemgmt/service/CaseManagementPrint.java`**
   - Remove integrator references in print service

6. **`casemgmt/web/CaseManagementView2Action.java`**
   - Remove remote issue/note display code

7. **`prevention/PreventionData.java`** (10+ integrator imports)
   - Remove `getLinkedRemotePreventionData()` method
   - Remove integrator blocks in other methods
   - Remove `RemotePreventionHelper` usage

8. **`prescript/data/RxPatientData.java`**
   - Remove remote drug/allergy data fetching

### Approach

For each file:
1. Read the full file
2. Identify all `...caisi_integrator...` imports and trace their usage
3. Remove the `isIntegratorEnabled()` conditional blocks
4. Remove now-dead methods (especially in DemographicManager interface)
5. Remove unused imports
6. Verify remaining code logic is correct

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 3: Remove Integrator Code from Data/Utility Classes

**Goal**: Clean up remaining utility, helper, and data classes.

**Risk**: Medium.

### Files to Modify

1. `rx/StaticScriptBean.java` — remote drug display
2. `prescript/pageUtil/AllergyHelperBean.java` — remote allergy display
3. `prescript/pageUtil/RxShowAllergy2Action.java` — integrator conditionals
4. `prescription/util/DrugrefUtil.java` — integrator drug reference
5. `documentManager/EDocUtil.java` — remote document utility
6. `documentManager/actions/ManageDocument2Action.java` — remote documents
7. `documentManager/actions/AddEditDocument2Action.java` — ConformanceTestHelper
8. `web/DemographicSearchHelper.java` — remote demographic search
9. `form/FrmLabReq07Record.java` — integrator facility lookup
10. `form/FrmLabReq10Record.java` — integrator facility lookup
11. `lab/ca/all/web/LabDisplayHelper.java` — remote lab display
12. `lab/ca/on/CommonLabResultData.java` — Ontario remote lab integration
13. `messenger/pageUtil/ImportDemographic2Action.java` — integrator import
14. `messenger/pageUtil/MsgViewMessage2Action.java` — integrator message view
15. `messenger/tld/MsgNewMessageTag.java` — integrator message check
16. `webserv/rest/DemographicService.java` — remote demographic REST
17. `webserv/rest/PatientDetailStatusService.java` — integrator status
18. `webserv/rest/conversion/summary/LabsDocsSummary.java` — integrator check
19. `utility/ContextStartupListener.java` — integrator initialization

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 4: Remove Integrator Code from JSPs

**Goal**: Clean up JSP files that reference integrator classes.

**Risk**: Low — JSPs are presentation-only; integrator blocks are dead code.

### Integrator-Specific JSPs to DELETE

These JSPs serve ONLY integrator functionality:

1. `admin/integratorStatus.jsp`
2. `admin/IntegratorStatus.jspf` (if exists)
3. `admin/viewIntegratedCommunity.jsp`
4. `admin/setIntegratorProperties.jsp`
5. `oscarPrevention/display_remote_prevention.jsp`
6. `appointment/copyRemoteDemographic.jsp`
7. `demographic/copyLinkedDemographicInfoAction.jsp`
8. `demographic/DiffRemoteDemographics.jsp`

### JSPs to MODIFY (Remove `isIntegratorEnabled()` Conditional Blocks)

For each JSP, find and remove:
```jsp
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
28. `PMmodule/Admin/Facility/EditFacility.jsp` — integrator config fields only
29. `PMmodule/Admin/Facility/ViewFacility.jsp` — integrator status display only

### Verification
```bash
make clean && make install
server restart && server log  # Check for JSP compilation errors
```

---

## Phase 5: Remove Integrator Struts Mapping and Standalone Classes

**Goal**: Remove the integrator struts action and classes that exist solely for integrator.

**Risk**: Low.

### Struts Mapping to Remove

From `struts.xml`, delete:
```xml
<action name="integrator/IntegratorPush"
        class="io.github.carlos_emr.carlos.commn.web.IntegratorPush2Action"/>
```

**DO NOT touch** the caisicore JSP result paths (SystemMessage, IssueAdmin, etc.) —
those are not integrator.

### Files to Delete

1. `commn/web/IntegratorPush2Action.java` — integrator push admin action
2. `casemgmt/web/NoteDisplayIntegrator.java` — integrator note display
3. `managers/MessengerIntegratorManager.java` — integrator messaging bridge
4. `managers/IntegratorPushManager.java` — push operation management
5. `PMmodule/web/forms/IntegratorPushItem.java` (if exists)
6. `PMmodule/web/forms/IntegratorPushResponse.java` (if exists)

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 6: Delete Core Integrator Packages

**Goal**: Delete the bulk integrator code now that all external references are removed.

**Risk**: Low — by this phase, no code outside these packages should reference them.

### Directories to Delete

```
# WS stubs (~290 files)
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/

# Cached entity DAOs (~45 files)
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/

# Integrator utilities (~8 files)
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/util/

# Any remaining files in caisi_integrator/
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/

# PMModule integrator managers/helpers (~16 files)
src/main/java/io/github/carlos_emr/carlos/PMmodule/caisi_integrator/
```

### DO NOT DELETE

```
# These are NOT integrator:
src/main/java/io/github/carlos_emr/carlos/caisi/          # General CAISI utilities
src/main/java/io/github/carlos_emr/carlos/PMmodule/dao/    # PMModule DAOs
src/main/java/io/github/carlos_emr/carlos/PMmodule/service/ # PMModule services
src/main/webapp/caisicore/                                  # Admin JSPs
```

### Pre-Delete Verification

Before deleting, confirm no remaining references:

```bash
# Must return zero results (excluding the packages themselves)
grep -r "caisi_integrator" src/main/java/ --include="*.java" | \
  grep -v "caisi_integrator/" | wc -l

grep -r "caisi_integrator" src/main/webapp/ --include="*.jsp" | wc -l
```

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 7: Remove Integrator DAO/Model Classes from Common Packages

**Goal**: Remove integrator-specific classes that live in `commn/` packages.

### Files to Delete

```
# Models
commn/model/RemoteIntegratedDataCopy.java
commn/model/IntegratorProgress.java
commn/model/IntegratorProgressItem.java
commn/model/CachedAppointmentComparator.java

# DAOs
commn/dao/RemoteIntegratedDataCopyDao.java
commn/dao/RemoteIntegratedDataCopyDaoImpl.java
commn/dao/IntegratorConsentDao.java
commn/dao/IntegratorConsentDaoImpl.java
commn/dao/IntegratorControlDao.java
commn/dao/IntegratorControlDaoImpl.java
commn/dao/IntegratorProgressDao.java
commn/dao/IntegratorProgressDaoImpl.java
commn/dao/IntegratorProgressItemDao.java
commn/dao/IntegratorProgressItemDaoImpl.java

# Utilities
utility/ObjectMarshalUtil.java
```

### DO NOT DELETE (not integrator)

```
commn/dao/FacilityMessageDao.java          # Used by OrganizationMessage2Action
commn/dao/FacilityMessageDaoImpl.java      # Used by OrganizationMessage2Action
commn/model/FacilityMessage.java           # Used by OrganizationMessage2Action
commn/model/FacilityDemographicPrimaryKey.java  # Evaluate — may be PMModule
commn/dao/CaisiAccessTypeDao*.java         # Used by RoleCache, admin JSPs
commn/dao/CaisiFormDataDao*.java           # CAISI forms, not integrator
commn/dao/CaisiFormQuestionDao*.java       # CAISI forms, not integrator
```

### Files to Modify (Remove Integrator Constants)

- `commn/model/OscarMsgType.java` — remove `INTEGRATOR_TYPE` constant if present
- `commn/model/UserProperty.java` — remove integrator-related property constants
- `utility/SessionConstants.java` — remove integrator session constants

### HBM/JPA Cleanup

- Remove any HBM XML mappings for deleted entities
- Remove deleted entities from `persistence.xml` if listed
- Check `applicationContext.xml` for bean definitions referencing deleted classes

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 8: Clean Up Facility Model and Properties

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

**NOTE**: Database columns remain — Hibernate will ignore unmapped columns. Column drops
are a separate future migration.

### Facility HBM/JPA Mapping

Remove property mappings for the deleted fields from `Facility.hbm.xml` (or JPA
annotations).

### Properties Files — Remove Integrator Keys Only

- `oscarResources_en.properties` — remove keys containing `integrator`
- `oscarResources_es.properties` — same
- `oscarResources_fr.properties` — same
- `oscarResources_pl.properties` — same
- `oscarResources_pt_BR.properties` — same
- `carlos.properties` — remove `INTEGRATOR_*` keys

**DO NOT remove** `caisi_issues_dx.properties` (CAISI, not integrator).

### Configuration Files

- `.devcontainer/development/config/shared/volumes/carlos.properties` — remove
  integrator configuration entries only

### Verification
```bash
make clean && make install --run-tests
```

---

## Phase 9: Clean Up Tests

**Goal**: Remove integrator-related tests and update test base classes.

### Test Files to Delete

```
src/test/.../PMmodule/web/forms/IntegratorPushItemTest.java
src/test/.../commn/dao/IntegratorConsentComplexExitInterviewDaoTest.java
src/test/.../commn/dao/IntegratorConsentDaoTest.java
src/test/.../commn/dao/IntegratorControlDaoTest.java
src/test/.../commn/dao/RemoteIntegratedDataCopyDaoTest.java
```

### DO NOT DELETE (not integrator)

```
src/test/.../commn/dao/CaisiAccessTypeDaoTest.java     # Tests CaisiAccessTypeDao
src/test/.../commn/dao/CaisiFormDataDaoTest.java        # Tests CAISI forms
src/test/.../commn/dao/CaisiFormQuestionDaoTest.java    # Tests CAISI forms
```

### Modern Test Base Classes to Modify

Remove `when(facility.isIntegratorEnabled()).thenReturn(false)` mock setups from:

```
src/test-modern/.../managers/DemographicUnitTestBase.java
src/test-modern/.../managers/PrescriptionUnitTestBase.java
src/test-modern/.../managers/AllergyUnitTestBase.java
src/test-modern/.../managers/AppointmentUnitTestBase.java
src/test-modern/.../managers/LabUnitTestBase.java
src/test-modern/.../managers/MeasurementUnitTestBase.java
src/test-modern/.../managers/PreventionUnitTestBase.java
src/test-modern/.../managers/ScheduleUnitTestBase.java
```

Also remove any test methods that test integrator-specific behavior.

### Final Verification
```bash
make clean && make install --run-tests
make install --run-legacy-tests
```

---

## Post-Removal Verification Checklist

- [ ] `make clean && make install --run-tests` passes
- [ ] `make install --run-legacy-tests` passes
- [ ] `grep -r "caisi_integrator" src/main/ --include="*.java" | wc -l` returns 0
- [ ] `grep -r "IntegratorFallBack" src/main/ | wc -l` returns 0
- [ ] `grep -r "CaisiIntegratorManager" src/main/ | wc -l` returns 0
- [ ] `grep -r "isIntegratorEnabled" src/main/ | wc -l` returns 0
- [ ] Server starts without Spring context errors: `server restart && server log`
- [ ] Login works (both regular and PMM mode)
- [ ] Patient search works
- [ ] Encounter view loads (allergies, medications, issues, notes, labs, preventions)
- [ ] Prescription module works
- [ ] Case management notes load
- [ ] Document management works
- [ ] Lab results display correctly
- [ ] Prevention/immunization tracking works
- [ ] Admin facility edit page loads
- [ ] SystemMessage, FacilityMessage, IssueAdmin still work (caisicore JSPs)

---

## Database Migration (Separate PR, After Code Removal)

Database columns and tables should be dropped in a **future** migration, not during code
removal. This minimizes risk.

### Future Migration Script

```sql
-- database/mysql/updates/update-YYYY-MM-DD-remove-integrator-tables.sql

-- Drop integrator columns from Facility table
ALTER TABLE Facility DROP COLUMN integratorEnabled;
ALTER TABLE Facility DROP COLUMN integratorUrl;
ALTER TABLE Facility DROP COLUMN integratorUser;
ALTER TABLE Facility DROP COLUMN integratorPassword;
ALTER TABLE Facility DROP COLUMN enableIntegratedReferrals;

-- Drop integrator-specific tables (verify each is integrator-only)
DROP TABLE IF EXISTS remote_integrated_data_copy;
DROP TABLE IF EXISTS integrator_progress_item;
DROP TABLE IF EXISTS integrator_progress;
DROP TABLE IF EXISTS IntegratorConsentShareDataMap;
DROP TABLE IF EXISTS IntegratorConsentComplexExitInterview;
DROP TABLE IF EXISTS IntegratorConsent;
DROP TABLE IF EXISTS IntegratorControl;
DROP TABLE IF EXISTS cached_demographic;
DROP TABLE IF EXISTS cached_demographic_allergy;
DROP TABLE IF EXISTS cached_demographic_appointment;
DROP TABLE IF EXISTS cached_demographic_drug;
DROP TABLE IF EXISTS cached_demographic_form;
DROP TABLE IF EXISTS cached_demographic_issue;
DROP TABLE IF EXISTS cached_demographic_note;
DROP TABLE IF EXISTS cached_demographic_prevention;
DROP TABLE IF EXISTS cached_facility;
DROP TABLE IF EXISTS cached_program;
DROP TABLE IF EXISTS cached_provider;
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

**DO NOT drop**: `facility_message` (used by OrganizationMessage2Action), PMModule tables,
case management tables, CAISI form tables, or any other non-integrator table.

---

## Risk Mitigation

### If Something Breaks

1. Check `server log` for Spring context errors
2. Check build output for compilation errors
3. `git diff` to review what changed in the current phase
4. Revert uncommitted: `git checkout -- .`
5. Revert committed: `git revert HEAD`
6. Nuclear option: `git checkout pre-integrator-removal`

### Common Gotchas

1. **Unused import left behind**: Compilation still passes but CodeQL may flag it.
   Run `grep -r "caisi_integrator" src/main/` after each phase.

2. **JSP compilation error**: A JSP still imports a deleted class. These only surface
   at runtime — check `server log` after deployment.

3. **Test mocks `isIntegratorEnabled()`**: After Phase 8 removes the method, tests
   that mock it will fail. Phase 9 handles this.

4. **HBM mapping references deleted entity**: Check all `.hbm.xml` files in Phase 7.

5. **Spring bean references deleted class**: Check `applicationContext*.xml` files.
   Note: `applicationContextCaisi.xml` has NO integrator beans, but other context
   files might.

---

## Quick Reference: Integrator vs Not-Integrator

### DELETE (Integrator)

```
src/main/java/.../caisi_integrator/              # 340+ files (ws/, dao/, util/)
src/main/java/.../PMmodule/caisi_integrator/      # 16 files (managers, helpers)
src/main/java/.../casemgmt/web/NoteDisplayIntegrator.java
src/main/java/.../managers/MessengerIntegratorManager.java
src/main/java/.../managers/IntegratorPushManager.java
src/main/java/.../commn/web/IntegratorPush2Action.java
src/main/java/.../commn/model/RemoteIntegratedDataCopy.java
src/main/java/.../commn/dao/RemoteIntegratedDataCopy*
src/main/java/.../commn/dao/IntegratorConsent*
src/main/java/.../commn/dao/IntegratorControl*
src/main/java/.../commn/dao/IntegratorProgress*
src/main/java/.../utility/ObjectMarshalUtil.java
src/main/webapp/admin/integratorStatus.jsp
src/main/webapp/admin/viewIntegratedCommunity.jsp
src/main/webapp/oscarPrevention/display_remote_prevention.jsp
src/main/webapp/demographic/DiffRemoteDemographics.jsp
src/main/webapp/demographic/copyLinkedDemographicInfoAction.jsp
src/main/webapp/appointment/copyRemoteDemographic.jsp
```

### KEEP (Not Integrator)

```
src/main/java/.../caisi/                         # General CAISI utilities
src/main/java/.../PMmodule/dao/                  # PMModule DAOs
src/main/java/.../PMmodule/service/              # PMModule services
src/main/java/.../PMmodule/web/admin/            # PMModule admin
src/main/java/.../PMmodule/task/                 # PMModule scheduled tasks
src/main/java/.../www/SystemMessage2Action.java  # Admin action
src/main/java/.../www/OrganizationMessage2Action.java  # Admin action
src/main/java/.../www/IssueAdmin2Action.java     # Admin action
src/main/java/.../commn/dao/FacilityMessage*     # Used by OrganizationMessage
src/main/java/.../commn/dao/CaisiAccessType*     # Used by RoleCache
src/main/java/.../commn/dao/CaisiFormData*       # CAISI forms
src/main/java/.../commn/dao/CaisiFormQuestion*   # CAISI forms
src/main/webapp/caisicore/                       # Admin JSPs
src/main/resources/applicationContextCaisi.xml   # All PMModule/core beans
src/main/resources/caisi_issues_dx.properties    # CAISI diagnostic coding
database/mysql/caisi/                            # Mixed — needs careful evaluation
Login2Action caisiPMM result                     # PMModule login flow
```
