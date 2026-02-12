# PR 89 Modern-Test Coverage Gap Analysis (Hibernate positional parameters)

## Scope reviewed
This analysis is focused on DAO/query paths that still use **HQL positional parameters** (`?0`, `?1`, etc.), because PR 89 is focused on Hibernate 5→6 positional-parameter readiness.

## Direct answer to your question
### Are the other existing tests giving enough coverage?
**Mostly yes for the DAOs already targeted by the Hibernate migration test expansion, but not fully by themselves for full functional-equivalence confidence.**

- Existing modern integration tests already cover the primary DAOs that were changed during Hibernate positional-parameter prep.
- The highest-risk remaining uncovered `?0` DAO areas in modern-tests are still:
  1. `LookupDaoImpl`
  2. `ProgramTeamDAOImpl`

If you add those two suites, your positional-parameter coverage for the currently identified modern-test gaps becomes strong enough to catch most PR89 functional regressions at the DAO abstraction layer.

## Current state
- The repository already contains broad modern integration coverage for many positional-parameter DAO paths (security, PMmodule, casemgmt, provider, demographic, etc.).
- A static scan found **319** production Java files with at least one positional placeholder (`?N`).
- Of those, **27** still use legacy zero-based `?0` placeholders (highest migration risk).
- Only **2** of those `?0` files do not appear to have a corresponding modern-test class today.

## Coverage matrix for PR89-style DAO risk (functional equivalence intent)

### DAOs with existing modern integration coverage
These already have modern integration tests and are good signals for positional parameter behavior:
- `ClientReferralDAOImpl` → `ClientReferralDAOIntegrationTest`
- `CaseManagementNoteDAOImpl` → `CaseManagementNoteDaoIntegrationTest`
- `IssueDAOImpl` → `IssueDAOIntegrationTest`
- `ProviderDAOImpl` → `ProviderDAOIntegrationTest`
- `SecProviderDaoImpl` → `SecProviderDaoIntegrationTest`

### Remaining high-risk gaps (still missing modern integration tests)
1. **`LookupDaoImpl`**
   - `from LookupTableDefValue s where s.tableId= ?0`
   - `from FieldDefValue s where s.tableId=?0 order by s.fieldIndex`
   - `FROM LstOrgcd o WHERE o.codecsv like ?0`
   - `From LstOrgcd a where  a.fullcode like %?0`

2. **`ProgramTeamDAOImpl`**
   - `select pt.id from ProgramTeam pt where pt.programId = ?1 and pt.name = ?2`
   - `from ProgramTeam tp where tp.programId = ?0`

## Missing modern-test coverage you should add before merging PR 89

### 1) `LookupDaoImpl` (no modern-test counterpart found)
File: `src/main/java/io/github/carlos_emr/carlos/daos/LookupDaoImpl.java`

Recommended modern integration tests:
1. **GetLookupTableDef binds and returns correct row**
   - seed 2 lookup table defs, assert only exact `tableId` is returned.
2. **LoadFieldDefList ordering + parameter binding**
   - seed out-of-order rows and assert sorted by `fieldIndex`.
3. **updateOrgStatus path using LIKE ?0**
   - verify only matching descendants are updated.
4. **inOrg behavior around `like %?0` query**
   - assert expected inclusion semantics and null/empty handling.
   - this is especially important because `%?0` is fragile for Hibernate parser changes.

### 2) `ProgramTeamDAOImpl` (no modern-test counterpart found)
File: `src/main/java/io/github/carlos_emr/carlos/PMmodule/dao/ProgramTeamDAOImpl.java`

Recommended modern integration tests:
1. **teamNameExists true/false** for same/different program id.
2. **getProgramTeams(programId)** returns only requested program’s teams.
3. **Input guardrails**
   - null/invalid `programId` and blank `teamName` throw `IllegalArgumentException`.
4. **Save + read-back roundtrip**
   - `saveProgramTeam` then `getProgramTeam` / `getProgramTeams` validates persistence and query behavior.

## Additional checks needed for your stated end-goal (functional + equivalent performance)
Your end-goal includes **equivalent DAO behavior** and confidence that positional updates do not alter function.

To meet that goal, keep these checks in your merge gate in addition to existing tests:

1. **Behavioral equivalence assertions**
   - For each migrated DAO method, assert:
     - same record set membership,
     - same ordering,
     - same null/empty behavior,
     - same exception behavior for invalid inputs.

2. **Projection/type equivalence assertions**
   - Where queries changed shape (entity vs scalar projection), assert returned Java type and values explicitly.

