# CARLOS EMR — i18n Coverage Baseline Report

**Generated:** April 11, 2026
**Scripts:** `scripts/audit-i18n-coverage.sh` · `scripts/check-i18n-properties.sh`
**Raw data:** `i18n-coverage-report.csv` (project root)

This report establishes the i18n baseline for the CARLOS EMR codebase. Use it to
track remediation progress by re-running the audit scripts and comparing against
these baseline numbers.

---

## Executive Summary

| Metric | Value |
|--------|-------|
| JSP/JSPF files scanned | **1,090** |
| Fully i18n'd | **209** (19%) |
| Partially i18n'd | **357** (32%) |
| No i18n coverage | **524** (48%) |
| Files using legacy inline `fmt:setBundle` | **505** (46%) |
| English properties keys | **8,143** |
| Locales | en, es, fr, pl, pt_BR |

**Overall i18n completion: 19% (full) + 32% (partial) = 51% of files touched**

The remaining 48% of files have zero `fmt:message` usage and are entirely hardcoded
in English. The largest untranslated areas are `form/` (clinical forms, 172 files),
`billing/` (181 files), and `encounter/` (93 files).

---

## JSP Coverage by Domain

### Summary Table

| Domain | Total | None | Partial | Full | % Done |
|--------|-------|------|---------|------|--------|
| `admin/` | 65 | 22 | 18 | 25 | 38% full |
| `appointment/` | 22 | 4 | 5 | 13 | 59% full |
| `billing/` | 181 | 106 | 59 | 16 | 9% full |
| `casemgmt/` | 44 | 32 | 8 | 4 | 9% full |
| `demographic/` | 43 | 12 | 16 | 15 | 35% full |
| `documentManager/` | 12 | 0 | 8 | 4 | 33% full |
| `eform/` | 29 | 8 | 11 | 10 | 34% full |
| `encounter/` | 93 | 27 | 34 | 32 | 34% full |
| `form/` | 172 | 104 | 63 | 5 | 3% full |
| `lab/` | 20 | 8 | 12 | 0 | 0% full |
| `mcedt/` | 16 | 15 | 1 | 0 | 0% full |
| `messenger/` | 15 | 8 | 4 | 3 | 20% full |
| `oscarMDS/` | 15 | 4 | 10 | 1 | 7% full |
| `oscarPrevention/` | 9 | 2 | 7 | 0 | 0% full |
| `oscarReport/` | 31 | 16 | 8 | 7 | 23% full |
| `oscarResearch/` | 8 | 3 | 1 | 4 | 50% full |
| `oscarRx/` | 38 | 12 | 19 | 7 | 18% full |
| `provider/` | 66 | 30 | 20 | 16 | 24% full |
| `report/` | 17 | 11 | 5 | 1 | 6% full |
| `schedule/` | 12 | 1 | 2 | 9 | 75% full |
| `tickler/` | 8 | 3 | 2 | 3 | 38% full |
| `WEB-INF/` | 87 | 40 | 29 | 18 | 21% full |
| *(other/root)* | 87 | — | — | — | — |

> **Note:** The domain table covers 1,003 files across named domains. The executive
> summary total of 1,090 includes an additional 87 files in root-level webapp paths
> and unlisted subdirectories, reflected in the *(other/root)* row above.

---

## Priority 1: admin/, provider/, demographic/

These domains are the highest-frequency daily workflows (administration, patient records,
provider configuration) and represent the highest clinical and operational impact.

### admin/ — 40 files need work (22 none + 18 partial)

| File | Category | Hardcoded Count |
|------|----------|-----------------|
| `admin/jobs.jsp` | none | 33 |
| `admin/eformReportTool/eformReportTool.jsp` | none | 18 |
| `admin/configureFax.jsp` | none | 17 |
| `admin/billingreferralAdmin.jsp` | none | 16 |
| `admin/api/clients.jsp` | none | 15 |
| `admin/sitesAdminDetail.jsp` | none | 13 |
| `admin/sitesAdmin.jsp` | none | 13 |
| `admin/providerPrivilege.jsp` | none | 13 |
| `admin/UsageReport.jsp` | none | 13 |
| `admin/manageFaxes.jsp` | none | 12 |

