# CDS import specification crosswalk

This is a development and review artifact for the import implementation targeting `release/2026.08`. It inventories the **reachable schema-declared wire contract**, maps each field to its implemented handling, and makes the evidence and its limits inspectable. It is not OntarioMD certification, an assertion that every CDS-S logical requirement is encoded in this XSD, or a replacement for the clinical, database, recovery, authorization and installed-candidate gates.

## Inputs and provenance

The user supplied the OntarioMD CDS-S 5.2 Final and EMR Data Migration 5.2 Final bundles dated 2026-09-08, and PCON-2024-02. Their distinct ZIP hashes were:

| Bundle | SHA256 |
| --- | --- |
| CDS-S 5.2 Final | `a1be80fc623eb41c383b74fc8033787c0d04028c3da8b3670ed6d0bfec8f5ec8` |
| Data Migration 5.2 Final | `84a528cc1f894705e1c5a07a1df46f3ddf1a69eca386a6b3ef8d23b82d9b1d26` |
| PCON-2024-02 | `fc2d379002981b1527b23bb3c2964210e71f73d6f3ecc0b4e15105147d7633b3` |

The two deployed [pinned schemas](../src/main/resources/cds-import/schema/) are byte-identical to the pair in the supplied migration bundle and the PCON migration 5.1 Final pair. The schema comment itself identifies EMR Data Migration 5.0 / schema v1.0 / 2017-08-04; a later bundle name does not change these actual bytes. `CdsSchemaValidator` pins the main schema to `2bffde70ae84137624db888611e8a3b919e868789b726ee012d39513ce846726` and the datatype schema to `e999fafd945d13a7a2866482e06029cea86b902ce7f30139377a3600c3d9ce31`. The operator still selects the registered `pcon-2024-02` or `omd-5.2` logical profile explicitly; equal wire schemas do not establish equal logical-profile applicability.

The documentary review uses **EMR Data Migration Implementation Guide v1.1 Final**, **Data Dictionary v1.1**, **Requirements v1.1**, and the supplied CDS-S Business View v2.3 / Data Dictionary v2.1 / Requirements v1.1. The inventory includes schema declarations and facets, not copies of those PDFs or workbook prose. Requirement identifiers below are the published identifiers; `reviewGroup` names are local implementation classifications, not invented OntarioMD requirements.

## How to read the artifacts

- [cds-spec-crosswalk.json](cds-spec-crosswalk.json) is generated from the complete local import graph. It includes all **407 distinct leaf/attribute paths**, **45 instantiated choices / 130 arms**, and **150 sequence/all/choice instances** under `OmdCds/PatientRecord`, across 16 source categories. Eight of the 407 paths are attributes. QName prefixes are fixed for display: `cds` means namespace `cds`, `dt` means namespace `cds_dt`; an attribute without a prefix is genuinely unqualified.
- [cds-spec-review.json](cds-spec-review.json) is the separately reviewed input. Its 54 groups enumerate exact paths, native destinations or source-only dispositions, normalization, original retention, transaction/dependency policy, error policy, implementation/reader references and tests. Every choice arm separately identifies its applicable groups and evidence. There is no wildcard rule that silently classifies future source fields.
- A field's `variants` retain **all** declaration contexts, type/base/facet chains, enum/pattern/length/whitespace constraints, defaults/fixed values, nil permission, parent occurrence bounds and conditional choice membership. A phone extension appearing in both phone-number sequence alternatives therefore retains two variants instead of losing one branch. Repetition is represented as source occurrence bounds, not flattened to one clinical fact.
- `globalTypes` also records 18 datatype declarations unreachable from this envelope. For example, unused datatype attributes or a blank-indicator branch are not falsely advertised as fields allowed anywhere in a patient record. The standalone global `Reports` declaration does not change the importer's required patient envelope.
- The eight attributes are the schema-declared application attributes. XML namespace declarations and universal XML Schema instance control attributes such as `xsi:type` are not additional clinical data definitions in these counts. The accepted XML bytes retain that metadata; accepted attributes inside a source section also follow the parser's generic preservation path. External schema resolution remains denied by the pinned validator.
- [coverage.json](../src/test/resources/cds-spec/coverage.json) binds four **synthetic structural fixtures** to their SHA256, independently walked instance paths/values and exclusive choice-arm witnesses. A shared `extension` field alone cannot certify which phone sequence was exercised. All four fixtures contain deliberately synthetic values; they are parser/schema examples, not clinical import decisions.

