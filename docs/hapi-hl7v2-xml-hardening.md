# HAPI HL7 v2 upgrade and XML entity hardening (issue #5414)

CARLOS EMR read HL7 v2 messages with HAPI HL7 v2 (`ca.uhn.hapi`) **1.0.1**, a 2010 release. That
parser followed external entities and external DTDs. This note records what was found, what was
changed, and what was and was not verified. It covers `ca.uhn.hapi` only; HAPI FHIR
(`ca.uhn.hapi.fhir`) is a separate dependency on its own version line.

No real patient data, credentials or sensitive files were used. Every message, file and server in
the tests is synthetic and local.

## Result

| | Before | After |
|---|---|---|
| `hapi-base` and `hapi-structures-v22`, `v23`, `v231`, `v25`, `v26` | 1.0.1 (vendored in `local_repo/`) | **2.6.0** from Maven Central, one `hapi-hl7.version` property |
| External general entities, external parameter entities, external DTD | followed (file read, HTTP request) | not loaded |
| `DOCTYPE` in HL7 XML reaching CARLOS code | accepted | refused by `Hl7XmlMessageValidator` before HAPI parses it |
| Transitive `xercesImpl` 2.12.2, `xml-apis` 1.4.01, `commons-cli` 1.0 | on the classpath | gone (the JDK's built-in JAXP parser is used); `joda-time` excluded |

## Where HL7 XML enters CARLOS

HAPI parses XML in one place that CARLOS reaches: `GenericParser` in
`integration.hl7.handlers.PhsStarHandler.init(String)`. `GenericParser` picks the XML parser when
the text looks like HL7 v2 XML, and the pipe parser otherwise.

| Code | HAPI call | Reads XML? |
|---|---|---|
| `PhsStarHandler.init` | `GenericParser.parse` | **Yes**, if the message is XML |
| 17 lab handlers in `lab.ca.all.parsers` (CML, CLS, MDS, GDML, Spire, IHA, IHAPOI, MEDITECH, PATHL7, Epsilon, Excelleris Ontario, TrueNorth, Alpha, BioTest, CDL, HHS EMR, default) | `PipeParser.parse` | No: `PipeParser` never reads XML |
| `MEDITECHHandler.getXML`, `PATHL7Handler.getXML` | `DefaultXMLParser.encode` | No: encodes a message to XML, parses nothing |
| `HL7A04Data`, `OmpO09Builder` | `PipeParser.encode` / builders | No: build outbound messages |
| Upload handlers `MEDITECHHandler`, `IHAPOIHandler`, `IHAHandler`, `DefaultHandler`, `ExcellerisOntarioHandler`, `PATHL7Handler`, `PathNet.Connection` | CARLOS's own `XmlUtils.createSecureDocumentBuilderFactory()` | Yes, but not HAPI: already hardened (`disallow-doctype-decl`) |

**How untrusted input reaches `PhsStarHandler`.** The `PHS` lab type is chosen by the lab upload
route:

- *Manual upload* (`InsideLabUpload2Action`): any user holding `_lab` write privilege can post
  `type=PHS` with a file. The upload popup does not list PHS, and its free-text "Other" box is not
  submitted, but the action takes any registered handler name from the `type` parameter.
- *Signed lab interface* (`LabUpload2Action`): the type comes from the registered upload key, so
  only a sender holding a key configured as `PHS` reaches it.

So the input is attacker-controlled only by an authenticated user with lab-write rights or by a
holder of a PHS interface key. It is not reachable anonymously. Whether the content of a local
file can be read back through a CARLOS page or response was **not** established (the unfixed
library loads the file into its parsed DOM, but the re-encoded pipe message did not carry it); do
not read this as a demonstrated disclosure. It is an unsafe-parser finding with an authenticated
entry point.

## What changed

### 1. HAPI HL7 v2 1.0.1 to 2.6.0 (all six artifacts together)

2.6.0 (February 2025) is the latest release. HAPI 2.x sets `external-general-entities`,
`external-parameter-entities` and `load-external-dtd` to false. It does **not** forbid the
`DOCTYPE` itself, which is why the validator below exists.

The vendored 1.0.1 artifacts under `local_repo/ca/uhn/` (including the unused `v21` and `v24`
structure jars, which were never in the `pom.xml`) are removed. There is no other copy of `ca.uhn.hapi`
in the repository. `dependencies-lock.json` is regenerated: the six HAPI entries move to 2.6.0 with
new integrity hashes, and `commons-cli`, `xercesImpl` and `xml-apis` drop out.

`hapi-base` 2.x declares `joda-time` 2.1, which no class in `hapi-base` or any `hapi-structures`
module uses and whose time zone data dates from 2012; it is excluded.

### 2. `Hl7XmlMessageValidator` (defence in depth)

HAPI 2.6.0 still parses a `DOCTYPE` and expands the internal entities it declares. Measured on
2.6.0: a six-level "billion laughs" message took 1.2 s, and the cost grows exponentially with every added level.
`Hl7XmlMessageValidator.requireNoDocumentType(parser, message)` parses an XML message first with
the project's hardened `XmlUtils.createSecureDocumentBuilderFactory()`, which rejects any
`DOCTYPE` outright. `PhsStarHandler.init` calls it before `GenericParser.parse`. Whether the text is
XML is decided by the same `Parser.getEncoding` call HAPI itself makes, and pipe messages are not
inspected (a result value may legitimately say `<!DOCTYPE`). The exception text is fixed and never
echoes the message.

### 3. Behaviour that HAPI 2.x changes, and how the code keeps it the same

An API-compatible build is not a behaviour-compatible one. Every lab handler was run over every
fixture and a set of synthetic messages on both HAPI versions, and the results were compared
(see Verification). That found four things beyond the compile errors:

1. **Missing repetitions are `RuntimeException`s now.** 1.0.1 threw a checked `HL7Exception` for
   `getORDER_OBSERVATION(7)` on a message with two; 2.6.0 throws a `RuntimeException` caused by one.
   A `catch (HL7Exception)` that still compiled would silently stop catching it. The 14 handler
   blocks that wrapped such lookups now catch `RuntimeException` and call
   `Hl7Utils.rethrowUnlessHl7AccessFailure`, so they still answer with an empty value and other
   runtime failures stay visible. `CLSHandler`'s five public accessors declared
   `throws HL7Exception`; they now convert back with `Hl7Utils.unwrapHl7AccessFailure`.
2. **Generic-message segment names differ, and results would have been dropped.**
   `IHAHandler` and `DefaultGenericHandler` find the nth request as `"OBR" + n`. HAPI 1.0.1 folded
   adjacent same-type segments into repetitions only for the first run of a type; 2.x folds every
   run. With 2.x names, `CC63171.hl7` (requests 19 and 20 are back to back) reported 20 requests
   instead of 21, with no error. `GenericSegmentNames` rebuilds the 1.0.1 names from the message,
   and the handlers use it; their results on the sample labs are unchanged.
3. **Numbered Terser paths.** 1.0.1 could not resolve `/.OBR2-14-1`, so
   `IHAHandler.getRequestDate(i)` always returned the message date for every request after the
   first. 2.x resolves it (by its own names) and would have started storing a different date.
   The message date is now returned explicitly, so the stored value is unchanged.
4. **v2.3 `PID-5` is a repeating field in 2.x.** `getPatientName()` became an array; the call sites
   use `getPatientName(0)`, the first repetition, as before. `ExtraComponents.getComponent` returns
   `Variable` instead of `Varies`.

### 4. Differences that remain, by design

These are what the comparison still shows, none of them in clinical field values:

- **TX values lose trailing blanks; they keep leading blanks.** HL7 v2 defines trailing blanks in
  `TX` as insignificant and leading blanks as significant. 1.0.1 did the reverse. In the sample
  data the only effect is Epsilon's `Clinical History: ` now reading `Clinical History:`.
- **HL7 2.4 and 2.5.1 messages are accepted as generic messages.** There are no 2.4 or 2.5.1
  structure jars, so 1.0.1 refused such a message with an `HL7Exception` and the factory fell back
  to a `null` handler. 2.x parses it as a generic message, so `DefaultGenericHandler`,
  `HHSEmrDownloadHandler` and `IHAHandler` now read it. The version-specific handlers still reject
  it, now with a `ClassCastException` that `Factory` already catches.
- **`getXML()` on the MEDITECH, PATHL7 and IHAPOI handlers** now writes
  `<?xml version="1.0" encoding="UTF-8"?>` and no newline after it. Nothing in CARLOS calls it.
- **`SpireHandler.getZDSName` / `getZDSResultStatus`** return an empty string where they returned
  `null` for an absent field; the only caller encodes both identically.
- **Outbound `OMP^O09` (prescription QR)** encodes exactly as before apart from the timestamp and
  the build stamp. The `ADT^A04` builder changed only its `PID-5` accessor (item 4 above).

### 5. Pre-existing quirks left as they are

The goal was no change in what labs show, so two oddities of the generic handlers are preserved
and noted here for a separate change: only the first `NTE` after a request or result is reachable
when later ones follow in a run; and `IHAHandler.getOBRComment` returns field 3 of whatever segment
follows a request that has no `NTE`.

## Verification

| Check | Result |
|---|---|
| Lab, HL7, HRM and appointment-archive tests (1,855 tests) before and after | 1,855 passed, 0 failed, both times |
| Dependency lock check, clean compile, checkstyle | pass |
| Differential run: every public getter of 17 handlers x 83 messages (5 CML, CLS, 2 Epsilon, MEDITECH, IHAPOI, Excelleris/PATHL7 archives and synthetic ORU in HL7 2.2 to 2.6), about 176,000 observations on each version | identical except the items listed above |
| New tests (below) fail on the vulnerable 1.0.1 baseline and when the fix is removed | confirmed by mutation |

New tests:

- `HostileHl7Xml` (test support): synthetic hostile messages, a disposable marker file and a
  loopback HTTP observer. No public service is contacted.
- `HapiHl7XmlParserHardeningUnitTest`: HAPI itself, with none of CARLOS's checks in front. For an
  external file entity, an external HTTP entity, an external parameter entity, an external DTD and a
  file read through a parameter entity, parsing either rejects or reads the message without the
  external part: no request reaches the observer and the marker never appears. A control shows the
  observer does see a parser left at its defaults. Fails on 1.0.1.
- `Hl7XmlMessageValidatorUnitTest`, `PhsStarHandlerXmlBoundaryUnitTest`: the guard, the handler
  boundary (no record touched), and that valid XML and pipe messages are still read.
- `GenericSegmentNamesUnitTest`: the 1.0.1 naming rules and the 21-request regression.
- `Hl7UtilsUnitTest`, `HapiAccessFailureFallbackUnitTest`: the access-failure helpers, the handler
  fallbacks and the TX behaviour.

## Not verified

- Remote exploitability and disclosure through a CARLOS response were not established (see above).
- Real lab messages from production senders were not available. Coverage is the repository
  fixtures plus synthetic messages; a sender whose messages rely on one of the 1.0.1 behaviours
  listed under "Differences that remain" would see that difference.

## Adding code that parses HL7

- Text from outside CARLOS that may be XML goes through `Hl7XmlMessageValidator` before any
  `GenericParser` or `DefaultXMLParser.parse`. The lab handlers use `PipeParser`, which never reads
  XML.
- Do not look repetitions up by HAPI's generic-message names; ask for the repetition by index, or
  use `GenericSegmentNames`.
- Around a typed HAPI accessor that can miss, catch `RuntimeException` and call
  `Hl7Utils.rethrowUnlessHl7AccessFailure`, not `HL7Exception` alone.
- Keep the six `ca.uhn.hapi` artifacts on the single `hapi-hl7.version` property.
