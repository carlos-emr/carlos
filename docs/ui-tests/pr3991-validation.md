# PR #3991 validation

Issues: #3953 (HL7 line breaks), #4013 (pre-existing duplicate TRUENORTH narrative).
Validated on 2026-09-27 against release/2026.08 commit
`25a867f07b9bda663aaa70aab8e5de225605f999`.

## Review and changes

The shared handler marker contract remains unchanged for PDF/export consumers.
The encoder converts recognized break markers to newlines before OWASP content
encoding and emits only constant `<br/>` markup. Other markup, attributes and
already encoded markers remain escaped; null values remain empty.
Both full and AJAX views now cover Ontario results with populated OBX sub-IDs.
TRUENORTH FT results already exposed as OBR comments are no longer printed twice;
the full view's always-true exclusion and the preview's missing exclusion are fixed.

The deployed check creates its own synthetic patient, verifies ownership before
cleanup, attempts remaining lab cleanup after a failure and reports all cleanup
failures. It uses the shared validated screenshot helper and requires PDF text
extraction. Browserless regressions cover cleanup failures and changed ownership.
The unrelated Docker helper added by the original PR was removed, including its
readiness, ambiguous package-selection and fixed-password network exposure defects.
The existing VM validation instructions remain intact.

## Local tests and package validation

- Java 25 full unit/integration suite: 13,367 tests reported, zero failures/errors,
  51 skips. Focused lab/encoder/tag suite: 91 tests, zero failures/skips.
- Final encoder check after the BDD method rename: 47 tests, zero failures/skips.
- Full Node script suite: 1,041 passed, zero failures/skips.
- JSP compilation: 982 pages; WAR packaging and Javadoc generation succeeded.
- All three DEBs built as `2026.08.0~alpha16~pr3991.1`, with the VM stopped and
  a single packaging worker. All 6,667 checked class/web payloads matched both
  the package and the installed deployment. Package build identity was checked.
- Installation into the existing Ubuntu 26.04 `carlos-val` VM succeeded. Package
  health checks passed, including HTTPS, WAF blocking, database and DrugRef.

## Installed browser tests

`lab-line-break-rendering` passed with strict browser error checks:

- PATHL7 full view and preview: result/reference-range/NTE line breaks render;
  hostile script/image markup remains literal and inert.
- ExcellerisON full view and preview: the same checks plus an explicit assertion
  that populated OBX-4 takes the composite sub-ID branch.
- Both formats' downloaded PDFs: result and NTE lines remain separate, with no
  literal break marker.
- TRUENORTH full view and preview: one occurrence of the FT narrative, with its
  line break retained.
- All owned fixture lab/routing/info rows and the synthetic patient were removed;
  cleanup verifies zero remaining rows.

Existing neighboring checks: `lab-pdf-footer`, `lab-requisition-links`,
`lab-acknowledge` (10 workflow assertions), `lab-macro-tickler`, and
`demographic-gates`: five passed, zero failures/skips.

No test-specific application configuration was needed. Compilation and VM browser
work ran serially; the VM was stopped after validation. Logs and package hashes
are retained under `~/work/pr3985-4000-evidence/` on the development machine.

## SHA-256 package hashes

- `carlos-emr-drugref_2026.08.0~alpha16~pr3991.1_all.deb`: `ee525b691b58686a71457088accb8cac4f27b614872b70a136d6b7659d4e0752`
- `carlos-emr-eform-renderer_2026.08.0~alpha16~pr3991.1_all.deb`: `2d98f7366fbd3a1f17ee34de2c6e30f834c86eb0e13e117c7d1cc533c5ff3085`
- `carlos-emr_2026.08.0~alpha16~pr3991.1_amd64.deb`: `826960b8fa3a3078ce3b6ae821bbf91372761b5b7c4003cc75a8b63041568ed5`
