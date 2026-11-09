# JAXB provider compatibility

CARLOS packages Glassfish JAXB and uses CXF for JAX-WS. Metro's `jaxws-ri` POM
previously brought a second JAX-WS provider, a duplicate JAXB implementation, and
EclipseLink MOXy into the WAR. MOXy remains a **test-scoped** compatibility oracle;
it and its dependencies must not appear in `WEB-INF/lib` (#5476).

Provider discovery depended on classpath order: the issue's deployed application
selected MOXy, while a fresh package made from release commit
`112feaecb7b` selected Glassfish. Reproducing the old deployment requires explicitly
selecting `org.eclipse.persistence.jaxb.JAXBContextFactory`. The test dependency is
declared after `jaxb-runtime`, so `ServiceLoader` finds Glassfish first and the
test oracle cannot become the default provider in surefire, IDE runners or ad hoc
Maven goals; `JaxbProviderDiscoveryUnitTest` pins that order. The build does not
force the provider through the `jakarta.xml.bind.JAXBContextFactory` system
property: with it set, `ContextFinder` forwards a caller's own
`JAXBContextFactory` map entry to the provider, which rejects it, and
`RestJaxbContextUnitTest` selects providers exactly that way.

## Preserved XML and JSON contracts

`CharacterXmlAdapter` retains string-valued characters, omission of null values,
and an empty element for the NUL sentinel. Package annotations also cover primitive
characters and collection items. Map keys need an explicit adapter because JAXB's
synthetic map-entry binding does not inherit that conversion.

MOXy shapes a Calendar value from `Calendar.isSet`, not from the field values. A
calendar with no time field set (`clear()` followed by year, month and day, as in
`Demographic.getBirthDay`) is written as a date only; any other calendar is a full
date-time, and its zone offset is written only when the zone fields are set. Every
calendar produced by `DateUtils.toGregorianCalendar` goes through `setTime` and so
has all fields set: those values carry their offset (`2026-07-15T09:30:00-04:00`,
or `2026-07-15T13:30:00Z` for `useGMTTime`), and a midnight value keeps its time
(`2026-07-15T00:00:00-04:00`). Glassfish without an adapter writes exactly the same
strings for those production shapes, so the only behaviour the adapter has to add
is the set-state rule. `CalendarXmlAdapter` reads the set-state before copying the
calendar and `CalendarXmlAdapterUnitTest` pins the strings captured from the
unmodified release classes under MOXy.

The Glassfish contract in published main (`e53e6cb5551`, beta6) wrote the patient
`birthDay` property as `xs:dateTime`, including midnight and the zone offset.
This property has a scoped `CalendarDateTimeXmlAdapter` so the global MOXy
set-state rule cannot turn its declared date-time into a date-only value.
Nested patient records inherit the same correction. Both date-only and date-time
inputs remain accepted, and the frozen MOXy corpus remains an unchanged legacy
reader fixture. The serialization assertions explicitly retain main's birthday
output. The same scoped adapter now covers the declared date-time properties on
`AppointmentTransfer`, `AppointmentArchiveTransfer`, `CalendarScheduleCodePairTransfer`
and `LookupCodeValue` (#5721). These properties must remain schema-valid even for
cleared calendars. The general legacy adapter resolves an independent clone so
serialization cannot normalize the caller's lazy state.

Birthday display formatting also uses the returned calendar's own timezone. A
shared `DateFormatUtils` formatter can have cached another default timezone when
it was first initialized; that cache must not move a recorded birthday to the
previous day. The timezone regression initializes that cache before changing the
default and checks both ordinary clinic zones and the fixed UTC offset extremes.

Date and Calendar values omit trailing fractional-second zeroes. The Calendar
reader trims XML boundary whitespace before parsing, preserving
the native JAXB acceptance of whitespace-surrounded date-times; the regression
uses an appointment DTO and rejects any JAXB validation event. `AppointmentExtTo`
now pins the beta6 Glassfish sequence explicitly. `DrugTransfer` retains the
alphabetical sequence required by the published Prescription WSDL; its conflict
with the historical Glassfish sequence uses the explicit `glassfish-legacy` profile. Present
empty filter maps retain `<params/>`, and present empty appointment-duration maps
retain `<appointmentCodeDurations/>`; absent maps remain absent. Empty containers
carry no character values and therefore do not require choosing between the
numeric and literal character protocols. Anonymous map
carrier types are non-final because XML Schema forbids a `final` attribute on an
anonymous complex type.

Empty or XML-whitespace-only optional Calendar elements retain native MOXy's null
interpretation. This matters for SOAP appointment updates, where null start/end
values preserve the existing dates. The regression uses the actual appointment
binding with strict validation events, matching CXF's reader; malformed nonempty
dates are still rejected.

The frozen fixture's populated Calendar is built with `clear()` and therefore has
no zone fields set; it covers the date-only and offset-less forms but not the
`setTime` shape, which is why the production shapes are pinned separately.

Both production REST JSON mappers use `RestXmlAnnotationIntrospector`, which keeps
JAXB names and existing adapters but excludes the `XmlCompatibilityAdapter` family.
Without this restriction, Jackson converts JSON's NUL character into XML's empty
string and applies the XML date conversions to JSON. This also protects collection
items and preserves the OAuth mapper's existing smart-date behavior.

The SOAP endpoints use the published WSDLs in `src/main/resources/wsdl/published`.
These were captured from the unmodified WAR with MOXy explicitly selected. CXF
continues to rewrite service addresses for the requesting host. Keeping the
published documents also preserves differences in provider-generated schema
metadata (final-type flags, empty sequences and schema defaults), rather than
relying on a schema generator to reproduce another implementation's formatting.
An intentional SOAP contract change must update the corresponding published WSDL
and its compatibility evidence together.

## Regression evidence

`JaxbImplementationCompatibilityUnitTest` discovers annotated application classes,
SOAP/REST transfer objects, Ontario client bindings, HRM bindings, nested annotated
types, and the application classes in their Glassfish binding graphs. It exercises
constructor defaults and populated bound properties, nested beans, collections,
arrays, and dates in `America/Toronto`. Container arguments are resolved from the
concrete field or accessor, including inherited type variables, so populated lists
and maps contain typed values rather than silently remaining empty. Cycles stop at an already visited class and
nesting stops after five levels; each discovered class is also exercised as a root.
The current inventory contains 336 concrete classes. It does not instantiate SOAP
clients, service implementations, abstract types, or non-binding implementation
helpers.

The test explicitly constructs both JAXB providers and compares namespace-aware
XML trees with ordered children and unordered attributes. Frozen MOXy XML in
`src/test/resources/xml/jaxb-moxy-baseline.json` was captured from the **unmodified**
WAR built from `112feaecb7b`, using an isolated application class loader. These
fixtures prevent a new annotation from silently changing both providers together.
Non-null Date constructor defaults are fixed to a deterministic instant. Populated
strings use a synthetic numeric value where legacy accessors parse numeric strings.

The reader comparison feeds MOXy-written XML to both implementations and compares
the resulting bound state. Some legacy entity setters normalize values or expose
multiple names for the same underlying field, so their old reader's result is the
reference rather than an assumed lossless object round trip. A separate
`JaxbUnmarshalCompatibilityUnitTest` compares the complete object graphs read from
shipped `apconfig.xml`, `VTForm.xml`, the explicitly fictitious HRM report, and
representative UCR/fax sample files.

`JaxbCharacterJsonCompatibilityUnitTest` compares 100 combinations (both production
mappers, ten beans, five character scenarios) against JSON captured before the
migration. `CharacterXmlAdapterUnitTest` pins SOAP schema types and character edge
cases.

## Existing errors distinguished from compatibility regressions

The exact pre-existing MOXy context failures are recorded by root class in
`jaxb-legacy-context-errors.json`. Most lead to the immutable
`CdsTemporalNativeDateDto`; others involve `PreventionDateDTO`, immutable response
wrappers, or `ConsultationAttachment`. The `Allergy` and `BpmhForm2Bean` contexts
also fail on the incompatible `Allergy.setDemographicNo(int)` JavaBean accessor.
A failed legacy context cannot produce XML
to compare. The test requires the recorded cause and verifies Glassfish context
creation and output where the type supports it. A context rejected by both
providers remains explicitly identified; it is not silently removed from discovery.
Four DTOs have null-unboxing getters on their constructor defaults. Both providers'
exceptions are checked before testing their populated form.

`release/2026.08` later made several of those contexts constructible (#5632 added
JAXB adapters to `PreventionDateDTO` and `CdsTemporalNativeDateDto`). The recorded
failures are kept as the list of types for which no MOXy representation ever
shipped: when MOXy can now build such a context, the test still exercises
Glassfish but does not compare against live MOXy, which differs by design (it
omits an empty `@XmlElementWrapper` and cannot marshal a class-level-adapter root).
Fields the release line adds to a baselined type are added to the frozen entry
with their synthetic value (`AppointmentTo1.editVersion`, #5561), and a type the
release line makes a root element is added to the published WSDLs that reference
it (`ProviderTransfer`, #5632).

The migration permits the documented provider XML response to change from 500
under MOXy to 200 under Glassfish. Three other existing defects exposed by the
inventory are corrected:

- `ProfessionalSpecialist` attempted to pad a short address array by assigning past
  its end. Single-component, empty and form-supplied replacements now rebuild the
  component cache. Missing components are padded with empty strings; commas are
  added only when serializing the stored address, so later street/city/postal/
  province edits preserve separators without exposing them in component getters.
- `ConsultationRequestTo1` initialized JAXB live collections with immutable empty
  lists, preventing the reader from adding attachments or extras. They now start
  as mutable empty lists.
- `BpmhForm2Bean` bound its private `SimpleDateFormat` as form data. MOXy serialized
  implementation-specific formatter text; Glassfish generated invalid XML with a
  digit-leading element name. The formatting object is now XML-transient.

Static analysis also exposed a precedence error in `AppointmentExtTo.toString`;
date formatting now handles absent dates and retains the complete description.
`AppointmentTransfer.copyTo` explicitly rejects a null target with the same
`IllegalArgumentException` already enforced by `BeanUtils`, before any date writes.
Neither change alters the XML or JSON fixtures.

Live verification uses the Ubuntu 26.04 Debian installation with fictitious demo
records. Capture every endpoint declared in `spring_ws.xml` (currently **14**),
compare namespace-expanded WSDL names and attributes, and preserve child order in
schema sequences. Declaration order at the WSDL/schema top level is irrelevant.
Also compare authenticated provider and schedule-code JSON, verify provider XML's
500-to-200 correction, and inspect loaded classes to establish that only Glassfish
JAXB is loaded after removal of Metro.

## Follow-up qualification for #5720 and #5721

`PublishedXmlSchemaContractUnitTest` validates native objects against unmodified
schemas generated from the hash-verified beta6 package with Glassfish explicitly
selected. The fixtures and their provenance are in
`src/test/resources/xml/published-beta6-glassfish/`. Date-time tests cover the four
affected DTOs, summer/winter, UTC, fractional-hour offsets, strict reads and
unchanged caller state. Map and appointment checks include nested responses.

`JaxbGeneratedSchemaUnitTest` compiles each generated root context without
stripping schema attributes. Five HRM contexts still fail on the same unresolved
`ContactPurpose` declaration as beta6, and `ConsultationAttachment` still lacks
a no-arg constructor. Anonymous types without a root emit no standalone schema;
their owning contexts are checked, and an empty source list is never submitted
to JAXP as successful validation. These baseline gaps remain distinct from the
three map-context regressions.

The binary verifier accepts `--main-xml-provider glassfish|moxy|automatic` and
`--candidate-xml-provider glassfish|moxy|automatic`, records the requested and
observed implementations, and rejects a substituted provider. The original
automatic-discovery lane remains required, alongside separate explicit Glassfish
and MOXy predecessor lanes. The comparator still preserves lexical values, child
order, missing elements and API differences; no compatibility allowlist was added.
Automatic discovery on beta6 can select either provider, so its results must not
be relabeled as the historical Glassfish observation.

The release default follows the published WSDLs. Literal `0` and numeric `0`
denote different characters, so the server never guesses which input was meant.
The explicit profiles in [rest-soap-xml-profiles.md](rest-soap-xml-profiles.md)
preserve numeric characters, historical MOXy date-only birthdays and the historical
flat drug sequence. Requests and responses acknowledge the selected contract;
unknown or repeated selections fail. All fourteen published WSDLs remain unchanged.
Fractional-zero spellings, optional edit versions, temporal precision carriers and
the provider root retain separate binary-client qualification requirements.

These corrections do not close the release qualification umbrella. Reviewed
production-path ownership, the complete coverage union, installed ON/BC lifecycle
and recovery acceptance, vendor staging, supported Java extensions and the
existing rollout-policy thread remain required under #5720. `releaseReady` must
remain false while any required item is incomplete. Candidate-bound build, package,
browser, comparison and CI results belong to the associated PR; historical
receipts are never promoted to a new source revision.
