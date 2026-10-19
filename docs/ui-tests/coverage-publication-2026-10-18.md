# Workflow coverage publication to release/2026.08

The contribution retains all **83 additional registered checks**, including
passing workflows, and the stronger assertions in existing checks. It integrates
`release/2026.08` at `c1ccd7ca42879cb33c2f8b8f21c56a2fa928c69b`; the combined
manifest contains **786 entries**. Application fixes are inherited from that
release base. The contribution itself changes tests, their registrations and
coverage documentation.

The [original validation report](ui-path-coverage-2026-10-18.md) records the
Debian packages built from `a7611d07b4ae9b237b4a5d252fa938e09208eb85` and installed
on Ubuntu 26.04.1. Its 94 Rx results (79 passes and 15 exact known failures),
server-log findings and fixture cleanup are historical evidence for that pinned
source. They are not a claim that the publication head has the same failures or
that every registered check has run. The renderer omission and external-service
limits still apply to those package results.

## Release fixes included before publication

The release advanced while the coverage contribution was being prepared:

- #5023 and #5027 address favourite directions/PRN, patient-registry indication
  selection, Hide CPP failure feedback, optional pickup clearing, invalid written
  dates and inline doses, empty timeline selection, correction after preview and
  handling of empty DrugRef replies.
- #4800 fixes noon pickup parsing; #4862 fixes date-only pickup display on both
  the static script and medication record; #4959 rejects the selectable no-match
  placeholder; #4932 supplies Manage Letters labels.
- #5024 preserves the overdue consultation's patient and suggested tickler text.

The affected assertions remain in the suite, with their `expectedFailure` pins
removed. Historical failing evidence remains in the register; a separate note
identifies each merged fix. Current behavior must pass the actual assertions.
The unresolved RH, task-list, waiting-list, episode and duplicate patient-set
findings retain exact failure classifications.

## Independent checks retained alongside upstream additions

The release added focused checks with four names this contribution had also
chosen. Both scripts and their assertions are retained. The upstream checks keep
their names; the contribution's broader or independent variant uses:

| Original contribution handle | Published contribution handle |
|---|---|
| `consultation-overdue-tickler` | `consultation-overdue-tickler-handoff` |
| `rx-indication-patient-registry` | `rx-indication-registry-roundtrip` |
| `rx-cpp-visibility-save-refusal` | `rx-cpp-visibility-refused-state` |
| `rx-timeline-clear-selection` | `rx-timeline-empty-selection` |

The release's `rx-preview-return-edit` checks X and Escape together. The
contribution additionally retains `rx-preview-close-return-edit` and
`rx-preview-escape-return-edit` as separately selectable cases in that same
script. Each checks the original row's archival, the corrected row, exactly one
active medication and an untouched control medication.

## Finding ID crosswalk

The release independently added findings 284–286. To preserve its consecutive
register, the contribution's original **284–308 become 287–311**. Add three to
an original campaign finding number to locate its current register entry. Older
findings 82, 99, 139 and 144 keep their numbers.

| Original findings in tickets/logs | Current register findings | Issue |
|---|---|---|
| 284, 285, 287, 288, 290 | 287, 288, 290, 291, 293 | #4992 |
| 286, 289, 293 | 289, 292, 296 | #4993 |
| 291 | 294 | #4994 |
| 292, 295 | 295, 298 | #4996 |
| 294 | 297 | #4929 |
| 296 | 299 | #4997 |
| 297 | 300 | #4999 |
| 298 | 301 | #5000 |
| 299 | 302 | #5001 |
| 300 | 303 | #5002 |
| 301, 302 | 304, 305 | #5003 |
| 303 | 306 | #5004 |
| 304 | 307 | #5005 |
| 305 | 308 | #4799 |
| 306 | 309 | #4861 |
| 307 | 310 | #4915 |
| 308 | 311 | #5006 |

Ticket bodies and archived output retain their original numbers as evidence.
The [dated tracking audit](defect-tracking-2026-10-18.md) records the issue/PR
states when tickets were filed. The current [application register](app-findings-log.md)
records the release fixes separately. Original validation commit hashes in the
reports predate DCO sign-off; sign-off changes commit messages, not their trees.

## Post-merge Rx validation follow-up

Fresh CARLOS and matching DrugRef development debs from merged source
`4c33e615976d5c4cc63a4079d2a8930fb92c5f62` were installed on Ubuntu 26.04.1.
The browser selection exposed test synchronization races in favourites navigation
and Hide CPP refusal completion; follow-up #5029 retains the business assertions
while waiting for the actual controls, alert, rollback and profile refresh.

The independent journal audit also showed that finding **311 remains open** under
#5006. Its successful Info fallback and CARLOS empty-reply handling do not fix the
pinned DrugRef `NoResultException`. The Info workflow now explicitly skips as a
whole when no readable log source is configured, and keeps its exact known-failure
pin when the source is configured. A regression test prevents a missing log source
from reporting a pass.

Two additional real-UI task checks hold quantity/directions calculations while
another Rx window discards their card. Both expose finding **312**, grouped in
#5036. They assert unchanged clinical rows and an absent draft separately from the
server-error assertion. These two passing controls and the exact defect
reproductions are retained. No server-log baseline is relaxed.

The original issue audit remains a dated record. The post-publication total adds
one newly tracked finding (312) and one grouped ticket (#5036); the original
missing-DIN ticket is reused. The primary contribution's 83 registrations remain
intact, alongside these two additions and the latest release's independent tests.
