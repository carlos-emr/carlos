# Retired BCAR 2020 save refusal (#5836)

The deprecated `FrmBCAR2020Record.saveFormRecord(Properties)` logged every submitted
field at INFO and returned the posted form ID without persisting anything. The
actor overload delegated to it. The generic form action then acknowledged or
redirected the request and wrote a success audit; Pregnancy's `saveFormAjax`
returned the posted ID and wrote an ADD audit.

The exact reproduced class is also present on release `99ec72b519`. Eighteen
regressions fail against the release implementation, including both real action
callers, both record overloads, and captured synthetic clinical-field logging.
An earlier fixture attempt registered a second audit mock and is excluded from
that reproduction; the canonical reproduction uses the base fixture's audit mock.

The retired method now throws its declared `SQLException` before inspecting the
submitted properties. Its message is static and directs callers to reopen the
supported BC Antenatal Record. Existing action error handling prevents both false
success acknowledgements and success audits. The supported `FrmBCAR20202Action`
and transactional save service retain their existing behavior.

Validation on Java 25:

- 166 unique unit cases pass with no failures, errors, or skips: retired-save and
  privacy controls, generic form/Pregnancy actions, BCAR ownership, prefill,
  field classification, page routes, and supported-save controls.
- All 15 existing supported-save transaction integration cases pass against the
  isolated H2/Spring/JPA test context, including real commits and rollbacks.
- Measured release changed-line coverage is 100% for lines, instructions, and
  branches, raising the prior 0% line/instruction floors with source-bound evidence.

Unit report SHA-256:
`314e20d618e3ec95626fc1f203fce9a0177a8e5e84b35f27bfc1035271134242`.
Transaction report SHA-256:
`3300de8aebbebcfce3a888eab6acfb6c9477f807a5ebc4f1eed3683315911160`.

This fix is not part of the earlier installed `b4048148` build. Qualification of
the next combined package and its browser checks is tracked separately.

The Sonar follow-up separates the two checked-exception overload calls into
individual callable lambdas and avoids Java's restricted `record` identifier in
local test variables. All 166 related cases pass again without failures, errors,
or skips; report SHA-256:
`8b77af8fe424124b9ed6c2bd399da3457c5e0d8d6f2c85cf9397e0e106607963`.
Fresh source-bound changed-line coverage remains 100/100/100 percent. This
follow-up changes test structure and documentation only; production code is
identical to the frozen combined package source.
