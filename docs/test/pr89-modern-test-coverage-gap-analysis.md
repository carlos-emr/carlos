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
- A static scan found approximately **304** production Java files with at least one positional placeholder (`?N`) (count may drift as code evolves).
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

### Previously missing — now covered by this PR
1. **`LookupDaoImpl`** → `LookupDaoIntegrationTest` (added in this PR)
   - ✅ `from LookupTableDefValue s where s.tableId= ?0` — `shouldBindTableId_inGetLookupTableDef`
   - ✅ `from FieldDefValue s where s.tableId=?0 order by s.fieldIndex` — `shouldBindAndOrder_inLoadFieldDefList`
   - ⬜ `FROM LstOrgcd o WHERE o.codecsv like ?0` — not directly testable (`updateOrgStatus` is private)
   - ✅ `From LstOrgcd a where  a.fullcode like %?0` — `shouldExerciseInOrg_withLikeParameterBinding` (documents fragile `%?0` HQL pattern)

2. **`ProgramTeamDAOImpl`** → `ProgramTeamDaoIntegrationTest` (added in this PR)
   - ✅ `select pt.id from ProgramTeam pt where pt.programId = ?0 and pt.name = ?1` — `shouldReturnTrue_whenBothProgramAndNameMatch`
   - ✅ `from ProgramTeam tp where tp.programId = ?0` — `shouldFilterTeams_byRequestedProgramOnly`

## Remaining modern-test coverage gaps

### `LookupDaoImpl` — `updateOrgStatus` (private method, not directly testable)
File: `src/main/java/io/github/carlos_emr/carlos/daos/LookupDaoImpl.java`

The `updateOrgStatus` method is private and uses `FROM LstOrgcd o WHERE o.codecsv like ?0`.
It is only exercisable through higher-level public methods that modify org status.
Direct test coverage is not feasible without refactoring the method to package-private or testing through the public API chain.

### `inOrg` — LIKE `%?0` pattern (documented, fragile)
The `inOrg` method uses `like %?0` where the `%` wildcard is embedded in the HQL string rather than in the parameter value.
This is non-standard HQL and may break during Hibernate 6 migration. The test `shouldExerciseInOrg_withLikeParameterBinding` documents
current behavior via a try/catch pattern — it will detect if the query starts failing after migration.

### Depth gaps in `CaseManagementNoteDAOImpl` and `DemographicDaoImpl` — NOW COVERED
The following previously untested `?0` methods now have modern integration tests:

**CaseManagementNoteDAOImpl** (6 methods added):
- `getEditors(CaseManagementNote)` — cross-join with Provider by UUID
- `getAllEditors(String)` — cross-join with Provider by demographicNo
- `getHistory(CaseManagementNote)` — note history ordered by update_date
- `getRawNoteInfoByDemographic(String)` — Object[] projection
- `getRawNoteInfoMapByDemographic(String)` — Map projection
- `getMostRecentNotes(Integer)` — most recent note per UUID by demographicNo

**DemographicDaoImpl** (6 methods added):
- `getClientsByChartNo(String)` — single-param chart number lookup
- `getDemographicWithGreaterThanYearOfBirth(int)` — year comparison with String cast
- `getDemographicsByHealthNum(String)` — HIN lookup
- `getDemographicWithLastFirstDOBExact(String, String, ...)` — 2+ params with dynamic binding
- `getDemographicIdsAddedSince(Date)` — date-based ID filter
- `getActiveDemographicAfter(Date)` — active status + date filter

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
- [x] Add `LookupDaoIntegrationTest` (core query-binding tests added; LIKE semantics deferred).
- [x] Add `ProgramTeamDaoIntegrationTest` (all 5 test methods across 4 categories implemented).
- [x] Extend `IssueDAOIntegrationTest` with projection and normalization assertions.
- [x] Extend `SecProviderDaoIntegrationTest` with `findByLastName` and `findAll` coverage.
- [x] Add LIKE semantics test for `LookupDaoImpl.inOrg` (documents fragile `%?0` pattern).
- [x] Deepen `CaseManagementNoteDaoIntegrationTest` with 6 additional `?0` method tests (editors, history, projections).
- [x] Deepen `DemographicDaoIntegrationTest` with 6 additional `?0` method tests (chartNo, yearOfBirth, healthNum, exact name, date filters).
- [ ] Run modern-tests suite on the PR branch rebased/aligned with `develop`.
- [ ] Include at least one query-count/timing guardrail for top 3 most-called migrated DAO methods.

