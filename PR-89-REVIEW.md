# PR #89 Exhaustive Review: Hibernate 6 Positional Parameter Migration

**PR**: #89 (`chore/hibernate6-prep-positional-params`)
**Reviewed**: 2026-02-25
**PR Head**: `1ac9373604` (2026-02-11)
**Develop Head**: `bd63456be0` (2026-02-25)
**Scope**: `?0` → `?1` positional parameter migration for Hibernate 6 compatibility

---

## Executive Summary

**VERDICT: PR #89 cannot be merged as-is.** The core `?0` → `?1` positional parameter migration and the `HqlQueryHelper` utility are well-designed, but the PR:

1. **Introduces 4 confirmed bugs** in HQL property names and null safety
2. **Removes Alias search** from 5 patient search methods (functional regression)
3. **Re-introduces 569 deleted files** (70% of the diff) from modules removed on develop
4. **Reverts critical develop features** (CSRFGuard 4.5 → 3.1, Drools 7.x → 2.0, CVE fixes)
5. **738 merge conflicts** due to diverged histories

**Recommendation**: Cherry-pick the `?0` → `?1` migration from the 27 DAO files into a fresh branch off develop, fixing the bugs identified below. Discard all other changes.

---

## Part 1: Migration Correctness (The `?0` → `?1` Work)

### What's Good

- **HqlQueryHelper utility** (`io.github.carlos_emr.carlos.utility.HqlQueryHelper`): Well-designed bridge for Hibernate 5→6 migration. Correctly uses 1-based `setParameter(i + 1, params[i])`, handles named parameters, collections via `setParameterList`, pagination, and Spring exception translation.
- **Core parameter shifting**: Every `?0` became `?1`, every `?1` became `?2`, etc. **No off-by-one errors found** in the parameter numbering itself.
- **No remaining `?0` references** in functional code on the PR branch.
- **SQL injection fixes**: 15+ instances of string concatenation in HQL replaced with proper parameterization (see Part 3).

### 27 DAO Files Needing Migration on Current Develop

These are the files that still use `?0` positional parameters on develop and need the migration:

| # | File | Module |
|---|------|--------|
| 1 | `PMmodule/dao/ClientReferralDAOImpl.java` | Program Management |
| 2 | `PMmodule/dao/DefaultRoleAccessDAOImpl.java` | Program Management |
| 3 | `PMmodule/dao/FormsDAOImpl.java` | Program Management |
| 4 | `PMmodule/dao/ProgramClientRestrictionDAOImpl.java` | Program Management |
| 5 | `PMmodule/dao/ProgramClientStatusDAOImpl.java` | Program Management |
| 6 | `PMmodule/dao/ProgramFunctionalUserDAOImpl.java` | Program Management |
| 7 | `PMmodule/dao/ProgramProviderDAOImpl.java` | Program Management |
| 8 | `PMmodule/dao/ProgramQueueDaoImpl.java` | Program Management |
| 9 | `PMmodule/dao/ProgramSignatureDaoImpl.java` | Program Management |
| 10 | `PMmodule/dao/ProgramTeamDAOImpl.java` | Program Management |
| 11 | `PMmodule/dao/ProviderDaoImpl.java` | Program Management |
| 12 | `PMmodule/dao/SecUserRoleDaoImpl.java` | Program Management |
| 13 | `casemgmt/dao/CaseManagementCPPDAOImpl.java` | Case Management |
| 14 | `casemgmt/dao/CaseManagementIssueDAOImpl.java` | Case Management |
| 15 | `casemgmt/dao/CaseManagementNoteDAOImpl.java` | Case Management |
| 16 | `casemgmt/dao/CaseManagementNoteExtDAOImpl.java` | Case Management |
| 17 | `casemgmt/dao/CaseManagementNoteLinkDAOImpl.java` | Case Management |
| 18 | `casemgmt/dao/ClientImageDAOImpl.java` | Case Management |
| 19 | `casemgmt/dao/IssueDAOImpl.java` | Case Management |
| 20 | `casemgmt/dao/RoleProgramAccessDAOImpl.java` | Case Management |
| 21 | `commn/dao/DemographicDaoImpl.java` | Demographics |
| 22 | `daos/LookupDaoImpl.java` | Lookup |
| 23 | `daos/ProviderDAOImpl.java` | Provider |
| 24 | `daos/security/SecProviderDaoImpl.java` | Security |
| 25 | `daos/security/SecobjprivilegeDaoImpl.java` | Security |
| 26 | `daos/security/SecuserroleDaoImpl.java` | Security |
| 27 | `daos/security/UserAccessDaoImpl.java` | Security |

