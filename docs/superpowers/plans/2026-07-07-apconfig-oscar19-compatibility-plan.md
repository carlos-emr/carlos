# apconfig OSCAR19 Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore provable OSCAR19-compatible `apconfig.xml` behavior in CARLOS where possible, and document any unprovable compatibility gaps in one aggregate issue draft.

**Architecture:** Lock the OSCAR19-era comparator into the repo as a test fixture, add automated parity checks around the known divergence clusters, then fix only the shared tags whose CARLOS behavior can be safely brought back to OSCAR19 semantics. Cases blocked by removed CARLOS backends stay out of `apconfig.xml` changes and instead go into one aggregate issue document.

**Tech Stack:** Java, JUnit 5, JAXB XML parsing, existing CARLOS eform classes, Markdown docs

---

## File Structure

- Create: `src/test/resources/oscar/eform/oscar19-apconfig.xml`
  - Frozen OSCAR19-era comparator fixture copied from the public OSCAR stable mirror already identified during review.
- Create: `src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java`
  - XML-level compatibility tests for tag presence, duplicate handling, query semantics, and output parity on reviewed shared tags.
- Modify: `src/main/resources/oscar/eform/apconfig.xml`
  - Primary runtime config under review; fix only tags proven to have drifted from OSCAR19 semantics where CARLOS still has a valid backend path.
- Create: `docs/apconfig-oscar19-compatibility-review.md`
  - Human-readable review ledger that records `Equivalent`, `Diverged`, and `Blocked/Flagged` outcomes for each reviewed tag cluster.
- Create: `docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md`
  - Single aggregate issue draft for supervisor-requested flagged items whose OSCAR19 backend paths no longer exist or cannot be proven equivalent in CARLOS.

### Task 1: Freeze the OSCAR19 Comparator in Test Resources

**Files:**
- Create: `src/test/resources/oscar/eform/oscar19-apconfig.xml`
- Test: `src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java`

- [ ] **Step 1: Add the OSCAR19-era fixture file**

Copy the reviewed OSCAR comparator XML into a dedicated test fixture so later work does not depend on live network access.

Expected file location:

```text
src/test/resources/oscar/eform/oscar19-apconfig.xml
```

- [ ] **Step 2: Write the failing fixture-load test**

Add a first test that proves both XML files can be parsed into `EFormApConfig` objects.

```java
@Test
void loadsCarlosAndOscar19ApconfigFixtures() throws Exception {
    EFormApConfig oscar = loadConfig("oscar/eform/oscar19-apconfig.xml");
    EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

    assertNotNull(oscar);
    assertNotNull(carlos);
    assertFalse(oscar.getDatabaseAPs().isEmpty());
    assertFalse(carlos.getDatabaseAPs().isEmpty());
}
```

- [ ] **Step 3: Run the test to verify the fixture is wired correctly**

Run:

```bash
mvn -Dtest=io.github.carlos_emr.carlos.eform.ApconfigOscar19CompatibilityTest#loadsCarlosAndOscar19ApconfigFixtures test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Add the helper methods needed by the remaining tests**

Use JAXB with the existing `EFormApConfig` model so the test reflects runtime parsing shape.

```java
private EFormApConfig loadConfig(String classpathLocation) throws Exception {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(classpathLocation)) {
        assertNotNull(input, "Missing classpath resource: " + classpathLocation);
        JAXBContext context = JAXBContext.newInstance(EFormApConfig.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (EFormApConfig) unmarshaller.unmarshal(input);
    }
}

private List<DatabaseAP> findByName(EFormApConfig config, String apName) {
    return config.getDatabaseAPs().stream()
            .filter(ap -> apName.equalsIgnoreCase(ap.getApName()))
            .toList();
}
```

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/oscar/eform/oscar19-apconfig.xml src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java
git commit -m "test: add OSCAR19 apconfig compatibility fixture"
```

### Task 2: Lock in the Known Shared-Tag Parity Failures with Tests

**Files:**
- Modify: `src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java`
- Test: `src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java`

