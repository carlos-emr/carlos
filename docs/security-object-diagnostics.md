# Safe object diagnostics (issue #5486)

Application object diagnostics must not expose credentials, patient fields,
session state, clinical payloads, or nested objects. Parameterized logging and
`LogSafe` prevent eager formatting and log injection respectively; neither makes
patient data appropriate to log.

`AbstractModel` reports the runtime type and an Integer/Long surrogate ID only.
Null IDs remain visible; String and composite IDs are marked `protected`, because
their contents and `toString()` implementations are not inherently safe. It does
not call `hashCode()` or traverse associations. The old stack inspection in
`hashCode()` is no longer necessary. `Security` explicitly reports only its numeric
record ID. `LoggedInInfo` reports a sanitized provider number and numeric facility
ID, including when no user is logged in; it never formats the session, provider,
facility, or security object.

The former reflective DTO and value-object diagnostics report only
`Type[protected]`, except for explicitly allowed numeric keys and FHIR configuration
enum names/boolean flags. New diagnostic fields require an explicit review of whether
they can contain patient data or credentials. Adding a field to a model no longer
automatically adds it to the logs.

## Release-branch audit

Audited against `release/2026.08` at `112feaecb7b`, using searches for reflective
builder imports/calls, model overrides, explicit `toString()` callers, log
concatenation, collection/string formatting, and JSP/scriptlet/EL uses.

- Replaced all 59 reflective `toString()` implementations: the model bases,
  Clinic, Drug, FormBPMH, OscarJobType, CtlDocumentPK; all 13 SOAP and five REST
  transfer objects; billing entities/forms/filter data; prescription data and
  five nested drug data types; EDoc; provider/encounter data; case-management
  issue display; dashboard query/display/template types; BPMH/form transport;
  FHIR builder/configuration/sender/destination data.
- Removed HRM's separate reflective dump of every returned lab record and the
  unused reflective-builder JSP import. No reflective object-method site remains
  in `src/main`.
- Reviewed every `commn/model` diagnostic override. Demographic, DemographicExt
  and Admission delegate to the safe base. FlowSheetCustomization's explicit
  patient/measurement/payload dump now does too. Existing CDS and Ocean redactions
  remain explicit. Other retained overrides print keys/type information; composite
  keys and SpecialtyPK include text used as functional keys and were not rewritten.
- Removed model/context dumps from RecordUxService, IndicatorBeanFactory,
  CodeFilterManager, RxManagerImpl and prescription discontinuation. RecordUxService
  now logs operation names without the summary object or patient/context pairing.
  Removed the related NotesService request JSON/note-body dumps and RxWebService
  drug-list dump and RxDsMessageTo1 constructor's interaction-map dump. HRM logs
  only the result count.
- Explicit affected-object `toString()` callers were diagnostics/audit text,
  including `CheckBoxBean`, prescription auditing and FHIR retrieval auditing.
  No affected reflective representation was found being parsed, compared, used
  as a map key, persisted as functional application data, or rendered as clinical
  content. REST/SOAP and dashboard/form exports use getters, serializers and
  dedicated formatting methods. `FormTransportContainer` captures HTML through
  its separate response wrapper's `toString()`, which is unchanged. Numeric/date,
  SQL-builder, JSON and XML formatting calls are unchanged.

This audit concerns object diagnostics. It does not claim that every existing
explicit clinical-field log or audit event in the application has been remediated;
the broader audit-quality backlog is tracked separately in #4172 and #5322.

## Equality without reflection

`BaseObject` now uses reference identity for unsaved instances and explicit
persistent-key equality for CaseManagementCPP, ClientImage and Issue. Its hash
is stable when an ID is assigned and never reads clinical fields or associations.
Persistent type/key extraction recognizes Hibernate proxies without initializing
their fields. The four entities that opt into this contract have final forwarding
`equals()`, `hashCode()` and `toString()` overrides: Hibernate otherwise intercepts
these methods and initializes the target before the base implementation runs.
Real attached and detached proxy tests verify symmetry, stable collection membership,
protected diagnostics and zero additional SELECTs.
Sonar rule `java:S8947` normally rejects final JPA entity methods. These twelve
Object-method overrides carry a method-level exception: they intentionally bypass
Hibernate interception and inspect only the proxy type/key. Persistent-property
accessors remain overridable. The real-proxy tests reproduce the failure without
`final` and verify the supported Hibernate contract with it.
CaseManagementNote and CaseManagementIssue retain their existing identifier-based
overrides; FieldDefValue explicitly uses its `(tableId, fieldName)` composite key
through the existing JpaId value type, excluding mutable lookup values.
This replaces accidental all-field equality, including formatter/blob/content
state, with model identity. `CtlDocumentPK` explicitly compares all three key
parts with null safety. No entity-wide reflective equals/hashCode remains.

## Regression gates

`SafeDiagnosticUnitTest` exercises populated credential/patient fields, unsaved
and arbitrary-key models, background contexts, log injection, and nested objects
that throw if formatted. `BaseObjectIdentityUnitTest` checks persistent/unsaved
identity, stable set membership, and all composite-key components.
`BaseObjectProxyIntegrationTest` exercises actual Hibernate proxies and committed
rows for all four affected entities. RecordUx and HRM tests capture DEBUG output
while checking that the requested clinical records are still returned.
`SafeDiagnosticSinksUnitTest` captures billing, indicator and note diagnostics and
prescription audit calls. It verifies that clinical values still reach returned
records and note persistence, including tickler links, without entering log payloads.

The blocking Semgrep step tests its positive/negative fixture and scans
`src/main` for reflective object methods, including qualified/static-import forms
and JSP imports, and the recursive equality opt-in `setTestRecursive`.
Its narrow lexical ban intentionally also rejects references to
these APIs in application comments: explanatory documentation belongs here.