With those added, you should be able to catch functional regressions caused by positional-parameter updates with high confidence at the data abstraction layer.


## Implementation status for the two previously missing suites

### A) `LookupDaoIntegrationTest` — IMPLEMENTED (core + LIKE)
1. ✅ **Test fixture and seed helpers** — native SQL inserts (ORM save incompatible with `mutable="false"` + `native` generator on String ID).
2. ✅ **Core query-binding tests** — `shouldBindTableId_inGetLookupTableDef`, `shouldBindAndOrder_inLoadFieldDefList`.
3. ✅ **LIKE semantics test** — `shouldExerciseInOrg_withLikeParameterBinding` documents fragile `%?0` HQL pattern via try/catch (infrastructure: `LstOrgcd.hbm.xml` registered in persistence.xml, `lst_orgcd` table in `test-lookup-tables.sql`).
4. ⬜ **Negative and edge tests** — deferred.

### B) `ProgramTeamDaoIntegrationTest` — FULLY IMPLEMENTED
1. ✅ **Fixture and factories** — helper methods persist `Program` and `ProgramTeam` via ORM.
2. ✅ **Positional binding tests** — `shouldReturnTrue_whenBothProgramAndNameMatch`, `shouldFilterTeams_byRequestedProgramOnly`.
3. ✅ **Validation behavior tests** — `shouldThrow_forInvalidTeamNameExistsInputs`, `shouldThrow_forInvalidGetProgramTeamsInputs`.
4. ✅ **Roundtrip tests** — `shouldPersistAndRetrieveTeam_viaSaveGetRoundtrip`.

## Assertion-coverage audit of currently in-scope DAOs

Status legend:
- **Covered**: existing modern tests include direct asserts for changed/in-scope behavior.
- **Partial**: some related behavior covered, but a changed/in-scope method lacks direct assertion.

| DAO | In-scope changed behavior | Status | Evidence |
|---|---|---|---|
| `ProviderDAOImpl` | `getProviderByName` parameter binding and null behavior | **Covered** | `ProviderDAOIntegrationTest` has positive and both negative match asserts. |
| `CaseManagementNoteDAOImpl` | `getNotesByDemographicSince` + 6 additional `?0` methods (editors, history, projections, most-recent) | **Covered** | `CaseManagementNoteDaoIntegrationTest` — 27 tests covering CRUD, search, date filtering, cross-join editor queries, history, raw/map projections, and most-recent-per-UUID. |
| `ClientReferralDAOImpl` | multi-parameter referral queries | **Covered** | `ClientReferralDAOIntegrationTest` asserts filtering for client/facility/program combinations. |
| `IssueDAOImpl` | search and multi-param issue queries | **Covered** | `IssueDAOIntegrationTest` now asserts scalar projection, input normalization (uppercase→lowercase), and blank-input behavior for `getLocalCodesByCommunityType`. |
| `SecProviderDaoImpl` | `findById(id,status)` and status filters | **Covered** | `SecProviderDaoIntegrationTest` now includes `findByLastName` (findByProperty path) and `findAll` assertions. |

### Interpretation of the audit
- The existing test base is solid for the primary PR89 migration themes (parameter binding/order/filtering).
- Both previously missing suites (`LookupDaoImpl`, `ProgramTeamDAOImpl`) have been added in this PR.
- The previously partial `IssueDAOImpl` and `SecProviderDaoImpl` coverage has been upgraded to **Covered** with targeted additions in this PR.
- `CaseManagementNoteDAOImpl` depth expanded: 6 additional `?0` methods now tested (editors, history, projections, most-recent).
- `DemographicDaoImpl` depth expanded: 6 additional `?0` methods now tested (chartNo, yearOfBirth, healthNum, exact name, date filters).
- `LookupDaoImpl` LIKE semantics: `inOrg` now has a behavioral test documenting the fragile `%?0` pattern. `updateOrgStatus` remains untestable (private method).
- Remaining gap: `LookupDaoImpl.updateOrgStatus` (private, not directly testable without refactoring).

## Answer to your merge-test question
Yes — this is exactly the right strategy.

If these tests pass on a branch based on `develop`, and then fail after merging PR89, that is a high-signal regression indicator for DAO abstraction behavior changed by PR89. Keep in mind:
- It proves a regression against your tested contract (which is what you want).
- The signal is strongest when tests assert result-set membership, ordering, null/empty behavior, and representative query-count/perf guardrails.