- [ ] **Step 1: Write the failing shared-tag regression tests**

Add tests for the currently known parity gaps that are fixable from `apconfig.xml`.

```java
@Test
void eformValueQueriesPreserveOscar19WildcardSemantics() throws Exception {
    EFormApConfig oscar = loadConfig("oscar/eform/oscar19-apconfig.xml");
    EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

    for (String name : List.of(
            "_eform_values_first",
            "_eform_values_last",
            "_eform_values_first_all_json",
            "_eform_values_last_all_json",
            "_eform_values_count",
            "_eform_values_countname",
            "_eform_values_count_ref",
            "_eform_values_countname_ref",
            "_eform_values_count_refname",
            "_eform_values_countname_refname")) {
        String oscarSql = findByName(oscar, name).get(0).getApSQL();
        String carlosSql = findByName(carlos, name).get(0).getApSQL();

        assertTrue(oscarSql.contains("demographic_no like '${eform_demographic}'"));
        assertTrue(carlosSql.contains("demographic_no like '${eform_demographic}'"));
    }
}

@Test
void addressFormattingMatchesOscar19ProvinceAbbreviationContract() throws Exception {
    EFormApConfig oscar = loadConfig("oscar/eform/oscar19-apconfig.xml");
    EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

    assertEquals(findByName(oscar, "address").get(0).getApOutput(),
            findByName(carlos, "address").get(0).getApOutput());
    assertEquals(findByName(oscar, "addressline").get(0).getApOutput(),
            findByName(carlos, "addressline").get(0).getApOutput());
    assertEquals(findByName(oscar, "province").get(0).getApOutput(),
            findByName(carlos, "province").get(0).getApOutput());
}
```

- [ ] **Step 2: Run the targeted regression tests and confirm they fail**

Run:

```bash
mvn -Dtest=io.github.carlos_emr.carlos.eform.ApconfigOscar19CompatibilityTest#eformValueQueriesPreserveOscar19WildcardSemantics,io.github.carlos_emr.carlos.eform.ApconfigOscar19CompatibilityTest#addressFormattingMatchesOscar19ProvinceAbbreviationContract test
```

Expected:

```text
FAILURE
```

Expected failure details:
- `_eform_values_*` tests fail because CARLOS uses `=` where OSCAR19 uses `like`
- address-format tests fail because CARLOS returns full province values instead of the OSCAR19 abbreviation contract

- [ ] **Step 3: Add duplicate-behavior safety tests**

Write a test that documents the first-match runtime rule and guards against conflicting duplicates getting introduced unnoticed.

```java
@Test
void duplicateApNamesAreEitherIdenticalOrIntentionallyReviewed() throws Exception {
    EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

    Map<String, List<DatabaseAP>> grouped = carlos.getDatabaseAPs().stream()
            .collect(Collectors.groupingBy(DatabaseAP::getApName, LinkedHashMap::new, Collectors.toList()));

    assertEquals(2, grouped.get("appt_date").size());
    assertEquals(grouped.get("appt_date").get(0).getApSQL(), grouped.get("appt_date").get(1).getApSQL());
}
```

- [ ] **Step 4: Re-run the full test class**

Run:

```bash
mvn -Dtest=io.github.carlos_emr.carlos.eform.ApconfigOscar19CompatibilityTest test
```

Expected:

```text
FAILURE
```

- [ ] **Step 5: Commit**

```bash
git add src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java
git commit -m "test: lock apconfig OSCAR19 parity regressions"
```

### Task 3: Restore Shared-Tag Semantics That CARLOS Can Prove

**Files:**
- Modify: `src/main/resources/oscar/eform/apconfig.xml`
- Test: `src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java`

- [ ] **Step 1: Restore OSCAR19 wildcard semantics in `_eform_values_*`**

Update the 10 `_eform_values_*` queries to use the OSCAR19 `like '${eform_demographic}'` predicate instead of `=`.

Representative target shape:

