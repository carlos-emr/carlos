# Patient-specific REST authorization audit

The release supports patient-specific module overrides and separate demographic/eChart record restrictions. A global role check is insufficient for a patient-specific operation. PatientApiAccess checks a positive patient identifier, the requested module/right at that patient, and isAllowedAccessToPatientRecord before accessing clinical data or changing it. Policy failures propagate; they do not become successful empty responses.

The initial source inventory at 90f57841b59ab3838cc4fe00bd8356ad3bc674f4 found 100 single-line null-context privilege calls across 76 methods in the REST tree. This is a candidate inventory, not proof that all 100 are vulnerabilities. Multiline helpers and downstream manager calls also require review.

| Family | Disposition |
| --- | --- |
| MeasurementService patient measurements | Already enforces scoped measurement read and record restrictions in the base; retain its negative and positive endpoint tests. |
| Tickler overdue count, JSON patient filter and legacy patient list filter | Enforce scoped tickler read and chart restrictions before count/list queries. Empty optional filters retain their existing global discovery meaning. |
| DiseaseRegistryService patient read/add | Enforce scoped registry read/write and chart restrictions before the row query or mutation. Catalogue/quick-list lookup remains global. |
| RecordUxService record menu and summary definitions | Require chart access and evaluate each optional capability with a readonly patient-specific check, avoiding session-lock side effects from hidden optional capabilities. |
| NotesService tickler note retrieval/save | Resolve the tickler's stored patient, require scoped tickler/eChart rights and chart access, and verify linked/revised notes belong to that patient before reading history or writing. Missing ticklers/notes are explicit; a client cannot substitute another patient in the body. |
| DemographicService, merge/unmerge preflights | Global checks remain additional boundaries; existing manager/resource patient checks and whole-batch participant validation are separately qualified by the demographic and merge suites. |
| Consent-type catalogue, app/clinic configuration, job administration, provider/team metadata, pharmacy directory, eForm definitions/images/database tags, drug-reference lookup, instruction parsing, report definitions, encounter templates and user preferences | Their global checks protect nonpatient resources. A patient identifier must not be invented for these role-scoped operations. |
| PatientDetailStatus configuration / HIN validation | getStatus returns clinic settings, and validateHC validates caller-supplied data without reading a chart. Uniqueness lookup uses DemographicManager's authorized discovery path; the supplied demographic ID is an exclusion, not a chart retrieval grant. |
| Program admission lists, bulk report results and unfiltered tickler discovery | Row and total policy still requires reconciliation; a global list permission must not be treated as proof of patient-level filtering. |
| Appointment history and billing details | Patient appointment read and chart access precede history lookup. Global and patient billing rights are readonly additional boundaries before any billing query. |
| Group-note extensions and other downstream appointment/tickler operations | Remaining source paths are tracked in the acceptance ledger; this document does not claim the broader sweep is finished. |

The focused endpoint tests preserve authorized response data and reject patient overrides, blocked charts, invalid IDs and cross-patient note revisions before the relevant clinical query/write. Captured sink checks separately protect request/file paths. Repository tests do not substitute for installed authorization-policy qualification.