**Remediation command:**
```bash
awk -F',' '$2=="admin" && ($3=="none" || $3=="partial") {print $5, $1}' \
    i18n-coverage-report.csv | sort -rn
```

### provider/ — 50 files need work (30 none + 20 partial)

| File | Category | Hardcoded Count |
|------|----------|-----------------|
| `provider/cpp_preferences.jsp` | none | 25 |
| `provider/formALPHAprint1.jsp` | none | 24 |
| `provider/clients.jsp` | none | 12 |
| `provider/setLabRecallPrefs.jsp` | none | 7 |
| `provider/providerAddress.jsp` | none | 4 |
| `provider/appointmentprovideradminmonth.jsp` | none | 4 |
| `provider/appointmentprovideradminday.jsp` | none | 4 |

### demographic/ — 28 files need work (12 none + 16 partial)

| File | Category | Hardcoded Count |
|------|----------|-----------------|
| `demographic/contact.jsp` | none | 32 |
| `demographic/demographiceditdemographic.jsp` | partial | 21 |
| `demographic/AddAlternateContact.jsp` | none | 12 |
| `demographic/procontact.jsp` | none | 10 |
| `demographic/EnrollmentHistory.jsp` | none | 9 |
| `demographic/displayHealthCareTeam.jsp` | none | 6 |
| `demographic/demographicAudit.jsp` | none | 6 |

**Note on `demographiceditdemographic.jsp`:** This file already uses the JS i18n
`var i18n = {}` pattern correctly. The 21 remaining hardcoded items are in HTML
elements that need `fmt:message` wrapping.

---

## Priority 2: appointment/, tickler/, schedule/

### appointment/ — 9 files need work (4 none + 5 partial)

`appointment/` is relatively well converted at 59% full. The 4 `none` files and
5 `partial` files are the gap to close.

### tickler/ — 5 files need work (3 none + 2 partial)

`tickler/ticklerAdd.jsp` is a model implementation (uses `var i18n = {}` JS pattern
and proper bundle declaration). The 5 remaining files should follow this pattern.

### schedule/ — 3 files need work (1 none + 2 partial)

`schedule/` is the best-converted high-priority domain at 75% full. Only 3 files
remain.

---

## Priority 3–5: billing/, encounter/, casemgmt/, form/

### billing/ — 165 files need work (106 none + 59 partial)

The largest single remediation effort (excluding `form/`). BC/ON provincial billing
forms contain dense tabular data with hardcoded English labels. Recommend tackling
domain-agnostic `billing/` utilities first, then province-specific forms.

Top hardcoded file: `billing/CA/BC/adjustBill.jsp` (90 instances).

### encounter/ — 61 files need work (27 none + 34 partial)

The `encounter/` domain has significant partial coverage (34 files) which is good
— 34 files have established key namespaces already. Completing partial files is
lower effort than starting from scratch.

### form/ — 167 files need work (104 none + 63 partial)

Clinical forms (`form/`) are the single largest domain by file count (172 files).
Most are complex multi-page forms like BCAR, SF-36, and Rourke growth charts.
Translation of these forms requires clinical domain expertise and should be reviewed
by healthcare professionals. Schedule separately from the generic i18n work.

**Top hardcoded form files:**
| File | Hardcoded Count |
|------|-----------------|
| `form/formBCAR2020pg1.jsp` | 308 |
| `form/formintakeinfo.jsp` | 163 |
| `form/formchf.jsp` | 133 |
| `form/formannualfemaleV2.jsp` | 122 |
| `form/formannualmaleV2.jsp` | 116 |

---

## Legacy Bundle Pattern

