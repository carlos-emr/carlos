# CARLOS EMR - Drools Decision Support System

Comprehensive documentation of the Drools Rule Language (DRL) decision support system used across CARLOS EMR for clinical decision support, immunization scheduling, population health reporting, and pregnancy workflow management.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
  - [Rule Loading Pipeline](#rule-loading-pipeline)
  - [KieBase Caching](#kiebase-caching)
  - [KieSession Lifecycle](#kiesession-lifecycle)
  - [Programmatic DRL Generation](#programmatic-drl-generation)
- [Configuration Properties](#configuration-properties)
- [Fact Classes](#fact-classes)
- [Rule Categories](#rule-categories)
  - [1. Flowsheet Timeliness Rules](#1-flowsheet-timeliness-rules)
  - [2. Per-Item Decision Support Rules](#2-per-item-decision-support-rules)
  - [3. Clinical Report Numerator Rules](#3-clinical-report-numerator-rules)
  - [4. Prevention/Immunization Rules](#4-preventionimmunization-rules)
  - [5. Workflow Rules](#5-workflow-rules)
  - [6. Clinical Guideline Rules](#6-clinical-guideline-rules)
- [DRL File Reference](#drl-file-reference)
  - [Flowsheet DRL Files](#flowsheet-drl-files)
  - [Decision Support DRL Files](#decision-support-drl-files)
  - [Clinical Report Test DRL Files](#clinical-report-test-drl-files)
  - [Prevention DRL File](#prevention-drl-file)
  - [Workflow DRL Files](#workflow-drl-files)
  - [Orphaned DRL Files](#orphaned-drl-files)
- [DroolsNumerator Variants](#droolsnumerator-variants)
- [Known Bugs and Quirks](#known-bugs-and-quirks)
- [Canadian Clinical Guidelines Referenced](#canadian-clinical-guidelines-referenced)
- [Extending the System](#extending-the-system)
- [Migration Notes (Drools 2.0 → 7.x → 10.0)](#migration-notes-drools-20--7x--100)

---

## Overview

CARLOS EMR uses the [Drools](https://www.drools.org/) rule engine (version 10.0.0, executable model) for clinical decision support. The system evaluates patient data against evidence-based clinical rules to generate warnings, recommendations, and colour-coded indicators that help healthcare providers identify patients who need attention.

The Drools system was originally written for the Drools 2.0 API (XML rule format) and was migrated to the modern KIE API (DRL text format) as part of PR #423 in February 2026.

### Key Java Classes

#### Core Infrastructure

| Class | Package | Purpose |
|-------|---------|---------|
| `DroolsHelper` | `c.e.c.drools` | Central utility for loading DRL files and compiling `KieBase` instances via standard KIE API |
| `RuleBaseFactory` | `c.e.c.drools` | Thread-safe `QueueCache` of compiled `KieBase` objects with 24h TTL and `ReadWriteLock` |
| `DroolsCompilationException` | `c.e.c.drools` | Checked exception for DRL compilation or loading failures |
| `RuleBaseCreator` | `c.e.c.encounter.oscarMeasurements.util` | Generates DRL rule text from `DSCondition` objects and compiles/caches via `RuleBaseFactory` |

#### Flowsheet Subsystem

| Class | Package | Purpose |
|-------|---------|---------|
| `MeasurementFlowSheet` | `c.e.c.encounter.oscarMeasurements` | Loads/executes flowsheet-level and per-item rules; manages `KieSession` lifecycle |
| `MeasurementTemplateFlowSheetConfig` | `c.e.c.encounter.oscarMeasurements` | Singleton flowsheet configuration manager; parses XML, builds item trees, triggers rule compilation |
| `TargetColour` | `c.e.c.encounter.oscarMeasurements.util` | Generates DRL for colour-coding measurement values against clinical thresholds |
| `Recommendation` | `c.e.c.encounter.oscarMeasurements.util` | Generates DRL for time-based clinical reminders (e.g., "overdue for review") |

#### Prevention, Workflow, and Reporting

| Class | Package | Purpose |
|-------|---------|---------|
| `PreventionDSImpl` | `c.e.c.prevention` | Spring `@Component` that loads and executes prevention/immunization rules (three-tier loading) |
| `WorkFlowDSFactory` | `c.e.c.workflow` | Factory for loading and executing workflow rules (e.g., Rh-negative pregnancy) |
| `DroolsNumerator` (1-5) | `c.e.c.report.ClinicalReports` | Five variants that execute clinical report numerator test rules with different date-range strategies |

#### Clinical Decision Support Guidelines

| Class | Package | Purpose |
|-------|---------|---------|
| `DSGuidelineDrools` | `c.e.c.decisionSupport.model.impl.drools` | JPA entity that parses XML guideline definitions into programmatic DRL rules |
| `DSPreventionDrools` | `c.e.c.decisionSupport.prevention` | Parses XML prevention guidelines from `ResourceStorage` into DRL via `RuleBaseCreator` |

> **Note**: Package prefix `c.e.c` = `io.github.carlos_emr.carlos` throughout this document.

---

## Architecture

```
+-------------------------------------------------------------+
|                    Flowsheet XML Configs                      |
|  (diabetesFlowsheet.xml, hivFlowsheet.xml, chf.xml, etc.)   |
|  - ds_rules="" attribute on <flowsheet> and <item> elements  |
+------+----------------------------------+--------------------+
       |                                  |
       v                                  v
+------------------+           +----------------------+
| Flowsheet-Level  |           |  Per-Item Decision   |
| Timeliness Rules |           |   Support Rules      |
|                  |           |                      |
| TWO SOURCES:     |           | TWO SOURCES:         |
| 1. Static DRL    |           | 1. Static DRL files  |
|    (diab.drl)    |           |    (diab-BP.drl)     |
| 2. Programmatic  |           | 2. Programmatic DRL  |
|    from Recom-   |           |    from TargetColour |
|    mendation     |           |    objects           |
|    objects        |           |                      |
| Fact: Measure-   |           | Fact: Measurement-   |
|   mentInfo       |           |   DSHelper           |
+------+-----------+           +----------+-----------+
       |                                  |
       v                                  v
+-------------------------------------------------------------+
|                    MeasurementFlowSheet.java                  |
|  - Loads static DRL via DroolsHelper                         |
|  - Compiles programmatic DRL via RuleBaseCreator              |
|  - Creates KieSession, inserts facts, fires rules            |
+-------------------------------------------------------------+

+-------------------+    +------------------+    +------------------+
| Clinical Report   |    | Prevention/      |    | Workflow Rules   |
| Numerator Rules   |    | Immunization     |    | (Rh_workflow.drl)|
| (test*.drl)       |    | (prevention.drl) |    |                  |
|                   |    |                  |    | Fact: WorkFlow-  |
| Fact: Measurement-|    | Fact: Prevention |    |   Info           |
|   DSHelper        |    |                  |    |                  |
|                   |    | THREE SOURCES:   |    | Called from:     |
| Called from:      |    | 1. Filesystem    |    | WorkFlowDS-     |
| DroolsNumerator   |    | 2. Database      |    |   Factory.java   |
| (1-5 variants)    |    | 3. Classpath     |    |                  |
+-------------------+    +------------------+    +------------------+

+-----------------------------------------------------------+
|              Programmatic DRL Generation                    |
|  (No static DRL files - rules generated from XML/objects)  |
|                                                             |
|  DSGuidelineDrools    DSPreventionDrools                   |
|  (JPA entity with     (XML from resource_storage           |
|   XML conditions)      database table)                     |
|        |                      |                            |
|        v                      v                            |
|  DRL generated via     DRL generated via                   |
|  getDroolsCondition()  RuleBaseCreator.getRule()           |
|        |                      |                            |
|        v                      v                            |
|  DroolsHelper          RuleBaseCreator                     |
|  .createKieBaseFromDrl()  .getRuleBase()                   |
|        |                      |                            |
|        v                      v                            |
|  Cached in              Cached in                          |
|  RuleBaseFactory        RuleBaseFactory                    |
|                                                             |
|  Fact: DSDemographic-   Fact: Prevention                   |
|    Access                                                  |
+-----------------------------------------------------------+
```

### Rule Loading Pipeline

All DRL loading follows a filesystem-first, classpath-fallback pattern. The specific tiers vary by subsystem:

| Subsystem | Tier 1 (Highest Priority) | Tier 2 | Tier 3 (Fallback) |
|-----------|--------------------------|--------|-------------------|
| **Flowsheet rules** | `MEASUREMENT_DS_DIRECTORY` filesystem | -- | Classpath `/oscar/encounter/oscarMeasurements/flowsheets/` |
| **Per-item DS rules** | `MEASUREMENT_DS_DIRECTORY` filesystem | -- | Classpath `.../flowsheets/decisionSupport/` |
| **Prevention rules** | `PREVENTION_FILE` property (filesystem or `classpath:` prefix) | `ResourceStorage` database table (XML→DRL via `DSPreventionDrools`) | Classpath `/oscar/oscarPrevention/prevention.drl` |
| **Workflow rules** | `WORKFLOW_DS_DIRECTORY` filesystem | -- | Classpath `/oscar/oscarWorkflow/rules/` |
| **Clinical guidelines** | `RuleBaseFactory` cache (keyed by guideline ID) | Compiled on-demand from JPA entity XML | -- |

The centralized loading method `DroolsHelper.loadMeasurementRuleBase(String, Class<?>)` implements the two-tier filesystem/classpath strategy and is shared by `MeasurementFlowSheet` and all five `DroolsNumerator` variants.

### KieBase Caching

Compiled `KieBase` instances are cached in `RuleBaseFactory` to avoid expensive recompilation:

| Parameter | Value | Notes |
|-----------|-------|-------|
| **Cache type** | `QueueCache` (bucket-based) | 4 queue buckets for concurrent access distribution |
| **Max entries** | 2048 | Maximum number of `KieBase` instances cached simultaneously |
| **TTL** | 24 hours | Entries expire after `DateUtils.MILLIS_PER_DAY` |
| **Concurrency** | `ReentrantReadWriteLock` | Concurrent reads; serialized writes |
| **Key strategy (RuleBaseCreator)** | `"RuleBaseCreator:" + SHA-256(fullDrlString)` | Content-addressed: identical DRL → cache hit |
| **Key strategy (DSGuidelineDrools)** | Guideline's `ruleBaseFactoryKey` (JPA ID-based) | Invalidated on `@PostUpdate` |
| **Key strategy (prevention)** | Private `static KieBase` field | `PreventionDSImpl` bypasses `RuleBaseFactory`; reloaded via `reloadRuleBase()` |

### KieSession Lifecycle

All rule executions follow a strict lifecycle to prevent resource leaks:

```java
KieSession kieSession = kieBase.newKieSession();
try {
    kieSession.insert(factObject);  // MeasurementInfo, MeasurementDSHelper, Prevention, etc.
    kieSession.fireAllRules();
} finally {
    kieSession.dispose();           // Always dispose - sessions are never reused
}
```

Key points:
- **`KieBase` is thread-safe** and shared across requests. It is compiled once and cached.
- **`KieSession` is NOT thread-safe**. A new session is created for each rule execution and disposed immediately after.
- **`DroolsHelper.createKieBaseFromDrl()`** uses unique `ReleaseId` per compilation with the `KieServices`/`KieFileSystem`/`KieBuilder` pipeline, followed by `KieContainer.dispose()` and `KieRepository.removeKieModule()` to prevent unbounded metadata growth in long-running server processes.

### Programmatic DRL Generation

Several classes generate DRL rule text at runtime from structured data (XML or Java objects) rather than loading static `.drl` files:

| Generator Class | Input | Output DRL Fact Type | Cache Key |
|----------------|-------|---------------------|-----------|
| `TargetColour` | Flowsheet XML `<ruleset>/<rule>` elements | `MeasurementDSHelper` | SHA-256 via `RuleBaseCreator` |
| `Recommendation` | Flowsheet XML `<rules>/<recommendation>` elements | `MeasurementInfo` | SHA-256 via `RuleBaseCreator` |
| `MeasurementTemplateFlowSheetConfig` | Flowsheet XML `<rules>/<recommendation>` elements (time-based) | `MeasurementInfo` | SHA-256 via `RuleBaseCreator` |
| `DSGuidelineDrools` | JPA entity XML (conditions/parameters/consequences) | `DSDemographicAccess` | Guideline-specific key |
| `DSPreventionDrools` | `ResourceStorage` XML (prevention definitions) | `Prevention` | SHA-256 via `RuleBaseCreator` |

The programmatic DRL generation pipeline is:
1. **Parse source** → Extract conditions/parameters into `DSCondition` objects
2. **Generate DRL text** → `RuleBaseCreator.getRule()` or `DSGuidelineDrools.getRule()` builds DRL string with `eval()` expressions
3. **Compile** → `DroolsHelper.createKieBaseFromDrl()` compiles via standard KIE API
4. **Cache** → `RuleBaseFactory.putRuleBase()` stores the compiled `KieBase`

---

## Configuration Properties

These properties in `carlos.properties` control DRL file locations:

| Property | Default | Purpose |
|----------|---------|---------|
| `MEASUREMENT_DS_DIRECTORY` | *(not set)* | Filesystem directory for flowsheet-level and per-item DRL files (e.g., `diab.drl`, `decisionSupport/diab-BP.drl`) |
| `MEASUREMENT_DS_HTML_DIRECTORY` | *(not set)* | Filesystem directory for flowsheet HTML description files (not DRL, but related) |
| `PREVENTION_FILE` | *(not set)* | Path to prevention DRL file. Supports `classpath:` prefix or absolute filesystem path |
| `WORKFLOW_DS_DIRECTORY` | *(not set)* | Filesystem directory for workflow DRL files (e.g., `Rh_workflow.drl`) |

When these properties are not set, all DRL files are loaded from the classpath (the bundled defaults in the WAR file).

---

## Fact Classes

### MeasurementInfo (Flowsheet Timeliness)

**Package**: `io.github.carlos_emr.carlos.encounter.oscarMeasurements`

Used by flowsheet-level DRL files and programmatic rules (from `Recommendation` objects) to check when measurements were last recorded and generate overdue warnings.

| Method | Returns | Description |
|--------|---------|-------------|
| `getLastDateRecordedInMonths(String type)` | `int` | Months since the last recording of measurement type |
| `getLastValueAsInt(String type)` | `int` | Last recorded numeric value for measurement type |
| `addRecommendation(String type, String msg)` | `void` | Adds yellow recommendation indicator |
| `addWarning(String type, String msg)` | `void` | Adds red warning indicator |
| `getGender()` | `String` | Patient gender ("M" or "F") |
| `getAge()` | `int` | Patient age in years |

### MeasurementDSHelper (Per-Item Decision Support)

**Package**: `io.github.carlos_emr.carlos.encounter.oscarMeasurements.util`

Used by per-item decision support DRL files, programmatic rules (from `TargetColour` objects), and clinical report numerator DRL files to evaluate measurement values.

| Method | Returns | Description |
|--------|---------|-------------|
| `getDataAsDouble()` | `double` | Current measurement value as a number |
| `getNumberFromSplit(String delim, int idx)` | `double` | Split value (e.g., BP "120/80" split on "/" gives systolic at idx 0) |
| `setMeasurement(String type)` | `boolean` | Sets measurement context; always returns `true` |
| `setIndicationColor(String color)` | `void` | Sets colour indicator: "HIGH", "HIGH 1", "LOW" |
| `setInRange(boolean inRange)` | `void` | Sets in-range status (for clinical report rules) |
| `isInRange()` | `boolean` | Gets current in-range status |
| `isMale()` / `isFemale()` | `boolean` | Patient sex |
| `isDataEqualTo(String value)` | `boolean` | String comparison of measurement data |
| `getLastDateRecordedInMths()` | `int` | Months since measurement was recorded |

### Prevention (Immunization)

**Package**: `io.github.carlos_emr.carlos.prevention`

Used by `prevention.drl` and programmatic rules (from `DSPreventionDrools`) to evaluate vaccination history and generate immunization warnings.

| Method | Returns | Description |
|--------|---------|-------------|
| `getAgeInMonths()` | `int` | Patient age in months |
| `getAgeInYears()` | `int` | Patient age in years |
| `getNumberOfPreventionType(String type)` | `int` | Number of recorded doses for vaccine type |
| `getHowManyMonthsSinceLast(String type)` | `int` | Months since last dose (-1 = no record) |
| `getHowManyDaysSinceLast(String type)` | `int` | Days since last dose (0 = no record) |
| `getAgeInMonthsLastPreventionTypeGiven(String type)` | `int` | Patient age in months when last dose was given |
| `isPreventionNever(String type)` | `boolean` | Whether prevention is marked "never" (contraindicated/refused) |
| `isNextDateSet(String type)` | `boolean` | Whether a next-due date is manually set |
| `isPassedNextDate(String type)` | `boolean` | Whether the next-due date has passed |
| `isInelligible(String type)` | `boolean` | Whether patient is ineligible for this prevention |
| `isLastPreventionWithinRange(String type, String from, String to)` | `boolean` | Whether last dose was within date range |
| `isTodayinDateRange(String from, String to)` | `boolean` | Whether today is within date range |
| `isMale()` / `isFemale()` | `boolean` | Patient sex |
| `addWarning(String type, String msg)` | `void` | Adds red clinical warning |
| `addReminder(String msg)` | `void` | Adds yellow informational reminder |
| `log(String ruleName)` | `void` | Debug logging for rule activation |

### WorkFlowInfo (Workflow)

**Package**: `io.github.carlos_emr.carlos.workflow`

Used by `Rh_workflow.drl` to evaluate pregnancy workflow state and set dashboard colours.

| Method | Returns | Description |
|--------|---------|-------------|
| `getGestationAge()` | `int` | Current gestation in weeks (-1 if no EDD set) |
| `isCurrentState(String state)` | `boolean` | Checks workflow state machine position |
| `setColour(String colour)` | `void` | Sets dashboard indicator colour |

### DSDemographicAccess (Clinical Guidelines)

**Package**: `io.github.carlos_emr.carlos.decisionSupport.model`

Used by programmatic DRL rules generated by `DSGuidelineDrools` to evaluate patient data against clinical guideline conditions.

| Method | Returns | Description |
|--------|---------|-------------|
| `setPassedGuideline(boolean passed)` | `void` | Marks whether the guideline criteria are met |
| Various condition methods | `String`/`int` | Dynamic method calls generated from guideline XML conditions |

---

## Rule Categories

### 1. Flowsheet Timeliness Rules

**Location**: `src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/*.drl`

These rules check whether clinical measurements are overdue for recording. They use the `MeasurementInfo` fact class and generate warnings/recommendations based on time elapsed since last recording.

**Pattern**: Each measurement type has two rules:
- **Rule 1** (recommendation): Fires when the measurement is approaching its due date
- **Rule 2** (warning): Fires when the measurement is overdue

**Monitoring schedules vary by flowsheet**:
- **Quarterly** (3mo recommend, 6mo warn): Vital signs, key labs
- **Semi-annual** (6mo recommend, 9mo warn): Secondary labs
- **Annual** (10mo recommend, 12mo warn): Screening tests

**Referenced by**: `<flowsheet ds_rules="...">` attribute in XML config files.

**Also generated programmatically** by `Recommendation` objects parsed from `<rules>/<recommendation>` elements in flowsheet XML. These generate DRL via `Recommendation.getRuleBaseElement()` and are compiled together by `MeasurementFlowSheet.loadRuleBase()`.

### 2. Per-Item Decision Support Rules

**Location**: `src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/decisionSupport/*.drl`

These rules evaluate individual measurement VALUES against clinical thresholds and set colour indicators (HIGH, HIGH 1, LOW) that appear in the flowsheet display.

**Pattern**: Rules check the current measurement value and set `setIndicationColor()`:
- **"HIGH"**: Above target, needs attention (amber/yellow indicator)
- **"HIGH 1"**: Significantly above target, urgent (red indicator)
- **"LOW"**: Below expected range (blue indicator)

**Referenced by**: `<item ds_rules="...">` attribute on individual measurement items in XML configs.

**Also generated programmatically** by `TargetColour` objects parsed from `<ruleset>/<rule>` elements in flowsheet XML. These generate DRL via `TargetColour.getRuleBaseElement(String)` and are compiled by `MeasurementFlowSheet.loadMeasurementRuleBase(List<TargetColour>)`.

### 3. Clinical Report Numerator Rules

**Location**: `src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/decisionSupport/test*.drl`

These rules are used by the Clinical Reports module (`ClinicalReports.xml`) for population health reporting. They determine whether individual patients meet specific health indicator criteria (numerators) for clinical quality metrics.

**Pattern**: Rules evaluate measurement values and call `setInRange(true)` if the patient meets the criteria. The `DroolsNumerator` classes insert a `MeasurementDSHelper` fact, fire rules, then check `isInRange()`.

**Referenced by**: `ClinicalReports.xml` `<numerator>` elements via `droolsNumerator` indicator ID.

### 4. Prevention/Immunization Rules

**Location**: `src/main/resources/oscar/oscarPrevention/prevention.drl`

A single large DRL file containing all immunization and preventive care scheduling rules. Uses the `Prevention` fact class to evaluate patient age, vaccination history, and status flags.

**Called from**: `PreventionDSImpl.getMessages(Prevention)` with three-tier loading priority:

1. **`PREVENTION_FILE` property** -- filesystem path or `classpath:` prefix
2. **`ResourceStorage` database** -- XML converted to DRL via `DSPreventionDrools.createRuleBase(byte[])`
3. **Classpath fallback** -- bundled `/oscar/oscarPrevention/prevention.drl`

**Coverage**: DTaP-IPV, Hib, Pneu-C, Rot, MMR/MMRV, MenC-C, HPV, VZ (Varicella), dTap, Td, Flu, HPV-CERVIX, PAP, MAM (Mammogram), FOBT/Colonoscopy, Smoking, BMD, PHV, Annual Physical, Obesity.

### 5. Workflow Rules

**Location**: `src/main/resources/oscar/oscarWorkflow/rules/Rh_workflow.drl`

Workflow state machine rules for Rh-negative pregnancy management. Uses the `WorkFlowInfo` fact class to evaluate gestation age and workflow state, setting colour indicators for the dashboard.

**Called from**: `RHWorkFlow.executeRules()` via `WorkFlowDSFactory.getWorkFlowDS("Rh_workflow.drl")`.

### 6. Clinical Guideline Rules

**Location**: Generated programmatically from JPA entities (no static DRL files)

`DSGuidelineDrools` entities store clinical guideline definitions as XML. When `executeRules()` is called, the XML is parsed into `DSCondition`, `DSParameter`, and `DSConsequence` objects, which are translated into DRL rule text and compiled into a `KieBase`.

**Key methods in the DRL generation pipeline**:
- `getDroolsCondition(DSCondition)` -- converts a condition to a DRL `eval()` expression
- `getDroolsParameter(DSParameter)` -- converts a parameter to a DRL fact binding (uses FQCN inline, no import needed)
- `getDroolsConsequences(List)` -- builds the DRL `then` clause (always sets `setPassedGuideline(true)`)
- `getRule()` -- assembles the complete DRL rule string

The compiled `KieBase` is cached in `RuleBaseFactory` and invalidated on JPA `@PostUpdate`.

---

## DRL File Reference

### Flowsheet DRL Files

Located in `src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/`

| File | Fact Class | Measurements | Used By XML Configs |
|------|-----------|--------------|---------------------|
| `diab.drl` | MeasurementInfo | A1C, BP, WT, BMI, Waist, LDL, TC/HDL, TG, ACR, eGFR, FBS, NOSK, EXER | diabetesFlowsheet.xml, omdDiabetesFlowsheet.xml, omdChf.xml, omdAsthmaFlowsheet.xml, omdCOPDFlowsheet.xml, physicalFunctionFlowsheet.xml |
| `chf.drl` | MeasurementInfo | BP, WT, NOSK, A1C, LDL, TC/HDL, TG, FGLC, EYEE, ACR, eGFR, FTE, FTLS, PANE, EDGI, DMME, FBS | chf.xml |
| `ckd.drl` | MeasurementInfo | eGFR, ACR, BP, NOSK, A1C, LDL, HDL, TC/HDL, TG, FBS, Hb, FERR, TSAT, PTH, Ca, PHOS, VITD, HCO3 | ckdFlowsheet.xml |
| `hiv.drl` | MeasurementInfo | CD4, VLOA, Hb, ALT, FBS, TCHL, LDL, HDL, TG, URBH, USSH, UDUS, UAIP, UHTP, VB12, FTST, VDRL, TOXP, HpAI, HpBS, HpBA, HpCA, CMVI, G6PD | hivFlowsheet.xml |
| `hypertension.drl` | MeasurementInfo | BP, NOSK, DIER, SODI, DRPW, WT, BMI, ACR, TCHD, LDL, TG, EGFR | hypertensionFlowsheet.xml, omdHypertensionFlowsheet.xml |
| `inrFlowsheet.drl` | MeasurementInfo | COUM, INR | inrFlowsheet.xml |
| `housing.drl` | MeasurementInfo | Hass, CnAp, HAP, CCAC, CMSP, HVIS, Hsup | housingFlowsheet.xml |
| `identification.drl` | MeasurementInfo | OHIP, SIN, BCRT, LAND, Citz, Othi, Isup | identificationFlowsheet.xml |
| `intake.drl` | MeasurementInfo | Regn, Quat, CPP, CAIS, info, pink, ROI, Cons, Ornt, Insp | intakeFlowsheet.xml |
| `socialLegal.drl` | MeasurementInfo | HltA, MHA, SUA, Lwil, SoCm, EdTr, EmpA, LegA, Osup | socialLegalFlowsheet.xml |
| `finances.drl` | MeasurementInfo | *(CAISI financial assessment)* | financesFlowsheet.xml |

### Decision Support DRL Files

Located in `src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/decisionSupport/`

| File | Fact Class | Evaluates | Thresholds | Used By |
|------|-----------|-----------|------------|---------|
| `diab-A1C.drl` | MeasurementDSHelper | A1C value | >=7.0% or 0.07-2.0 (fraction) = HIGH | diabetesFlowsheet.xml, omdDiabetesFlowsheet.xml |
| `diab-ACR.drl` | MeasurementDSHelper | Albumin/Creatinine Ratio | M: >=2.0 HIGH; F: >=2.0 HIGH | diabetesFlowsheet.xml, chf.xml |
| `diab-BMI.drl` | MeasurementDSHelper | Body Mass Index | 25-30 HIGH, >30 HIGH 1, <18.5 LOW | diabetesFlowsheet.xml |
| `diab-BP.drl` | MeasurementDSHelper | Blood Pressure (sys/dia) | >130/80 combinations = HIGH | 5 XML configs (most widely used) |
| `diab-C-no-is-high.drl` | MeasurementDSHelper | Yes/No questions | "No" = HIGH (concerning) | FGLC, EYEE, FTE, FTLS items |
| `diab-C-yes-is-high.drl` | MeasurementDSHelper | Yes/No questions | "Yes" = HIGH (concerning) | PANE, EDGI items |
| `diab-EFGR.drl` | MeasurementDSHelper | eGFR | <=60 = LOW (CKD stage 3+) | diabetesFlowsheet.xml, chf.xml |
| `diab-LDL.drl` | MeasurementDSHelper | LDL Cholesterol | 2.0-3.4 HIGH, >=3.5 HIGH 1 | diabetesFlowsheet.xml, chf.xml |
| `diab-TCHDL.drl` | MeasurementDSHelper | TC/HDL Ratio | 4.0-4.9 HIGH, >=5.0 HIGH 1 | diabetesFlowsheet.xml, chf.xml |
| `diab-TG.drl` | MeasurementDSHelper | Triglycerides | >=2.0 = HIGH | diabetesFlowsheet.xml, chf.xml |
| `INR.drl` | MeasurementDSHelper | INR value | <2.0 LOW, 4.0-4.9 HIGH, >=5.0 HIGH 1 | inrFlowsheet.xml |

### Clinical Report Test DRL Files

Located in `src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/decisionSupport/`

Referenced by `ClinicalReports.xml` for population health indicator reporting.

| File | Evaluates | Criteria for `setInRange(true)` |
|------|-----------|-------------------------------|
| `testA1C.drl` | A1C control | A1C <= 7.0% (or fraction <= 0.07) AND within 13 months |
| `testA1Cabove7p9.drl` | Poor A1C control | A1C > 7.9% AND within 13 months |
| `testBPabove139.drl` | Elevated BP | Systolic > 139 AND within 13 months |
| `testBPlower130_80.drl` | BP at target | BP <= 130/80 AND within 13 months |
| `testBPlower131.drl` | Systolic at target | Systolic < 131 AND within 13 months |
| `testLDLlower2p6.drl` | LDL at target | LDL < 2.0 AND within 13 months (NOTE: filename says 2.6, code uses 2.0) |
| `testTripleWhammy.drl` | Triple target met | LDL < 2.0 AND BP <= 130/80 AND A1C <= 7.0% (sequential AND) |
| `testCD4lower200.drl` | AIDS-defining CD4 | CD4 < 200 AND within 13 months |
| `testCD4between200350.drl` | Moderate immunosuppression | CD4 200-349 AND within 13 months |
| `testCD4high350.drl` | Preserved immunity | CD4 >= 350 AND within 13 months |

### Prevention DRL File

| File | Location | Fact Class | Vaccine/Prevention Types |
|------|----------|-----------|------------------------|
| `prevention.drl` | `src/main/resources/oscar/oscarPrevention/` | Prevention | DTaP-IPV (6 rules), Tdap-IPV (1), Rot (2), Hib (6), Pneu-C (6), MMR (2), MMRV (1), MenC-C (3), HPV (1), VZ (4), dTap (1), Td (2), Flu (4), HPV-CERVIX (3), PAP (4+debug), MAM (5+debug), FOBT (4), Smoking (1), BMD (1), PHV (2), Annual Physical (2), Obesity (2) |

### Workflow DRL Files

| File | Location | Fact Class | Purpose |
|------|----------|-----------|---------|
| `Rh_workflow.drl` | `src/main/resources/oscar/oscarWorkflow/rules/` | WorkFlowInfo | Rh-negative pregnancy management (10 rules tracking gestation age vs. RhIg injection timeline) |

### Orphaned DRL Files

These files exist in the `decisionSupport/` directory but are **NOT referenced** by any XML config, Java code, or other configuration. They are candidates for deletion.

| File | Description | Likely Original Purpose |
|------|-------------|----------------------|
| `testBPabove140_90.drl` | BP >140/90 with 13-month window | Custom report numerator |
| `testBPabove140_90_1.drl` | BP >140/90 with 7-month window | Variant with stricter recency |
| `testBPhigher130_80.drl` | BP >130/80 with no recency window | Diabetes-specific BP threshold |
| `testBPlower140_90.drl` | BP <=140/90 with 13-month window | General hypertension target |
| `testBPlower140_90_1.drl` | BP <=140/90 with 7-month window | Variant with stricter recency |

---

## DroolsNumerator Variants

The Clinical Reports module uses five `DroolsNumerator` variants (all in `io.github.carlos_emr.carlos.report.ClinicalReports`) that implement the `Numerator` interface. Each variant applies a different date-range filtering strategy before evaluating the patient's measurement against a DRL rule:

| Class | Date Range Strategy | Measurement Selection | Use Case |
|-------|--------------------|-----------------------|----------|
| `DroolsNumerator` | **No date filtering** | Most recent value regardless of age | Simple current-value checks |
| `DroolsNumerator2` | **Explicit start/end dates** from `ClinicalReports.xml` | Most recent value within the specified date range | Date-bounded indicator reporting |
| `DroolsNumerator3` | **N-month lookback** from indicator `mth` attribute | Most recent value within the last N months | Recency-qualified checks (e.g., "A1C within last 13 months") |
| `DroolsNumerator4` | **Explicit start/end dates** (same as variant 2) | Most recent value within the specified date range | Identical to variant 2 (consolidation candidate) |
| `DroolsNumerator5` | **N-month lookback** (same as variant 3) | Most recent value within the last N months | Identical to variant 3 (consolidation candidate) |

All five variants share the same DRL loading logic (delegated to `DroolsHelper.loadMeasurementRuleBase()`), the same `KieSession` lifecycle, and the same `MeasurementDSHelper` fact type. Variants 4 and 5 are near-duplicates of 2 and 3 respectively and are tracked for consolidation in GitHub issue #411.

---

## Known Bugs and Quirks

### Copy-Paste Artifacts in prevention.drl

| Rule | Bug | Impact |
|------|-----|--------|
| DTaP-IPV 4.1 | Conditions check `count==2` (should be `count==3` for 4th dose) | Duplicates DTaP-IPV 3.1 behavior |
| DTaP-IPV 5, 6 | Warning says "Needs Third DTaP-IPV" | Incorrect message for 5th/6th doses |
| MenC-C 1 | Log says "Pneu-C 4" | Wrong debug log message |
| Rot 1, 2 | Uses `System.out.println()` instead of `prev.log()` | Inconsistent logging |
| Flu 1 | Hardcoded 2005-2006 date range | Effectively dead code |
| HPV-CERVIX 3 | Message: "Neither" | Typo (should be "Neither") |
| FOBT Ineligible | Rule name and `isIneligible()` method | Misspelling of "Ineligible" |
| Hib 6 | Warning says "Needs second Hib" | Should say "Needs first Hib" (count==0) |

### Wrong Measurement Type in addWarning() (identification.drl)

| Rule | Passes | Should Pass |
|------|--------|------------|
| Citz 1 | "LAND" | "Citz" |
| Othi 1 | "LAND" | "Othi" |
| Isup 1 | "LAND" | "Isup" |

### Wrong Measurement Type in addWarning() (socialLegal.drl)

| Rule | Passes | Should Pass |
|------|--------|------------|
| SoCm 1 | "Lwil" | "SoCm" |

### Package Name Mismatches (Fixed)

All DRL package names now match their file purpose. Previously, 14 files had
inherited incorrect package names from copy-paste during the original Drools 2.0
XML era (e.g., all BP/LDL/TripleWhammy files used `package ReportA1C;`). These
were corrected during the Drools 7.x migration since the package declaration is
a DRL-internal namespace with no effect on rule execution in this codebase.

### Threshold Inconsistencies

| File | Filename Implies | Code Uses | XML Says |
|------|-----------------|-----------|----------|
| `testLDLlower2p6.drl` | LDL < 2.6 | LDL < 2.0 | "LDL lower than 2.5" |

### Decision Support Log Message Artifacts

| File | Log Says | Actually Evaluates |
|------|----------|-------------------|
| `diab-EFGR.drl` | "ACR RULES" | eGFR values |

### Pre-existing Code Bugs in DSPreventionDrools (Resolved)

These bugs existed in the legacy `DSPreventionDrools` methods `handleNumberOfPreventionsCondition()` and
`handleMonthsSinceLastCondition()`. Both methods were replaced by the unified `processGenericNumberValues()`
during the Drools 7 migration, which resolved both issues:

- **Discarded `replaceAll` return value** (OR conditions silently failed) — eliminated; `processGenericNumberValues()` does not use string-based OR concatenation.
- **Negative month misparsing** (split on `"-"` at position 0) — fixed; `processGenericNumberValues()` explicitly guards against this with `toParse.indexOf("-") != 0`.

---

## Canadian Clinical Guidelines Referenced

The DRL rules implement thresholds from these Canadian clinical practice guidelines:

| Guideline Organization | Rules Using It |
|-----------------------|---------------|
| **Diabetes Canada** (formerly CDA) | A1C target <=7.0%, BP target <130/80, LDL <2.0 mmol/L |
| **Hypertension Canada** | BP targets, DASH diet, sodium <2000mg, alcohol limits |
| **Canadian Cardiovascular Society (CCS)** | LDL tiers (2.0-3.4 moderate, >=3.5 significant), TC/HDL ratio |
| **KDIGO (CKD staging)** | eGFR <=60 (Stage 3+), ACR thresholds |
| **NACI (National Advisory Committee on Immunization)** | All vaccine schedules in prevention.drl |
| **Canadian Task Force on Preventive Health Care** | PAP (3yr), HPV-CERVIX (5yr), MAM (2yr), FOBT (2yr), Smoking, BMD |
| **Osteoporosis Canada** | BMD screening at 65+ |
| **SOGC (Society of Obstetricians and Gynaecologists)** | RhIg at 28 weeks gestation |

---

## Extending the System

### Adding a New Flowsheet Timeliness Rule

1. Create or edit a DRL file in `src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/`
2. Use `MeasurementInfo` as the fact class
3. Follow the two-rule pattern: recommendation at early threshold, warning at late threshold
4. Use `m.addRecommendation("TYPE", "message")` for approaching-due alerts
5. Use `m.addWarning("TYPE", "message")` for overdue alerts
6. Reference it in the flowsheet XML: `<flowsheet ds_rules="yourfile.drl" ...>`

**Alternative (no DRL file)**: Add `<rules><recommendation>` elements directly in the flowsheet XML item definition. The `Recommendation` class will generate DRL programmatically.

### Adding a New Decision Support Rule

1. Create a DRL file in `src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/decisionSupport/`
2. Use `MeasurementDSHelper` as the fact class
3. Set indication colours for out-of-range values using `m.setIndicationColor("HIGH")`, `"HIGH 1"`, or `"LOW"`
4. Reference it on a measurement item: `<item ds_rules="yourfile.drl" ...>`

**Alternative (no DRL file)**: Add `<ruleset><rule>` elements directly in the flowsheet XML item definition. The `TargetColour` class will generate DRL programmatically.

### Adding a New Clinical Report Numerator

1. Create a `test*.drl` file in the `decisionSupport/` directory
2. Use `MeasurementDSHelper` as the fact class
3. Call `m.setInRange(true)` when the patient meets the criteria
4. Add a `<numerator>` entry in `ClinicalReports.xml` referencing the DRL
5. Choose the appropriate `DroolsNumerator` variant based on your date-range strategy (see [DroolsNumerator Variants](#droolsnumerator-variants))

### Adding a New Prevention Rule

1. Edit `prevention.drl` directly (all prevention rules are in one file)
2. Use the `Prevention` fact class
3. Follow the existing naming convention: `"VaccineCode N"` for dose N
4. Use `prev.addWarning()` for overdue alerts, `prev.addReminder()` for informational messages

### Site-Specific Rule Overrides

To override default rules without modifying the application:

1. Set the appropriate property in `carlos.properties`:
   - `MEASUREMENT_DS_DIRECTORY=/path/to/custom/flowsheet/rules/`
   - `PREVENTION_FILE=/path/to/custom/prevention.drl`
   - `WORKFLOW_DS_DIRECTORY=/path/to/custom/workflow/rules/`
2. Place your customized DRL file(s) in the specified directory
3. The filesystem version takes priority over the bundled classpath version
4. `MEASUREMENT_DS_DIRECTORY` and `WORKFLOW_DS_DIRECTORY` file paths are validated via `PathValidationUtils.validatePath()` to prevent path traversal. `PREVENTION_FILE` is an admin-configured property (not user input) and is loaded directly without path traversal validation

### DRL Syntax Reference

All CARLOS DRL files use standard DRL syntax (compatible with Drools 7.x through 10.x):
- **Package declaration**: `package PackageName;`
- **Imports**: `import fully.qualified.ClassName;`
- **Rules**: `rule "Name" when <conditions> then <actions> end`
- **Conditions**: Use Drools `eval()` wrapper around Java boolean expressions
- **Fact binding**: `variable : ClassName()` binds the fact for use in conditions/actions
- **All rules fire**: Drools evaluates ALL rules whose conditions match (no short-circuit)

For the full Drools DRL language reference, see the [Drools Documentation](https://www.drools.org/learn/documentation.html).

---

## Migration Notes (Drools 2.0 → 7.x → 10.0)

### Drools 7.74.1 → 10.0.0 (Jakarta EE Migration)

Drools was upgraded from 7.74.1.Final to 10.0.0 for Jakarta EE compatibility. Drools 10.0.0 requires JDK 17+ (CARLOS uses JDK 21) and uses the executable model by default.

**Key changes:**
1. **Replaced `KieHelper` with standard KIE API**: The internal `org.kie.internal.utils.KieHelper` class throws `UnsupportedOperationException` in Drools 10 with the executable model. `DroolsHelper.createKieBaseFromDrl()` now uses the standard `KieServices` → `KieFileSystem` → `KieBuilder` → `KieContainer` pipeline.
2. **Replaced individual dependencies with `drools-engine` aggregator**: The four individual dependencies (`kie-api`, `drools-core`, `drools-compiler`, `drools-mvel`) were replaced by the single `drools-engine` aggregator dependency which includes the executable model compiler.
3. **Removed `mvel2` override**: Drools 10 manages its own MVEL dependency; the explicit `mvel2:2.5.2.Final` override is no longer needed.
4. **Thread safety via unique ReleaseId**: Each compilation uses a UUID-based `ReleaseId` to isolate concurrent compilations in the global KIE repository.
5. **DRL files unchanged**: All 39 DRL files are fully backward-compatible with Drools 10.0.0 syntax.

### Drools 2.0 → 7.74.1 (Original Migration, PR #423)

The Drools subsystem was migrated from Drools 2.0 (XML rule format) to Drools 7.74.1.Final (KIE API with DRL text format) in PR #423, February 2026.

#### API Changes

| Old (Drools 2.0) | New (Drools 7.74.1+) | Notes |
|-------------------|----------------------|-------|
| `org.drools.RuleBase` | `org.kie.api.KieBase` | Thread-safe compiled rule container |
| `org.drools.WorkingMemory` | `org.kie.api.runtime.KieSession` | Stateful rule execution session (not thread-safe) |
| `org.drools.io.RuleBaseLoader.loadFromInputStream()` | `DroolsHelper.loadFromInputStream()` | Custom wrapper using standard KIE API |
| `org.drools.io.RuleBaseLoader.loadFromUrl()` | `DroolsHelper.loadFromUrl()` | Custom wrapper using standard KIE API |
| `ruleBase.newWorkingMemory()` | `kieBase.newKieSession()` | Session creation |
| `workingMemory.assertObject()` | `kieSession.insert()` | Fact insertion |
| `workingMemory.fireAllRules()` | `kieSession.fireAllRules()` | Rule execution |
| *(no equivalent)* | `kieSession.dispose()` | Session cleanup (required in 7.x+) |

#### New Classes Introduced

| Class | Purpose |
|-------|---------|
| `DroolsHelper` | Centralizes DRL compilation via standard KIE API with error checking |
| `DroolsCompilationException` | Checked exception for compilation failures (replaces silent `null` returns) |

#### Key Design Decisions

1. **Standard KIE API**: Uses `KieServices`/`KieFileSystem`/`KieBuilder` for compilation with unique `ReleaseId` per compilation to ensure thread safety.

2. **`DroolsHelper.loadMeasurementRuleBase()`**: Centralized the two-tier measurement DRL loading logic that was previously copy-pasted across `MeasurementFlowSheet` and all five `DroolsNumerator` variants (6 copies reduced to 1).

3. **DRL format preserved**: All static `.drl` files were converted from Drools 2.0 XML rule format to DRL text format, but the rule logic (conditions, consequences, fact method calls) remains identical.

4. **Backward compatibility**: The migration is transparent to the rest of the codebase. All callers use the same `KieBase`/`KieSession` pattern, and the `RuleBaseFactory` cache operates identically (just stores `KieBase` instead of `RuleBase`).

#### Files Changed (Original 2.0 → 7.x Migration)

- **22 Java files**: API migration (`RuleBase` → `KieBase`, `WorkingMemory` → `KieSession`)
- **~40 DRL files**: Converted from Drools 2.0 XML format to DRL text format
- **8 test files**: 101 tests covering Drools infrastructure, DRL compilation, and subsystem integration
- **pom.xml**: Updated Drools dependency from 2.0 to 7.74.1.Final
- **carlos.properties**: Updated property comments for new API

---

## Test Coverage

The Drools subsystem has 101 modern JUnit 5 tests (tagged `@Tag("drools")`) covering the core infrastructure and all production DRL files. Tests are located in `src/test-modern/java/io/github/carlos_emr/carlos/`.

### Running Drools Tests

```bash
# Run all modern tests (includes Drools)
make install --run-modern-tests

# Run only unit tests (includes Drools unit tests)
make install --run-unit-tests

# Run by tag
mvn test -Dgroups="drools"

# Run a specific test class
mvn test -Dtest="DroolsHelperUnitTest"
```

### Test Files

| Test File | Class Under Test | Tests | What It Verifies |
|-----------|-----------------|-------|-----------------|
| `drools/DroolsCompilationExceptionUnitTest` | `DroolsCompilationException` | 4 | Constructor contracts, checked exception type |
| `drools/DroolsHelperUnitTest` | `DroolsHelper` | 12 | DRL compilation from String/InputStream/URL, null/empty/syntax-error handling, end-to-end rule firing |
| `drools/RuleBaseFactoryUnitTest` | `RuleBaseFactory` | 12 | Cache put/get/remove/flush, null validation, entry isolation |
| `drools/DrlCompilationIntegrationTest` | All production DRL files | 39 | Compilation of all ~40 DRL files modified in the migration (flowsheets, decision support, prevention, workflow) |
| `encounter/.../RuleBaseCreatorUnitTest` | `RuleBaseCreator` | 10 | DRL generation from DSCondition objects, SHA-256 cache keying, import deduplication |
| `encounter/.../TargetColourUnitTest` | `TargetColour` | 8 | Color-coded indicator DRL generation, XML serialization/deserialization round-trip |
| `encounter/.../RecommendationUnitTest` | `Recommendation` | 10 | Strength-based DRL consequences (hidden/warning/recommendation), $NUMMONTHS substitution, XML round-trip |
| `workflow/WorkFlowDSUnitTest` | `WorkFlowDS` | 6 | KieSession lifecycle (create, fire, dispose), rule firing verification, null KieBase guard, exception safety |

### Test Architecture

All tests use plain JUnit 5 with no Spring context (the Drools classes are standalone utilities). Key design decisions:

- **Real Drools compilation**: Tests compile actual DRL (not mocked) to verify the full KIE compilation pipeline
- **`@BeforeEach` cache flush**: Tests that interact with `RuleBaseFactory` flush the static cache before each test for isolation
- **Inline test DRL**: Tests use minimal DRL strings with `AtomicBoolean` or `WorkFlowInfo` facts rather than depending on production DRL files (except `DrlCompilationIntegrationTest` which deliberately tests production DRLs)
- **BDD naming**: `should<Action>_<preposition><Condition>()` convention per project standards

### What Is NOT Tested (and Why)

These classes are excluded because they require database access, Spring context, or complex mocking infrastructure:

| Class | Reason | Future Plan |
|-------|--------|-------------|
| `DSGuidelineDrools` | JPA entity with `@PostUpdate` callbacks; needs EntityManager | Requires Spring integration test infrastructure |
| `DSPreventionDrools` | XML-to-DRL conversion with `ResourceBundle` lookups | Requires complex XML fixtures |
| `PreventionDSImpl` | Spring `@Component` with `ResourceStorageDao` + `OscarProperties` | Requires Spring context |
| `MeasurementFlowSheet` | 15+ dependencies including DAO layer | Requires deep mocking |
| `MeasurementTemplateFlowSheetConfig` | Singleton with XML templates and flowsheet config | Requires infrastructure setup |
| `DroolsNumerator` 1-5 | `evaluate()` needs `LoggedInInfo` + database | Identical code across 5 variants (consolidation candidate, see #411) |