3. **Performance guardrails (practical, testable)**
   - Add lightweight query-count assertions for hot DAO calls (no N+1 regressions).
   - Add simple bounded-time assertions for representative datasets to catch major slowdowns.
   - Use stable thresholds (not micro-benchmarks) to avoid CI flakiness.

## Merge-confidence checklist for PR 89
Before merge, minimum high-signal checklist:
- [ ] Add `LookupDaoIntegrationTest` (4 scenarios above).
- [ ] Add `ProgramTeamDaoIntegrationTest` (4 scenarios above).
- [ ] Run modern-tests suite on the PR branch rebased/aligned with `develop`.
- [ ] Include at least one query-count/timing guardrail for top 3 most-called migrated DAO methods.

With those added, you should be able to catch functional regressions caused by positional-parameter updates with high confidence at the data abstraction layer.


## Concrete implementation plan for the two missing suites

### A) `LookupDaoIntegrationTest` plan
1. **Test fixture and seed helpers**
   - Build helper methods to insert minimal rows for `LookupTableDefValue`, `FieldDefValue`, and `LstOrgcd`.
   - Use deterministic identifiers per test (`System.nanoTime()` suffix).
2. **Core query-binding tests**
   - `GetLookupTableDef(tableId)` returns only exact match for `?0`.
   - `LoadFieldDefList(tableId)` returns rows ordered by `fieldIndex`.
3. **LIKE semantics tests**
   - Exercise `updateOrgStatus` path and assert only `codecsv like ?0` descendants are updated.
   - Exercise `inOrg` and assert expected include/exclude behavior for `%?0` semantics.
4. **Negative and edge tests**
   - No matching tableId returns null/empty expectations without exceptions.
   - Empty org chains do not cause unexpected errors.

### B) `ProgramTeamDaoIntegrationTest` plan
1. **Fixture and factories**
   - Create helper to persist program IDs and related `ProgramTeam` rows.
2. **Positional binding tests**
   - `teamNameExists(programId, teamName)` true when both match, false when only one matches.
   - `getProgramTeams(programId)` filters by the correct program (`?0`).
3. **Validation behavior tests**
   - Null/invalid `programId` and blank `teamName` throw `IllegalArgumentException`.
4. **Roundtrip tests**
   - `saveProgramTeam` + `getProgramTeam` + `getProgramTeams` verifies persistence/read consistency.

## Assertion-coverage audit of currently in-scope DAOs

Status legend:
- **Covered**: existing modern tests include direct asserts for changed/in-scope behavior.
- **Partial**: some related behavior covered, but a changed/in-scope method lacks direct assertion.

| DAO | In-scope changed behavior | Status | Evidence |
|---|---|---|---|
| `ProviderDAOImpl` | `getProviderByName` parameter binding and null behavior | **Covered** | `ProviderDAOIntegrationTest` has positive and both negative match asserts. |
| `CaseManagementNoteDAOImpl` | `getNotesByDemographicSince` demographic/date filtering (`locked=false` path query) | **Covered** | `CaseManagementNoteDaoIntegrationTest` asserts inclusion/exclusion by demo+date and empty result behavior. |
| `ClientReferralDAOImpl` | multi-parameter referral queries | **Covered** | `ClientReferralDAOIntegrationTest` asserts filtering for client/facility/program combinations. |
| `IssueDAOImpl` | search and multi-param issue queries | **Partial** | `IssueDAOIntegrationTest` covers search/type+code flows; no direct assertion found for `getLocalCodesByCommunityType` projection shape (`SELECT i.code`). |
| `SecProviderDaoImpl` | `findById(id,status)` and status filters | **Partial** | `SecProviderDaoIntegrationTest` covers `findById(id,status)` and status filtering; no direct assertion found for `findByProperty` Criteria path or `findAll` query text change. |

### Interpretation of the audit
- The existing test base is solid for the primary PR89 migration themes (parameter binding/order/filtering).
- Two missing suites (`LookupDaoImpl`, `ProgramTeamDAOImpl`) remain the highest-value additions.
- If you want strict equivalence confidence for **every** touched behavior, add two targeted tests beyond that:
  1. `IssueDAOIntegrationTest` case for `getLocalCodesByCommunityType` result projection/type.
  2. `SecProviderDaoIntegrationTest` case that exercises `findByProperty` (e.g., `findByLastName`) and `findAll`.

## Answer to your merge-test question
Yes — this is exactly the right strategy.

If these tests pass on a branch based on `develop`, and then fail after merging PR89, that is a high-signal regression indicator for DAO abstraction behavior changed by PR89. Keep in mind:
- It proves a regression against your tested contract (which is what you want).
- The signal is strongest when tests assert result-set membership, ordering, null/empty behavior, and representative query-count/perf guardrails.