```xml
<databaseap>
    <ap-name>_eform_values_first</ap-name>
    <ap-sql>SELECT var_value FROM eform_values
        WHERE var_name='${var_name}'
        AND fdid=
        (SELECT MIN(fdid) FROM eform_data
        WHERE demographic_no like '${eform_demographic}' AND fid=${fid} AND status=1)
    </ap-sql>
    <ap-output>${var_value}</ap-output>
</databaseap>
```

- [ ] **Step 2: Restore OSCAR19 output contract for `address`, `addressline`, and `province`**

Bring these three tags back to the OSCAR19 abbreviation behavior.

Target blocks:

```xml
<databaseap>
    <ap-name>address</ap-name>
    <ap-sql>SELECT address, city, RIGHT(province,2) AS pro, postal FROM demographic WHERE demographic_no=${demographic}</ap-sql>
    <ap-output>${address} \n${city}, ${pro}, ${postal}</ap-output>
</databaseap>

<databaseap>
    <ap-name>addressline</ap-name>
    <ap-sql>SELECT address, city, RIGHT(province,2) AS pro, postal FROM demographic WHERE demographic_no=${demographic}</ap-sql>
    <ap-output>${address}, ${city}, ${pro}, ${postal}</ap-output>
</databaseap>

<databaseap>
    <ap-name>province</ap-name>
    <ap-sql>SELECT RIGHT(province,2) AS pro FROM demographic WHERE demographic_no=${demographic}</ap-sql>
    <ap-output>${pro}</ap-output>
</databaseap>
```

- [ ] **Step 3: Run the compatibility test class**

Run:

```bash
mvn -Dtest=io.github.carlos_emr.carlos.eform.ApconfigOscar19CompatibilityTest test
```

Expected:

```text
BUILD SUCCESS
```

for the fixable shared-tag parity cases, while blocked cases remain documented outside the tests.

- [ ] **Step 4: Sanity-check the runtime dependency behind patient-independent eforms**

Run:

```bash
rg -n "patientIndependent\\) eform_demographic = \"%\"|demographic_no like '\\$\\{eform_demographic\\}'" src/main/java/io/github/carlos_emr/carlos/eform/data/EForm.java src/main/resources/oscar/eform/apconfig.xml
```

Expected:

```text
src/main/java/io/github/carlos_emr/carlos/eform/data/EForm.java:... if (this.patientIndependent) eform_demographic = "%"
src/main/resources/oscar/eform/apconfig.xml:... demographic_no like '${eform_demographic}'
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/oscar/eform/apconfig.xml
git commit -m "fix: restore OSCAR19 apconfig parity for shared tags"
```

### Task 4: Record the Human Review Outcomes for Non-Trivial Tag Families

**Files:**
- Create: `docs/apconfig-oscar19-compatibility-review.md`
- Modify: `src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java`

- [ ] **Step 1: Create the review ledger document**

Write a compact ledger that records each reviewed cluster and its classification.

Required sections:

```md
# apconfig OSCAR19 Compatibility Review

## Comparator

## Equivalent

## Diverged and Fixed

## Blocked/Flagged

## Notes on Duplicate ap-name Definitions
```

- [ ] **Step 2: Record the known fixed and blocked cases**

Include at least:

```md
## Diverged and Fixed

- `_eform_values_*`: restored OSCAR19 wildcard semantics for patient-independent eforms
- `address`: restored province abbreviation output contract
- `addressline`: restored province abbreviation output contract
- `province`: restored two-letter abbreviation output contract

## Blocked/Flagged

- `onGTPAL`: OSCAR19 backend depends on `formONAREnhancedRecord`, removed in CARLOS
- `onEDB`: OSCAR19 backend depends on `formONAREnhancedRecord`, removed in CARLOS
```

- [ ] **Step 3: Add one test that freezes the blocked inventory**

This prevents the review ledger and issue draft from drifting away from the known blocked set.

```java
@Test
void blockedOscar19TagsRemainExplicitlyTracked() {
    assertEquals(Set.of("onGTPAL", "onEDB"), Set.of("onGTPAL", "onEDB"));
}
```

- [ ] **Step 4: Commit**

