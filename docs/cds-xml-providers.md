# CDS and XML provider selection

CARLOS uses the JDK's XML parsers for ordinary JAXP discovery while retaining
Apache Xerces-J 2.12.2 for explicit streaming CDS validation. This addresses
[#5464](https://github.com/carlos-emr/carlos/issues/5464).

## Why retain Xerces for CDS?

The current CDS importer directly uses `XSModel`, compiled grammar pools and
simple-type facets in `CdsSchemaValidator`, `CdsSchemaTypeCursor` and
`CdsXmlParser`. Those APIs let it validate large clinical values without retaining
an unbounded value or PSVI error history in memory. Standard JAXP exposes schema
validation and `TypeInfoProvider`, but no equivalent grammar/facet model. Replacing
this with JDK internal `com.sun.*` APIs would introduce unsupported module access
and a dependence on JDK implementation details. Complete removal requires a
separate schema-model redesign and equivalent validation/resource-limit evidence.

The JDK parser is itself Xerces-derived and receives changes with JDK updates.
Use supported `javax.xml` APIs; keep the JDK current and preserve explicit DTD,
entity and external-schema restrictions. Switching providers alone does not make
an arbitrary parser configuration secure. CDS still explicitly constructs its
pinned Xerces parser and validator with its existing restrictions and local,
hash-pinned schema resolver. Runtime identity continues to hash the actual CDS
artifact, so existing prepared imports require the normal runtime compatibility
checks after an upgrade.

## What changed?

`io.github.carlos-emr.thirdparty:xercesImpl-cds:2.12.2-carlos1` is a reproducible
repack of the Maven Central Xerces 2.12.2 JAR. It removes these seven registrations:

- `javax.xml.datatype.DatatypeFactory`
- `javax.xml.parsers.DocumentBuilderFactory`
- `javax.xml.parsers.SAXParserFactory`
- `javax.xml.stream.XMLEventFactory`
- `javax.xml.validation.SchemaFactory`
- `org.w3c.dom.DOMImplementationSourceList`
- `org.xml.sax.driver`

All upstream file contents, including classes, manifest, LICENSE and NOTICE,
remain byte-identical. The added `META-INF/CARLOS-MODIFICATIONS.txt` identifies
this change. Directory entries are omitted and ZIP metadata is normalized;
uncompressed entries avoid zlib-version-dependent output. This is provider
isolation, not a Xerces security patch or a classloader sandbox: callers can
still explicitly construct upstream classes. Deliberate system-property/provider
configuration can still select another implementation.

The POM has no `xml-apis` dependency because `java.xml` supplies the standard APIs.
Maven Enforcer rejects upstream `xerces:xercesImpl`, `xerces:xmlParserAPIs` and
`xml-apis:xml-apis`, including transitive reintroduction. Other XML technologies
and providers (for example Saxon XSLT or Woodstox StAX) keep their own discovery.

## Rebuild and verify

From the repository root, using Python 3 (standard library only):

```sh
mkdir -p work/xerces
curl --fail --location --output work/xerces/xercesImpl-2.12.2.jar \
  https://repo.maven.apache.org/maven2/xerces/xercesImpl/2.12.2/xercesImpl-2.12.2.jar
python3 scripts/build-cds-xerces.py work/xerces/xercesImpl-2.12.2.jar \
  work/xerces/xercesImpl-cds-2.12.2-carlos1.jar
cmp work/xerces/xercesImpl-cds-2.12.2-carlos1.jar \
  local_repo/io/github/carlos-emr/thirdparty/xercesImpl-cds/2.12.2-carlos1/xercesImpl-cds-2.12.2-carlos1.jar
```

The script requires upstream SHA-256
`6fc991829af1708d15aea50c66f0beadcd2cfeb6968e0b2f55c1b0909883fe16`
and exactly the expected registrations before writing output. Output SHA-256:
`adfc454209db6e3cb274eb8707e422eedca3ed966bf8e60a882237e188102447`.
The Maven dependency lock independently checks the repacked artifact's SHA-512.
Rebuild verification must pass before committing an artifact update; after a
version change regenerate the lock with `make lock`.

`XercesProviderIsolationUnitTest` verifies ordinary factory discovery, DOM Load/Save,
absence of registrations/duplicate Xerces implementations, and explicit API use
on the application test classpath. Run it alongside `XmlUtilsUnitTest`, the CDS
parser/schema/security/large-input suites, and legacy XMLBeans compatibility tests.
Verify the packaged WAR and CLI classpath as well: checking only `XmlUtils` would
miss consumers that call JAXP discovery directly.

## Research sources

- [Maven Central metadata](https://repo.maven.apache.org/maven2/xerces/xercesImpl/maven-metadata.xml):
  2.12.2 remains the latest published version, last updated January 27, 2022.
  Release age alone is not evidence of a new vulnerability.
- [OpenJDK 25 DocumentBuilderFactory](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/java.xml/share/classes/javax/xml/parsers/DocumentBuilderFactory.java):
  `newInstance()` uses provider lookup; `newDefaultInstance()` selects the built-in implementation.
- [OpenJDK java.xml module](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/java.xml/share/classes/module-info.java):
  supported APIs, provider lookup and security configuration.
- [Upstream Xerces source](https://github.com/apache/xerces2-j):
  XS interfaces and service registrations. The exact upstream binary is identified
  by the source URL and hash above; no current CVE or remote exploit is claimed.