`native-and-source` means the code has the stated native projection **when its documented conditions hold**, and retains the complete source independently. It does not mean a native row is always possible: partial appointments, unknown providers, conflicting repeated slots or an overlength narrative can require a visible source-only disposition. `source-only` means there is no claimed native clinical projection for that particular field. It remains separately addressable and visible in the corresponding category; it is not quietly concatenated into a generic Notes field or counted as a native fact.

Original accepted XML **bytes** are retained. A field's original text means XML character data or an attribute after XML's required entity/line-ending processing; a binary field contains decoded bytes. The source XML retains the original lexical representation, including Base64 spelling. Schema whitespace normalization and explicitly reviewed scalar corrections remain separate from original field content. Large values stay hash-bound offset references instead of being materialized in a clinical transaction.

## Category coverage and interpretation

| Source category | Distinct field/attribute paths | Native projection and principal evidence |
| --- | ---: | --- |
| Demographics | 98 | Demographic identity and details; scoped external relations/pharmacy; bounded Contact children. `CdsCoreProjectionUnitTest`, `CdsDemographicStorageMariaDbIntegrationTest`, `CdsIdentityMariaDbIntegrationTest`. Ordered legal GIV middle names project wholly within100Unicode code points, including joining spaces; overflow/empty values retain the full source with an explicit warning. Source name purposes/qualifiers are not arbitrary preferred-name selection rules. |
| PersonalHistory | 3 | Residual Name/DataType/Content remains category-local source data; no guessed occupation/social-history code from a vendor label. |
| FamilyHistory | 15 | Complete bounded narrative in category-linked native note; structured onset/code/relationship/history remains separate source data. |
| PastHealth | 19 | Complete bounded narrative in native medical-history note; structured source procedure/onset/resolution remains separate. |
| ProblemList | 17 | Complete bounded narrative in native concerns note; no guessed local diagnostic code or resolved-state assertion. |
| RiskFactors | 14 | Complete bounded risk narrative; separate exposure/life-stage/timing/residual data. |
| AllergiesAndAdverseReactions | 19 | Native Allergy and partial-date metadata; AL/AR and agent property remain independent. Clinical normalization/storage and temporal reader tests. |
| MedicationsAndTreatments | 45 | Native Drug and partial-date metadata; exact source dose/authority/currentness and reconciliation safeguards. Clinical normalization/storage and legacy projection tests. Description/source prescription ID and residuals are explicitly source-only. |
| Immunizations | 21 | Native Prevention/extensions/partial dates; required refusal assertion is independent of optional timing. Unknown date never becomes import date. Clinical storage and prevention precision/rule tests. |
| LaboratoryResults | 32 | Typed CdsLaboratoryResult plus native inbox projection only when safe; sensitivity and result status preserved. Source reviewer does not become local acknowledgement. Laboratory component/reader and native storage tests. |
| Appointments | 11 | Exact representable single-day native calendar slot, or explicit typed source-only occurrence; no invented day/time/duration/provider. Core projection and actual appointment storage tests. |
| ClinicalNotes | 20 | Complete bounded unsigned native body, truthful observation clock and conditional exact source-author match. Source creation/review clocks and participants remain separately visible; the native revision formula is not overwritten with source creation. Actual history storage tests. |
| Reports | 44 | Every supplied representation published/receipted; native Document precision marker prevents insert-time clinical timestamps. Source HRM/reviewer/message metadata remains source data, not local sign-off. Report media/publication/native storage tests. |
| CareElements | 33 | One bounded Measurement child plus metadata per occurrence; full dates/fixed units checked, text/unquantified data cannot silently become a numeric fact. Care/contact and actual native storage tests. |
| AlertsAndSpecialNeeds | 11 | Complete bounded category-linked reminder narrative; separate source dates/notes/residuals. |
| NewCategory | 5 | Named/described category and residual fields remain protected category-local data, without inventing a native clinical mapping. |

The exact per-path projection and source-only split is in the reviewed JSON. Target budgets and capacities must be read with the cited current model/normalizer/persister and forward migrations, not inferred from an XSD maximum alone. The review pins the actual implementation/reader source bytes; changing one of those files requires re-review before the generator will accept the crosswalk. The physical ON/BC schema contracts and native suites remain the evidence for real storage capacities, defaults, collation and transaction participation.

