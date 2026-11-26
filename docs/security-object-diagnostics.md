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
- Persisted audit text is an operational record, so the demographic-extension,
  consent-type, appointment and disease-registry write paths use explicit identifiers
  rather than diagnostic `toString()` output. Extension events retain the saved row id
  and sanitized field key, never the field value; new extensions are audited after
  saving. Consent-type creation records the assigned id after persistence succeeds.
  Appointment creation records appointment, demographic and provider identifiers;
  disease-registry creation records the entry id and sanitized coding-system name,
  without the diagnosis code. Historical rows that lost these fields are unchanged.
- Prescription events already retain their content/demographic columns, and FHIR
  retrieval events retain explicit lookup identifiers or persisted demographic ids;
  removing their reflective clinical details remains intentional. This change does
  not restore those field dumps. No affected representation is parsed, compared,
  used as a map key or rendered as clinical content. REST/SOAP and dashboard/form
  exports use getters, serializers and dedicated formatting methods.
  `FormTransportContainer` captures HTML through its separate response wrapper's
  `toString()`, which is unchanged. Numeric/date, SQL-builder, JSON and XML formatting
  calls are unchanged.

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

## AbstractModel equality and hash (issue #5303)

`AbstractModel` is the base of about 390 entity classes, so its contract is deliberately
small:

- `equals` never throws. An instance equals itself; `null`, another persistent type
  and a different identifier are never equal; an instance with no identifier yet
  equals only itself. Equality is symmetric because both sides apply the same rule.
- `hashCode` is the hash of the persistent type, not of the identifier. A new entity
  can therefore be added to a `HashSet`/`HashMap` (typically a cascading collection)
  and still be found after the database assigns its ID. The old identifier hash moved
  it to another bucket on save. Equal entities always share a type, so the hash stays
  consistent with `equals`. The cost is that same-type keys share one bucket, which is
  acceptable for the small child collections this base class serves; an entity that is
  held in large hashed collections overrides both methods with a business key
  (`Demographic`, `Provider`, `Program`, `Site` and `Drug` already do).
- The argument of `equals` may be a Hibernate proxy: its persistent class and ID come
  from the proxy's metadata, so an uninitialized or detached proxy is never loaded and
  never raises `LazyInitializationException`. A proxy *receiver* is initialized by
  Hibernate before `equals`/`hashCode` run, exactly as for any entity that overrides
  them; this base class does not use the `final` forwarding methods that `BaseObject`
  uses, because 28 subclasses override equality or hashing themselves.
- A subclass that overrides one of the two methods must override the other. The audit
  for #5303 found four that did not: `ProviderData` and `DSGuidelineProviderMapping`
  (equality without a hash) and `CdsClientForm` and `GroupNoteLink` (hash without equality).
  All four stay consistent under the type hash because their equality never relates
  distinct types. `DSGuidelineProviderMapping.equals` also used to cast and dereference
  its argument and now answers `false` for `null`, foreign types and missing keys.

`AbstractModelEqualityUnitTest` pins the contract on synthetic entities;
`AbstractModelProxyIntegrationTest` runs it through a real Hibernate session factory with
managed, detached and attached/detached uninitialized proxies, including zero extra SQL
when a proxy is the argument. The earlier `release/2026.08` (`9004536d`) observation of
`equals(null)` and unsaved-receiver failures was a source-level review finding; the fix
for it shipped in #6326 and is covered by the same unit test.

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
