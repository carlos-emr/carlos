# CDS uses JDK XML parsing and validation

CARLOS's streaming CDS importer uses supported JDK DOM, SAX and JAXP schema APIs.
No standalone Xerces JAR or repackaged Xerces artifact is required. This addresses
[#5464](https://github.com/carlos-emr/carlos/issues/5464).

## Fixing the CDS implementation

The CDS format does not require Xerces. The previous importer used its `XSModel`,
grammar-pool and simple-type facet interfaces to classify values before validation.
That coupling also brought service registrations that could replace application
XML providers. Standard JAXP exposes validation and type information, but not the
compiled grammar/facet model that implementation used.

`CdsSchemaModel` now reads classification metadata from the **same hash-pinned
local XSD bytes** used by the JDK validator. It records element/attribute types,
namespace-qualified names, inherited whitespace/facet categories and identity
semantics. It handles the constructs present in the pinned CDS schemas and fails
closed on unsupported constructs. It is not an alternative XSD validator: the JDK
still checks structure, derivation, lexical values, identity constraints, fixed
values and all schema restrictions.

`CdsSchemaTypeCursor` walks this immutable model with state bounded by XML depth.
The streaming parser preserves original source values while sending the validator
bounded witnesses only for types whose metadata proves that treatment safe.
Patterns/enumerations and intrinsic string-derived types retain their real lexical
validation. Existing limits for values, XML names, diagnostics, identity state,
attachments and disk use remain enforced. Invalid placements and `xsi:type`
overrides remain subject to the JDK validator; classification also stays bounded
while that invalid input is processed.

The JDK's PSVI augmentation is disabled to avoid accumulating ancestor error
contexts. Tests independently enable PSVI and compare the JDK validator's type
information with the owned metadata across the clinical fixtures and invalid-input
contexts. No JDK internal classes, reflection into `java.xml`, or module exports
are required.

## Security and dependency boundaries

CDS explicitly selects `SAXParserFactory.newDefaultInstance()` and
`SchemaFactory.newDefaultInstance()`. DTDs and external entities are disabled;
external DTD/schema access is denied. The schema resolver accepts only the expected
local, hash-verified datatype schema. Metadata DOM construction also rejects DTDs
and external resources. Runtime identity already hashes the JDK module image,
covering the XML implementation along with the existing runtime compatibility
checks for prepared imports.

Maven Enforcer rejects `xerces:xercesImpl`, the former CARLOS `xercesImpl-cds`
artifact, `xerces:xmlParserAPIs` and `xml-apis:xml-apis`, including transitive paths.
Classpath regressions check that standalone Xerces classes are absent and ordinary
DOM/SAX/schema/datatype/DOM Load-Save discovery uses JDK implementations. Other
intentional XML providers, including Woodstox StAX and Saxon XSLT, retain their own
roles. Keep the supported JDK patched; default provider selection alone does not
secure arbitrary parser configurations.

## Verification

Run the provider and metadata regressions alongside the CDS parser, specification
crosswalk, correction, identity, diagnostics and large-input suites. The large-input
tier uses fresh 64 MiB JVMs with source/payload sizes larger than heap. Verify the
real packaged WAR classpath, native CDS contracts and installed scaling/20 GiB,
ZIP64 and disk-failure profiles before release. Clinical fields, names, descriptor
types, whitespace rules and value hashes must remain consistent.

## Research sources

- [OpenJDK DocumentBuilderFactory](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/java.xml/share/classes/javax/xml/parsers/DocumentBuilderFactory.java):
  provider lookup versus explicit built-in factory selection.
- [JAXP TypeInfoProvider](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/java.xml/share/classes/javax/xml/validation/TypeInfoProvider.java):
  supported validator type information; it does not expose an XSModel/facet tree.
- [Maven Central Xerces metadata](https://repo.maven.apache.org/maven2/xerces/xercesImpl/maven-metadata.xml):
  standalone 2.12.2 remains the latest published version, dated January 2022.
  Release age alone is not evidence of a new vulnerability.