---

## Part 2: Confirmed Bugs

### BUG 1 (CRITICAL): Wrong HQL Property Names in `ProviderDAOImpl`

**File**: `src/main/java/io/github/carlos_emr/carlos/daos/ProviderDAOImpl.java`

The HBM mapping (`Provider.hbm.xml`) defines PascalCase property names:
```xml
<property column="last_name" name="LastName" />
<property column="first_name" name="FirstName" />
```

PR #89 uses **column names** instead of **HBM property names**:

| Method | Develop (Correct) | PR #89 (BROKEN) |
|--------|-------------------|-----------------|
| `getProviders()` | `p.LastName` | `p.lastName` |
| `getProviderByName()` | `p.FirstName = ?0 and p.LastName = ?1` | `p.first_name = ?1 and p.last_name = ?2` |

**Impact**: Runtime HQL parse failure — Hibernate resolves HQL properties via the HBM `name` attribute, not column names. `p.first_name` and `p.last_name` do not exist as entity properties.

**Root cause**: Likely confusion with `SecProvider.hbm.xml` which uses camelCase (`lastName`, `firstName`) for the **different** `SecProvider` entity.

### BUG 2 (HIGH): Null Safety Removed in `getProviderByName()`

**File**: `src/main/java/io/github/carlos_emr/carlos/daos/ProviderDAOImpl.java`

```java
// Develop (safe):
List<Provider> results = getHibernateTemplate().find(...);
return results.isEmpty() ? null : results.get(0);

// PR #89 (throws IndexOutOfBoundsException):
return (Provider) HqlQueryHelper.find(...).get(0);
```

**Impact**: If no provider matches, `.get(0)` on empty list throws `IndexOutOfBoundsException` instead of returning `null`.

### BUG 3 (CRITICAL): Wrong HQL Property Names in `DemographicDaoImpl`

**File**: `src/main/java/io/github/carlos_emr/carlos/commn/dao/DemographicDaoImpl.java`

The HBM mapping (`Demographic.hbm.xml`) defines:
```xml
<property column="first_name" name="FirstName" />
<property column="last_name" name="LastName" />
<property column="alias" name="Alias" />
```

PR #89 changes multiple methods to use raw column names:

| Method | Develop (Correct) | PR #89 (BROKEN) |
|--------|-------------------|-----------------|
| `searchDemographic()` | `d.LastName like :ln` | `last_name like ?1` |
| `searchDemographic()` | `d.FirstName like :fn` | `first_name like ?2` |
| `searchDemographicByNameAndStatus()` | `d.FirstName like :firstName` | `first_name like :firstName` |
| `searchMergedDemographicByName()` | `d.FirstName like :firstName` | `first_name like :firstName` |

**Impact**: Runtime HQL parse failure on every patient search operation.

### BUG 4 (MEDIUM): `locked` Boolean Comparison Changed

**File**: `src/main/java/io/github/carlos_emr/carlos/casemgmt/dao/CaseManagementNoteDAOImpl.java`

```java
// Develop (correct for boolean HBM type):
cmn.locked = false

// PR #89 (compares boolean to string):
cmn.locked != '1'
```

