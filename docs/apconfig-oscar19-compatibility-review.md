# CARLOS vs OSCAR19 apconfig Compatibility Review

## Comparator

- OSCAR baseline: `src/test/resources/oscar/eform/oscar19-apconfig.xml`
- CARLOS runtime file: `src/main/resources/oscar/eform/apconfig.xml`
- Review date: 2026-07-07

## Review Standard

Shared `ap-name` tags are treated as behavior contracts. CARLOS-only additions are acceptable, but shared tags should remain functionally equivalent to OSCAR19 unless an intentional replacement path is proven.

## Restored In This Pass

These shared tags were brought back to OSCAR19-compatible behavior and covered by `ApconfigOscar19CompatibilityTest`:

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
- `address`
- `addressline`
- `province`

Restored semantics:

- `_eform_values_*` again use `demographic_no like '${eform_demographic}'`, preserving OSCAR19 wildcard scope for patient-independent eforms.
- `address`, `addressline`, and `province` again return the OSCAR19 two-letter province contract.

## Verified Equivalent Or Acceptable

- CARLOS has 18 additional `ap-name` values that do not exist in OSCAR19. These are acceptable as CARLOS-only extensions because they do not replace OSCAR19 names.
- `hba1_lastvalue` and `hiv_lastvalue` differ only by SQL whitespace normalization and are functionally equivalent.
- Several duplicate shared tags remain in CARLOS, but the reviewed duplicates are either byte-identical or operationally equivalent under the current first-match loader behavior.

## Structural Review Notes

Duplicate-count mismatches remain for these shared tags and should be treated as cleanup candidates because `EFormLoader.getAP()` returns the first matching entry:

- `appt_date`
- `current_user`
- `current_user_cpsid`
- `current_user_fax`
- `current_user_fname_lname`
- `current_user_id`
- `current_user_ohip_no`
- `current_user_signature`
- `current_user_specialty_code`
- `current_user_address`
- `current_user_email`

Current assessment:

- `appt_date` and most `current_user*` duplicates are byte-identical duplicates.
- `current_user_address` and `current_user_email` use different SQL text across duplicates, but the difference is table qualification only and is functionally equivalent.
- `current_user_specialty_code` has an extra duplicate entry in CARLOS and should be reviewed for long-term cleanup even though the SQL text matches.

## Flagged For Follow-Up

These cases remain open and are consolidated in `docs/issues/2026-07-07-apconfig-oscar19-blocked-compatibility.md`:

- Missing OSCAR19 tags with no proven CARLOS replacement: `onEDB`, `onGTPAL`
- Shared immunization tags with broadened CARLOS lookup semantics: 53 tags

## Summary

Current status after this pass:

- Fixed and verified: 13 shared tags
- Blocked or unproven: 55 shared tags
- Structural cleanup candidates: 11 shared tag names with duplicate-count drift