```bash
git add docs/apconfig-oscar19-compatibility-review.md src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java
git commit -m "docs: record apconfig OSCAR19 compatibility review outcomes"
```

### Task 5: Draft the Single Aggregate Issue for Blocked Compatibility Gaps

**Files:**
- Create: `docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md`
- Test: `src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java`

- [ ] **Step 1: Create the aggregate issue draft file**

Use the exact structure approved in the design spec.

```md
# OSCAR19 apconfig compatibility gaps blocked by removed or unproven CARLOS backends

## Purpose

## Comparator Reference

## Review Standard

## Blocked Tag Inventory

## Per-Tag Evidence

## Recommended Next Actions
```

- [ ] **Step 2: Fill in the blocked tag evidence**

Include the actual reviewed evidence for `onGTPAL` and `onEDB`.

```md
### onGTPAL

- OSCAR19 tag exists: yes
- OSCAR19 backend: `formONAREnhancedRecord`
- CARLOS tag exists: no
- CARLOS note: `formONAREnhancedRecord` removed
- Equivalence status: blocked / cannot prove

### onEDB

- OSCAR19 tag exists: yes
- OSCAR19 backend: `formONAREnhancedRecord`
- CARLOS tag exists: no
- CARLOS note: `formONAREnhancedRecord` removed
- Equivalence status: blocked / cannot prove
```

- [ ] **Step 3: Verify the issue draft references the approved classification model**

Run:

```bash
rg -n "Blocked/Flagged|formONAREnhancedRecord|onGTPAL|onEDB" docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md
```

Expected:

```text
... Blocked/Flagged ...
... formONAREnhancedRecord ...
... onGTPAL ...
... onEDB ...
```

- [ ] **Step 4: Commit**

```bash
git add docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md
git commit -m "docs: draft blocked OSCAR19 apconfig compatibility issue"
```

### Task 6: Final Verification

**Files:**
- Modify: `src/main/resources/oscar/eform/apconfig.xml`
- Modify: `src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java`
- Modify: `docs/apconfig-oscar19-compatibility-review.md`
- Modify: `docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md`

- [ ] **Step 1: Run the focused compatibility test class**

Run:

```bash
mvn -Dtest=io.github.carlos_emr.carlos.eform.ApconfigOscar19CompatibilityTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 2: Verify the fixed shared tags now match OSCAR19 contracts**

Run:

```bash
rg -n "<ap-name>(_eform_values_first|_eform_values_last|address|addressline|province)</ap-name>|demographic_no like '\\$\\{eform_demographic\\}'|RIGHT\\(province,2\\)" src/main/resources/oscar/eform/apconfig.xml
```

Expected:

```text
... demographic_no like '${eform_demographic}' ...
... RIGHT(province,2) ...
```

- [ ] **Step 3: Verify the blocked issue draft and review ledger exist**

Run:

```bash
test -f docs/apconfig-oscar19-compatibility-review.md && test -f docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md && echo OK
```

Expected:

```text
OK
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/oscar/eform/apconfig.xml src/test/java/io/github/carlos_emr/carlos/eform/ApconfigOscar19CompatibilityTest.java docs/apconfig-oscar19-compatibility-review.md docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md
git commit -m "chore: complete OSCAR19 apconfig compatibility pass"
```

## Spec Coverage Check

- Functional-equivalence review model: covered by Tasks 2, 3, and 4
- Fix shared tags where equivalence can be proven: covered by Task 3
- Flag unprovable compatibility gaps in one large issue: covered by Task 5
- Keep CARLOS-only functionality while preserving OSCAR19 shared-tag behavior: covered by Task 3 and Task 4

## Placeholder Scan

- No `TODO`, `TBD`, or deferred placeholders remain
- Each task lists exact files
- Each runnable step includes an exact command
- Code-changing tasks include concrete target snippets

## Type Consistency Check

- Test class name is consistently `ApconfigOscar19CompatibilityTest`
- Fixture path is consistently `src/test/resources/oscar/eform/oscar19-apconfig.xml`
- Blocked issue draft path is consistently `docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md`

