# apconfig OSCAR19 Compatibility Review Design

## Goal

Bring CARLOS `apconfig.xml` to a state where it can do everything the OSCAR19-era `apconfig.xml` can do, plus CARLOS-specific additions, while preserving functional equivalence for shared tags.

This review is not name-only parity. Each OSCAR19 tag is treated as a behavior contract.

## Comparator Baseline

Use the OSCAR public mirror `stable` branch `src/main/resources/oscar/eform/apconfig.xml` as the OSCAR19-era baseline comparator.

Reasoning:
- The branch head is dated July 16, 2019.
- It is a reasonable public OSCAR19-era reference point.
- If a more authoritative OSCAR19 artifact is later provided, rerun the review against that artifact and treat this document as provisional.

## Review Standard

For each OSCAR19 `ap-name`, classify the CARLOS state as exactly one of:

### Equivalent

The CARLOS-backed query may differ internally, but the tag is functionally equivalent to OSCAR19 in:
- returned value semantics
- lookup scope
- practical behavior in eforms

### Diverged

The tag exists in CARLOS, but the effective behavior differs from OSCAR19.

Examples:
- broader or narrower lookup scope
- different value formatting
- changed fallback behavior
- changed source selection that alters user-visible output

These should be fixed in CARLOS if the OSCAR19 behavior is still intended.

### Blocked/Flagged

The OSCAR19 backend path for the tag no longer exists in CARLOS, or equivalence cannot be proven from current CARLOS data paths.

These cases must be recorded in one aggregate issue ticket, per supervisor direction.

## Decision Rules

Use the following rules during review:

1. Shared OSCAR19 tags should preserve OSCAR19 behavior unless equivalence can be proven with a different CARLOS backend.
2. CARLOS-only enhancements are acceptable if they are added under new tags or do not alter OSCAR19 tag behavior.
3. If OSCAR19 used a removed table, field, or workflow and CARLOS cannot prove equivalent output, classify the tag as `Blocked/Flagged`.
4. Duplicate `ap-name` definitions are not automatically safe. The runtime loader returns the first matching tag definition, so order matters.

## Known Initial Candidate Cases

These cases are already identified from the first comparison pass and should seed the review.

### Likely Blocked/Flagged

- `onGTPAL`
- `onEDB`

Reason:
- Present in OSCAR comparator
- Removed from CARLOS
- OSCAR source path depends on `formONAREnhancedRecord`, which CARLOS has explicitly removed

### Likely Diverged

- `_eform_values_first`
- `_eform_values_last`
- `_eform_values_first_all_json`
- `_eform_values_last_all_json`
- `_eform_values_count`
- `_eform_values_countname`
- `_eform_values_count_ref`
- `_eform_values_countname_ref`
- `_eform_values_count_refname`
- `_eform_values_countname_refname`

Reason:
- OSCAR comparator uses `demographic_no like '${eform_demographic}'`
- CARLOS uses `demographic_no = '${eform_demographic}'`
- CARLOS runtime still sets `eform_demographic = "%" ` for patient-independent eforms
- This likely changes lookup scope and breaks OSCAR-style wildcard behavior

### Additional Divergence Candidates

- `address`
- `addressline`
- `province`

Reason:
- CARLOS returns full province strings where OSCAR comparator returns 2-letter province abbreviations

### Review Candidates Requiring Verification

All broadened immunization lookup tags should be reviewed for equivalence rather than assumed safe.

Observed pattern:
- OSCAR comparator often uses exact `prevention_type` matching
- CARLOS often broadens matching using `LIKE` or alternate prevention type patterns
- This may be intentional compatibility work for prevention type migrations, but it is still a semantic change that requires validation

### Structural Hygiene Candidates

The CARLOS file contains duplicate `ap-name` entries for several shared tags.

These should be reviewed because the loader returns the first match:
- `appt_date`
- `current_user`
- `current_user_fname_lname`
- `current_user_ohip_no`
- `current_user_specialty_code`
- `current_user_cpsid`
- `current_user_id`
- `current_user_signature`
- `current_user_address`
- `current_user_email`
- `current_user_fax`
- `dtap_immunization_date`

Duplicates that are not byte-for-byte identical should be treated as review findings, because order determines behavior.

## Aggregate Issue Ticket Structure

Create one large issue ticket for `Blocked/Flagged` cases with these sections:

### Title

`OSCAR19 apconfig compatibility gaps blocked by removed or unproven CARLOS backends`

### Body Structure

1. Purpose
2. Comparator reference used
3. Review standard used
4. Blocked/Flagged tag inventory
5. Per-tag evidence
6. Missing backend or proof gap
7. Suggested resolution path
8. Open questions

### Per-Tag Evidence Template

For each blocked tag, include:
- OSCAR19 tag name
- OSCAR19 SQL/backend source
- Current CARLOS status
- Why equivalence cannot currently be proven
- Candidate replacement source in CARLOS, if any
- Recommended next action

## Review Execution Plan

Review each OSCAR19 tag in this order:

1. Does the tag exist in CARLOS?
2. If yes, does CARLOS preserve the same effective behavior?
3. If no, can CARLOS reproduce the same result through a different backend?
4. If not, record it as `Blocked/Flagged`.

Prioritize execution in this order:

1. Missing OSCAR19 tags
2. Shared tags with clear semantic drift
3. Shared tags with broadened backend matching
4. Duplicate-definition cleanup candidates

## Out of Scope

This review does not assume:
- every CARLOS-only tag is a problem
- every query text difference is a compatibility problem
- every OSCAR source path should be restored literally

The standard is functional equivalence, not textual identity.

## Approval State

Approved in discussion on July 7, 2026 as the working review model:
- use functional equivalence, not name-only parity
- flag unprovable equivalence cases
- consolidate all flagged cases into one large issue ticket
