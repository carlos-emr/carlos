# Provenance of vendored OSCAR 19 fixtures

Vendored verbatim from the upstream OSCAR EMR repository for reproducible
migration rehearsals (no Bitbucket checkout needed to run the fixture flow):

- Source repository: `https://bitbucket.org/oscaremr/oscar.git`
- Branch/commit: `master` @ `a7900d569d3faf741993e5e1da8c14021bbefede`
  (2020-03-09 — the OSCAR 19 line; release tag `OSCAR_19_RC1`)

| File here | Upstream path | License |
|---|---|---|
| `demo-data/demo.sql` | `release/demo.sql` | GPL v2 (header preserved: "Copyright Peter Hutten-Czapski 2012 released under the GPL v2"). Upstream's synthetic demonstration dataset: invented patients, providers and encounters for training installs — no real person or clinic. |
| `properties/oscar_mcmaster.properties` | `src/main/resources/oscar_mcmaster.properties` | GPL (upstream project license; file header preserved). Byte-identical except that every secret value (passwords, API keys, conformance keys) reads `<redacted-in-fixture>`; upstream's placeholder account names (`db_username=root`, `OMD_HRM_USER=mcmu`, …) are stock defaults, not credentials, and are kept as shipped. |

Everything else under `fixtures/` is synthetic CARLOS-authored fixture
content (clearly-fake values only — no real clinic or patient data):

- `properties/oscar-clinic-example.properties` — a fabricated
  "clinic-modified" O19 properties file driving the props-phase tests.
- `demo-data/roles.sql` — synthetic role/privilege and legacy-data cases for
  the roles post-step rehearsal (a clinic-custom role, two NULL `activeyn`
  assignments (a `doctor` row the import activates and a dormant `admin`
  row it leaves), an `indicatorTemplate` row whose template carries an
  embedded line break, an expired login, a document-queue object, a patient-scoped
  lockout, a clinic override of a stock grant, a grant on an object CARLOS
  no longer checks, legacy prevention codes `Flu` and `dTaP` next to the
  valid `DTaP`, a removed-module property key). Fake providers
  `999901`–`999903`, fake logins `fixture.*` with the upstream O19 seed
  clinician's legacy password hash (also quoted in `docs/Password_System.md`;
  not a secret). The rows this file adds carry fixed timestamps; the
  fixture dump itself is not byte-reproducible (the upstream seed writes
  NOW() values and mysqldump stamps its completion time), nor are the
  bundles that embed it — only the documents tar is.
- `documents/` — manifest + generator for a deterministic placeholder
  OscarDocument tree (no binaries committed), plus the matching fixture
  database rows.

`demo-data/demo.sql` and `properties/oscar_mcmaster.properties` carry no
CARLOS header on purpose: they are upstream files, kept as close to verbatim
as the redaction above allows, so that re-vendoring is a copy rather than a
merge. Do not edit the vendored files: re-vendor from upstream and update the
commit hash above instead.