**505 files** (46%) use the legacy inline `fmt:setBundle` pattern where the bundle
is re-declared on the same line as each `fmt:message`. This is a cleanup task that
can be batched independently of new translations:

```bash
# Files using legacy pattern, sorted by occurrences
awk -F',' 'NR>1 && $6>0 {print $6, $1}' i18n-coverage-report.csv | sort -rn | head -20
```

This can be fixed in a simple search-and-replace pass:
1. Remove all inline `<fmt:setBundle basename="oscarResources"/>` occurrences
2. Add a single `<fmt:setBundle basename="oscarResources"/>` before `<!DOCTYPE html>`

---

## Properties Key Parity

All five locale files have been checked as of April 11, 2026:

| Locale | Total Keys | Missing (vs EN) | Orphaned (vs EN) | Coverage |
|--------|-----------|-----------------|-------------------|----------|
| `en` | 8,143 | — | — | baseline |
| `es` | 8,213 | **31** | 101 | 99% |
| `fr` | 8,314 | **12** | 183 | 99% |
| `pl` | 7,823 | **355** | 35 | 95% |
| `pt_BR` | 8,239 | **31** | 127 | 99% |

**Encoding:** All five files pass ISO 8859-1 compliance check (no raw non-ASCII bytes).

### Universal Gaps (missing from ALL non-English locales)

These 12 keys exist in English but are untranslated in every other locale. Add
them as a single first-pass commit:

```
global.gender.female
global.gender.intersex
global.gender.male
global.gender.other
global.gender.undisclosed
inboxhub.toolbar.acknowledged
inboxhub.toolbar.all
inboxhub.toolbar.rapidReview
inboxhub.toolbar.showAllTypes
inboxhub.toolbar.showOnlyDocuments
inboxhub.toolbar.showOnlyHrms
inboxhub.toolbar.showOnlyLabs
```

### Polish (pl) — Largest Gap

Polish has 355 missing keys, concentrated in the `admin.*` namespace (lot management,
code styles, group ACL) and `demographic.*`. Many appear to be keys added after the
last bulk Polish translation update. Recommend a targeted batch update.

---

## Recommended Remediation Order

### Immediate Wins (High Impact, Low Effort)

1. **Add 12 universal-gap keys** to all 4 non-English locales (~5 min)
2. **Add 31 missing es/pt_BR keys** (all in `dms.*` and `messenger.*`) to Spanish and Portuguese
3. **Verify French is complete** after step 1 — the 12 universal-gap keys cover the French gap entirely

### Phase 1 Conversion Targets

Target files with `category=none` in `admin/`, `provider/`, `demographic/`:

```bash
# Generate a work list
awk -F',' '$3=="none" && ($2=="admin" || $2=="provider" || $2=="demographic") \
    {print $5, $2, $1}' i18n-coverage-report.csv | sort -rn | head -30
```

### Phase 2 Conversion Targets

Complete partial files in `appointment/`, `tickler/`, `encounter/`:

```bash
awk -F',' '$3=="partial" && ($2=="appointment" || $2=="tickler" || $2=="encounter") \
    {print $5, $2, $1}' i18n-coverage-report.csv | sort -rn
```

### Legacy Bundle Cleanup

Can be batched as a separate PR for any domain:

```bash
awk -F',' 'NR>1 && $6>5 {print $6, $2, $1}' i18n-coverage-report.csv | sort -rn | head -20
```

---

## Re-running the Audit

To update this baseline after remediation work:

```bash
# Re-run JSP coverage audit (overwrites i18n-coverage-report.csv)
./scripts/audit-i18n-coverage.sh

# Re-run properties parity check
./scripts/check-i18n-properties.sh

# Compare new full/partial/none counts against baseline
# Baseline: full=209, partial=357, none=524, legacy=505
```

Track progress by comparing the summary output of `audit-i18n-coverage.sh` against
the baseline numbers in this document.
