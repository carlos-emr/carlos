# Release 2026.08 promotion validation

Review baseline: `c181142689` on `release/2026.08`, compared with `origin/main`
(alpha12), 2026-09-18. This report accompanies a separate repair PR targeting
`release/2026.08`; the promotion PR is #3762.

## Confirmed findings

| Finding | Evidence | Repair and regression coverage |
| --- | --- | --- |
| General Conversions fails to initialize and its About link does nothing | Installed-package Playwright reports `Generic is not defined`, two syntax errors, and `popupStart is not defined` | Repair script initialization and popup handler; test the actual Chart → Calculators → General Conversions → About/Licence journey |
| General Conversions has inaccurate unit factors and loses zero/small values | Nautical-mile and long-ton factors are incorrect; U.S. liquid-volume factors also disagree with unit definitions. The formatter truncates scientific notation and the calculation treats zero as missing | Use exact unit definitions, significant-digit formatting, visible invalid-input feedback, and numerical browser assertions. Legacy `.htm` bookmarks forward to the maintained calculator |
| Legacy pharmacy search loses names containing `!` | Authenticated installed-package search for an owned `FAKE-PW…!` pharmacy returns no match | Restore the existing wildcard-search contract; retain the separate fax picker's literal escaping. New DAO integration and authenticated Playwright endpoint regressions |
| Report validation depends on administration navigation order | Full admin audit opens 102 items; Visit Report and Overnight Batch produce six JavaScript error signals. eForm fragments reload jQuery and discard the shell's validation plugins | Restore the shell's jQuery instance before the next fragment; new browser workflow visits eForms before both reports and checks invalid submission is blocked |
| Migration verification tools can echo a password prefix | An attached argument such as `-pSECRET=tail` is split at `=` before being printed in the refusal | Return only constant option names from both argument checks; regression cases cover attached passwords containing `=` and long/short options |

Conversion definitions: [NIST SP 811 Appendix B.8](https://www.nist.gov/pml/special-publication-811/nist-guide-si-appendix-b-conversion-factors/nist-guide-si-appendix-b8).

## Baseline verification

- Full Java `clean package`: 12,411 tests, zero failures/errors, 51 skips.
- Python: 1,585 tests passed. Node: 678 tests passed.
- Built main, DrugRef, and eForm-renderer DEBs, version
  `2026.08.0~alpha13~validation1`. DrugRef uses the pinned source revision;
  Chromium archive checksums were verified before packaging.
- Upgraded the existing alpha12 Ubuntu 26.04 VM using all three DEBs.
  Preserved clinical record counts, administrator credential records, and the
  initial credential file. Flyway validates; no incomplete-install marker.
- Installed `carlos-ctl check` passed all deployment checks, including HTTPS,
  WAF blocking, DrugRef lookup, renderer service, database, and service ownership.
- New ICHPPC browser lifecycle passed search, add, reopen, resolve, cancel delete,
  and confirmed archival deletion. The test owns and removes its synthetic patient.
- Full browser baseline began through nginx HTTPS. The admin failures above and
  pharmacy-search failure are confirmed against the built package. A first About
  test had an incorrect menu selector; that test error was corrected before the
  application errors were recorded.

## Resource controls and remaining validation

Java builds run only while the VM is stopped, with a 6 GiB build cgroup, no
cgroup swap, one test fork, and a host guard reserving at least 10 GiB available
memory. Baseline Java build minimum host availability was 16.02 GiB.

The initial 6 GiB VM browser run reached its guest reserve; the guard paused the
suite and terminated the browser. This is an incomplete suite run, not a pass.
The VM was stopped and configured for 8 GiB for the next pass. Host and guest
memory monitors remain active. No compilation overlaps the running VM.

Rebuilt-package validation, the complete browser result inventory, and final
repair-test results will be added after that pass. Tests requiring additional
fixtures or external services must be recorded as unverified/skipped rather
than counted as passes.

## Promotion checks requiring separate assessment

Promotion #3762 reports legacy lab AES encryption findings. Switching the
algorithm unilaterally would break the external sender's protocol; this repair
must not silently change that contract. The source also documents the external
RSA padding constraint. These findings are not evidence that the release
introduced the encryption code.

The CodeQL `util.py` alert traces to manifest-selected billing table identifiers,
not billing row payloads; it requires classification separately from the real
password-refusal leak fixed here. Path-containment and hash-comparison scanner
findings are under review; no global scanner suppression has been added.

Promotion DCO flags five historical commits without trailers and its reporting
step also receives HTTP 403. New repair commits carry DCO sign-off. This report
makes no assertion that the promotion's existing DCO/security gates are green.