The `locked` field is `type="boolean"` in HBM. Comparing to string `'1'` is fragile and breaks H2 test compatibility (per CLAUDE.md Pitfall #7).

---

## Part 3: Functional Regressions

### REGRESSION 1 (HIGH): Alias Search Removed from 5 Patient Search Methods

PR #89 removes `or d.Alias like :fn` from **all** search methods in `DemographicDaoImpl`:

| Method | Develop | PR #89 |
|--------|---------|--------|
| `searchDemographic()` | `d.FirstName like :fn OR d.Alias like :fn` | `first_name like ?2` (Alias gone) |
| `searchDemographicByNameString()` | `x.FirstName like :fn OR x.Alias like :fn` | `x.FirstName like :fn` (Alias gone) |
| `searchDemographicByNameAndStatus()` | `d.FirstName like :firstName OR d.Alias like :firstName` | `first_name like :firstName` (Alias gone) |
| `searchMergedDemographicByName()` | `d.FirstName like :firstName OR d.Alias like :firstName` | `first_name like :firstName` (Alias gone) |
| `findByAttributes()` | `d.FirstName like :firstName OR d.Alias like :firstName` | `d.FirstName like :firstName` (Alias gone) |

**Impact**: Patients registered under aliases (preferred name, maiden name, etc.) will no longer be found. This is a patient safety concern in a healthcare EMR.

### REGRESSION 2 (MEDIUM): `getDemographicNosByProvider()` Semantic Change

**Develop**: When `onlyActive=false`, returns full `Demographic` objects (despite `List<Integer>` declaration). When `onlyActive=true`, selects `DemographicNo` but does NOT filter by `PatientStatus`.

**PR #89**: Both paths always `SELECT d.DemographicNo` and `onlyActive=true` now adds `AND d.PatientStatus = 'AC'`.

This is arguably a bug fix (the develop version's `onlyActive` was misleading), but it changes behavior. The `PatientStatus = 'AC'` filter was never there before.

---

## Part 4: Security Fixes (Positive Contributions)

The PR fixes **15+ SQL injection vulnerabilities** that exist on develop. These should absolutely be ported:

| File | Issue | Fix |
|------|-------|-----|
| `RoleProgramAccessDAOImpl.hasAccess()` | `roleId` concatenated into HQL | Parameterized with `?1` |
| `SecobjprivilegeDaoImpl.update()` | 4 values concatenated | Parameterized with `?1`-`?4` |
| `SecobjprivilegeDaoImpl.getFunctionDesc()` | `function_code` concatenated | Parameterized with `?1` |
| `SecobjprivilegeDaoImpl.getAccessDesc()` | `accessType_code` concatenated | Parameterized with `?1` |
| `SecobjprivilegeDaoImpl.getByObjectNameAndRoles()` | `o` concatenated | Parameterized with `?1` |
| `IssueDAOImpl.findIssueByCode(String[])` | Codes joined + concatenated | Named param `:codes` |
| `IssueDAOImpl.getIssueIdsByRoles()` | Role names concatenated | Named param `:roleNames` |
| `IssueDAOImpl.search()` / `searchCount()` | Role names concatenated + broken LIKE | Named params fixed |
| `CaseManagementNoteDAOImpl` (6 methods) | `issueIds` / `programIds` concatenated | Named params |
| `CaseManagementIssueDAOImpl.getIssuesByProgramsSince()` | Program IDs concatenated | Named param |
| `UserAccessDaoImpl` (2 methods) | Shelter ID concatenated | Named param `:shelterPattern` |
| `LookupDaoImpl.inOrg()` | Wildcard inside HQL `%?0` | Fixed to parameterized value |

---

## Part 5: Files Deleted on Develop (Migration No Longer Needed)

**569 of 811 files (70.2%)** in PR #89's diff are files that have been deleted from develop. These represent wasted migration work. Major categories:

| Module | Files Deleted on Develop | Removal PR |
|--------|--------------------------|------------|
| CAISI Integrator (dao/ws/model/util) | 295 | #399 |
| OLIS v1 (query/parameter/segment) | 64 | Various cleanup |
| eRx (electronic prescribing) | 29 | Various cleanup |
| Unused DAOs | 38 | #437 |
| Unused classes | 28+ | #435, #436, #456 |
| CSRFGuard old classes | 3 | #465 |
| OneLogin SSO | 3 | #402 |

**If PR #89 were merged, it would re-introduce all 569 deleted files back into the codebase.**

---

## Part 6: Develop Feature Conflicts

PR #89 was forked from an older state and reverts these develop features:

| Feature | Develop | PR #89 | Impact |
|---------|---------|--------|--------|
| CSRFGuard | 4.5.0 with auto-injection | 3.1.0 (old API) | **Security regression**: loses auto-injection, logout broadcast |
| Drools | 7.74.1 (KIE API) | 2.0 (ancient) | **Won't run on Java 21**: janino 2.3.2 incompatible |
| OWASP Encoder | 1.4.0 | 1.2.1/1.2.3 | Security regression |
| CVE fixes | FHIR XXE, XStream DoS, Xalan | Not present | Re-opens vulnerabilities |
| SLF4J 2.x bridge | `log4j-slf4j2-impl` present | Removed | Silent log loss |
| `carlos.properties` | CARLOS naming | `oscar_mcmaster.properties` | Reverts project naming |
| `SecProvider.hbm.xml` | `generator class="assigned"` | `generator class="native"` | **Data corruption risk** |
| `casemgmt_note_ext.hbm.xml` | `` column="`value`" `` (backtick-quoted) | `column="value"` | Breaks H2 tests |
| Fax provider feature | MIDDLEWARE/SRFAX support | Old single-provider | Reverts fax transport |
| Test includes | Full suite with `**/*IntegrationTest.java` | Reduced patterns | Silently skips tests |
| Mockito | 5.21.0 | 5.8.0 | Downgrade may break tests |
| AssertJ | 3.27.7 | 3.24.2 | Downgrade |

---

## Part 7: Merge Conflict Summary

**738 total conflicting files** due to unrelated git histories.

| Category | Files | Nature |
|----------|-------|--------|
| DAO files (core migration) | 27 | `?0`→`?1` + HqlQueryHelper vs develop's `?0` + HibernateTemplate |
| Drools (develop-only migration) | 19 | Drools 7.x KIE API vs old Drools 2.0 |
| Fax provider (develop-only feature) | 9 | Multi-provider vs single-provider |
| JSP/webapp files | 354 | Copyright headers, Bootstrap 5, security hardening |
| Test files | 33 | Test base classes, integration tests |
| Config/build files | ~60 | pom.xml (48 conflict regions), struts.xml, web.xml, Spring contexts |
| Documentation | ~40 | CLAUDE.md, javadoc |
| Dependency locks | 3 | `package-lock.json` alone has 1,232 conflict regions |

---

## Part 8: Recommended Action Plan

### Option A: Cherry-Pick Migration (Recommended)

1. Create a **fresh branch off develop**
2. Port `HqlQueryHelper.java` utility class
3. Apply the `?0` → `?1` migration to the **27 DAO files that exist on develop**, fixing:
   - Use correct HBM property names (`p.LastName` not `p.last_name`)
   - Preserve null-safety patterns
   - Preserve Alias search in DemographicDaoImpl
   - Keep `locked = false` (not `!= '1'`)
4. Port the 15+ SQL injection fixes separately (high value)
5. Port the CaseManagementNoteDAOImpl subquery alias fixes (`cmn2`)

### Option B: Rebase PR #89 (Not Recommended)

Rebasing would require resolving 738 conflicts manually. Given that 70% of the files are deleted code and the PR reverts critical features, this is not practical.

---

## Appendix: CaseManagementNoteDAOImpl Subquery Fixes (Correct)

The PR correctly fixes invalid subquery syntax in multiple methods:

```java
// Develop (incorrect — uses outer alias in subquery without entity declaration):
"select max(cmn.id) from cmn where cmn.demographic_no = ?0 GROUP BY uuid"

// PR #89 (correct — declares entity, uses distinct alias):
"select max(cmn2.id) from CaseManagementNote cmn2 where cmn2.demographic_no = ?1 GROUP BY cmn2.uuid"
```

These are latent bugs that Hibernate 5 tolerates but Hibernate 6 will reject. These fixes should be ported.

---

*Generated with Claude Code — https://claude.ai/code*