These are the 16 XSD section categories. The operator log has separate ReportsText/ReportsBinary classifications, so a 17-category log/fixture count is not a seventeenth XSD section. Likewise `_FIELDS`, `_CONTACT` and `_CARE` helper units are operational dependencies, not extra imported source occurrences.

See also [clinical normalization and native-write contracts](cds-clinical-import-contract.md), [prevention precision/readers](cds-prevention-date-contract.md), [legacy projection safeguards](cds-legacy-projection-contract.md), [ledger/recovery](cds-import-ledger.md) and [maintenance admission](cds-maintenance-admission.md). These documents address reader behavior and failure boundaries beyond the finite source-path inventory.

## Published importer requirements and evidence boundaries

| Published requirement | Implementation/review evidence |
| --- | --- |
| DP02.01 — provisioned import, all wire choices, discrete fields where applicable, corresponding-module access for unmapped fields | This exact-path/choice inventory; native mapping groups and source-only groups; `CdsSourceFieldService`, authorized `CdsClinicalSourceService` and source-fields views; real parser corpus and native suites. A successful parser test alone is not evidence that every native clinical mapping is correct. |
| DP02.02 — documented user steps | [Operator guide](cds-import-operator-guide.md), [web import](cds-import-web.md) and [installed CLI verification](cds-import-cli-verification.md), including maintenance, preview, cancellation, recovery, review and completeness interpretation. |
| DP02.03 — vendor documentation | The source/destination/normalization/limits/reader contracts cited above and the reviewed crosswalk, including source-only and unsupported projection cases. |
| DP02.04 — ImportEventLog | `CdsImportResultService` streams `ImportEventLog.jsonl` with known inventory, patient dispositions and category counts; source occurrences are distinct from helper unit counts. Selected durable report and release/cleanup states remain part of operational completeness. |
| DP02.05 — import error log | The same report bundle streams `ImportErrorLog.jsonl`, ledger diagnostics and precise current schema diagnostics; original values/evidence remain protected. Errors cannot be inferred absent merely because no patient was parsed. |

Guide §1.8/§1.9/§1.10 and §18.3/§18.4 guide code, timing, length, mapping and unit interpretation; individual group references identify their actual category section. Source absence, explicit negative, unknown timing, timestamp precision, author attribution and clinical authority remain different meanings. No completeness claim may bypass source-field reconciliation, native receipts, dependent files, chart holds, unresolved clinical review, report delivery, cleanup disposition or verified maintenance release.

## Regeneration and required checks

Install the already hash-pinned development dependency using the same isolated environment as native CI:

```sh
python3 -m venv /tmp/cds-spec-venv
/tmp/cds-spec-venv/bin/python -m pip install --require-hashes -r scripts/ci/requirements.txt
/tmp/cds-spec-venv/bin/python scripts/cds-spec-crosswalk.py --check
/tmp/cds-spec-venv/bin/python -m unittest discover -s scripts/ci -p test_cds_spec_crosswalk.py -v
```

For an intentional contract change, first review the schema/provenance and native/source/reader dispositions, update the explicit reviewed paths/arms and reviewed hashes, then run `scripts/cds-spec-crosswalk.py --write-fixtures` using that Python environment. Without `--write-fixtures`, generation requires the checked-in synthetic fixture bytes and coverage manifest to match. `--check` writes nothing and refuses missing/stale classifications, choice evidence, source/schema drift, invalid references and stale generated artifacts. Unknown XSD constructs/attributes, recursive types, scoped prefix rebinding, DTD/entities, nonlocal imports or namespace mismatches require an explicit generator review rather than being skipped.

The native-contract workflow runs that check and its refusal tests, then requires `CdsSpecificationCrosswalkUnitTest` against the **actual deployed pinned validator and streaming field parser**. That six-case Java gate validates all four XMLs, checks every emitted original value/decoded binary, leaf namespace, kind, schema whitespace, length/hash and source address, checks all 130 exclusive witnesses, and rejects a document with both address alternatives without a parse seal. Source parsing remains preflight-only; these tests write no clinical rows. The existing physical/native, installed web/CLI, recovery, large-input and downstream-reader gates remain separately required.
