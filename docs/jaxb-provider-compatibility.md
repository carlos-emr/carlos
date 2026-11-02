# JAXB provider compatibility

CARLOS packages Glassfish JAXB and uses CXF for JAX-WS. Metro's `jaxws-ri` POM
previously brought a second JAX-WS provider, a duplicate JAXB implementation, and
EclipseLink MOXy into the WAR. MOXy remains a **test-scoped** compatibility oracle;
it and its dependencies must not appear in `WEB-INF/lib` (#5476).

Provider discovery depended on classpath order: the issue's deployed application
selected MOXy, while a fresh package made from release commit
`112feaecb7b` selected Glassfish. Reproducing the old deployment requires explicitly
selecting `org.eclipse.persistence.jaxb.JAXBContextFactory`. Ordinary Maven tests
explicitly select Glassfish so the test oracle cannot become the default provider.

## Preserved XML and JSON contracts

`CharacterXmlAdapter` retains string-valued characters, omission of null values,
and an empty element for the NUL sentinel. Package annotations also cover primitive
characters and collection items. Map keys need an explicit adapter because JAXB's
synthetic map-entry binding does not inherit that conversion.

MOXy's Calendar output omits timezone offsets and uses a date-only value at local
midnight. Its Date and Calendar values omit trailing fractional-second zeroes.
The date adapters retain those lexical forms; schema annotations retain the
existing `xs:dateTime` types. Two beans with lower-case accessor names also need
explicit alphabetical XML ordering. Empty filter maps remain omitted.

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
The current inventory contains 334 concrete classes. It does not instantiate SOAP
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
wrappers, or `ConsultationAttachment`. A failed legacy context cannot produce XML
to compare. The test requires the recorded cause and verifies Glassfish context
creation and output where the type supports it. A context rejected by both
providers remains explicitly identified; it is not silently removed from discovery.
Four DTOs have null-unboxing getters on their constructor defaults. Both providers'
exceptions are checked before testing their populated form.

The migration permits the documented provider XML response to change from 500
under MOXy to 200 under Glassfish. Three other existing defects exposed by the
inventory are corrected:

- `ProfessionalSpecialist` attempted to pad a short address array by assigning past
  its end. Actual array expansion now permits those XML setter paths to complete
  and retains comma separators when later city/postal/province edits rebuild it.
- `ConsultationRequestTo1` initialized JAXB live collections with immutable empty
  lists, preventing the reader from adding attachments or extras. They now start
  as mutable empty lists.
- `BpmhForm2Bean` bound its private `SimpleDateFormat` as form data. MOXy serialized
  implementation-specific formatter text; Glassfish generated invalid XML with a
  digit-leading element name. The formatting object is now XML-transient.

Live verification uses the Ubuntu 26.04 Debian installation with fictitious demo
records. Capture every endpoint declared in `spring_ws.xml` (currently **14**),
compare namespace-expanded WSDL names and attributes, and preserve child order in
schema sequences. Declaration order at the WSDL/schema top level is irrelevant.
Also compare authenticated provider and schedule-code JSON, verify provider XML's
500-to-200 correction, and inspect loaded classes to establish that only Glassfish
JAXB is loaded after removal of Metro.
