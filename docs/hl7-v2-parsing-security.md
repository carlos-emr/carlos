# HL7 v2 Parsing: HAPI 2.6.0 and the XML Entity Boundary

Issue #5414. This page records why CARLOS EMR moved from HAPI HL7 v2 1.0.1 to 2.6.0, which code
can reach HL7 XML parsing, the boundary that now protects it, how the upgrade was kept from
changing lab results, prescription QR codes and ADT exports, and what was and was not verified.
All evidence below comes from synthetic messages and disposable local files; no patient data or
real system files were used.

## Summary

- **Library**: all six HAPI HL7 v2 artifacts (`hapi-base`, `hapi-structures-v22`, `v23`, `v231`,
  `v25`, `v26`) move together from 1.0.1 (2010) to 2.6.0, the current release. Upstream fixed the
  XML parser's external entity handling in 2.4.
- **Reachability**: with 1.0.1, an authenticated user holding `_lab` write could upload a lab
  typed `PHS` whose body was HL7 XML and make the server read a local file and send it to a host
  of their choosing (out-of-band XXE). The chain was reproduced end to end against a loopback
  observer.
- **Boundary**: CARLOS no longer lets HAPI parse XML with its own reader. Bodies that may be XML go
  through `UntrustedHl7MessageParser`, whose XML side is `HardenedHl7XmlParser`: any DOCTYPE is
  refused, and if the platform XML parser cannot apply every protection, nothing is parsed.
- **Compatibility**: every pipe parser and every message CARLOS builds comes from `Hl7Parsers`,
  which keeps HAPI 1.0.1's trimming, escaping, absence of format checks and segment names. 67 of
  72 lab fixture snapshots and all three encoder snapshots are byte-identical to 1.0.1; the 5 that
  differ were reviewed one by one (below). Every HAPI-backed handler was also run over every HL7
  fixture on both versions to find differences outside each fixture's own lab type.

## Reachability assessment

HAPI only parses XML when CARLOS asks it to. Before this change there were two places:

| Code | HAPI class | Purpose | Reachable with untrusted XML? |
|---|---|---|---|
| `integration.hl7.handlers.PhsStarHandler.init` | `GenericParser` | Parse a PHS/STAR ADT or SIU message | **Yes** (see below) |
| `MEDITECHHandler.getXML`, `PATHL7Handler.getXML` | `DefaultXMLParser` | Encode a parsed message as XML | No: encoding only, and no production caller |

`GenericParser` reads a body that starts with `<` as HL7 XML. `PhsStarHandler.init` receives the
body of a lab upload whose type resolves to `PHS` in `message_config.xml`:

| Entry point | Who chooses the lab type | Privilege | PHS reachable |
|---|---|---|---|
| Web lab upload (`InsideLabUpload2Action`) | The uploading user (`type` parameter) | `_lab` write | Yes |
| REST `LabService` upload | The API client (request body) | `_lab` write | Yes |
| Signed lab upload (`LabUpload2Action`) | The type stored with the uploader's public key | Key plus `_lab` write | Only for a key typed `PHS` |
| SOAP `LabUploadWs` | Fixed per operation (CLS, Excelleris, ...) | `_lab` write | No |
| Demographic import labs | Fixed (CML, MDS, GDML) | Import rights | No |

Every lab handler other than PHS parses with HAPI's `PipeParser`, which refuses XML.

### Evidence under HAPI 1.0.1

`PhsStarHandlerXmlEntityUnitTest` drives `PhsStarHandler.init` with seven synthetic payloads built
by `Hl7XmlEntityAttackFixture` (a marker file in a temporary directory and an HTTP observer bound to
the loopback interface on an ephemeral port). Run against the unchanged 1.0.1 code, every payload
was accepted. Requests the observer received:

| Payload | Observer saw |
|---|---|
| External general entity, `file:` | (file read into the DOM; no request) |
| External general entity, `http:` | `GET /general` |
| External parameter entity | `GET /param.dtd` |
| External DTD, `SYSTEM` | `GET /external.dtd` |
| External DTD, `PUBLIC` | `GET /public.dtd` |
| Out-of-band exfiltration chain | `GET /oob.dtd`, then `GET /collect?` followed by the marker file's contents |
| Internal entity expansion | (expanded in memory) |

The exfiltration row is the important one: a parameter-entity chain read a local file and sent its
contents to a URL named in the upload. On a deployment whose server can reach the attacker's host,
that discloses any file the Tomcat account can read. Whether a given site was exposed depends on
who holds `_lab` write, whether PHS is a lab type its users can pick, and outbound network policy.

## The boundary

All in `io.github.carlos_emr.carlos.commn.hl7.v2`:

- **`HardenedHl7XmlParser`** extends HAPI's `DefaultXMLParser` and replaces only the step that
  turns text into a DOM. It sets `disallow-doctype-decl`, turns off external general and parameter
  entities and external DTD loading, enables secure processing, sets the JAXP
  `ACCESS_EXTERNAL_DTD` and `ACCESS_EXTERNAL_SCHEMA` limits to nothing, disables XInclude and
  entity-reference expansion, and installs an entity resolver that refuses every lookup. Any of
  these that the platform parser rejects makes parsing fail with an `HL7Exception`; HAPI's own
  `XMLUtils` would log a warning and parse anyway. A rejected message is reported only through
  that exception, which names the line and column but carries neither the platform parser's
  diagnostic nor its exception as a cause: the diagnostic can quote message text, and the PHS
  upload logs a rejection with its causes. Without its own error handler the platform parser
  would also print `[Fatal Error] ...` to standard error (Tomcat's `catalina.out`). Mapping the
  DOM onto HAPI's model and encoding are unchanged.
- **`UntrustedHl7MessageParser`** replaces `GenericParser`: pipe bodies go to a `PipeParser` from
  `Hl7Parsers`, XML bodies to `HardenedHl7XmlParser`, anything else is refused, as before.
  `PhsStarHandler.init` uses it, and no longer writes the message body (patient demographics) to
  the INFO log.
- **`Hl7ParserBoundaryGuardUnitTest`** fails the build if production code constructs or
  subclasses `GenericParser`, `DefaultXMLParser`, `XMLParser` or `PipeParser`, asks a
  `HapiContext` for its default parsers, uses HAPI's `XMLUtils` or pre-parser (whose SAX reader
  has no entity protection), creates `NoValidation`, calls `setValidationContext`, or builds a
  message without `Hl7Parsers.forBuilding`.

HL7 v2 XML never needs a DTD, so refusing DOCTYPE rejects no valid message. A refused upload fails
with an error, as any malformed upload does; nothing is half-imported.

## Keeping results identical: `Hl7Parsers`

HAPI 2.x behaves differently from 1.0.1 in ways that would have changed what CARLOS shows and
sends. Each was found by a snapshot test, not assumed:

| Difference in 2.x | Effect if left alone | Handling |
|---|---|---|
| `NoValidation` right-trims TX text instead of left-trimming FT/ST/TX | Indentation and padding of text results move | `LegacyHl7NoValidation` restores 1.0.1's rules |
| `\H\`, `\N\`, `\Xhh\`, `\C..\`, `\M..\`, `\Z..\` are kept intact instead of losing their escape characters | Raw escape codes appear in results; encoded output changes | `LegacyHl7Escaping`, identical to 1.0.1 on 200,000 random strings with three delimiter sets |
| In a message HAPI parses with its generic model (a type the structures jar does not define, such as the `ORU^O01` `IHAHandler` forces, or a version with no structures jar, which the generic handler now receives), a later run of a segment type is folded into repetitions of one numbered name (`OBR2` ×2) instead of one name per segment (`OBR2`, `OBR3`) | Handlers that walk segment names lose a request when two are adjacent and shift every later one; note counts change | `LegacyHl7PipeParser` parses each segment with HAPI 2.x's own segment parser but places it under its 1.0.1 name; the rule matched 1.0.1 on 2,000 random segment sequences |
| A subcomponent separator inside a single-value OBX-5 stays in the value | Results text changes; handlers that re-join subcomponents print a stray `&` | `Hl7Parsers` sets HAPI's `ca.uhn.hl7v2.model.varies.escape_subcomponent_delim_in_primitive` property to `false` unless an operator set it |
| A message being built checks numeric and date fields and trims text | A prescription with a dose such as `1-2` or a quantity such as `30 (thirty)` produces no QR code; padded names lose their padding | `Hl7Parsers.forBuilding` attaches a parser with no rules, as 1.0.1 effectively had |
| Indexed group and field getters throw an unchecked exception (wrapping the `HL7Exception`) instead of `HL7Exception` | A lab page could fail where it used to show a blank | The fourteen narrow `catch (HL7Exception)` blocks this affected now catch `RuntimeException` and pass it to `Hl7StructureAccess`, which rethrows anything that is not HAPI's wrapped `HL7Exception`, so other failures propagate as before |
| v2.3 PID-5 is a repeating field | Compile errors | Callers read repetition 0, which is what 1.0.1 returned |

`Hl7Parsers.newPipeParser()` is for received HL7; `forBuilding` and `newEncodingPipeParser()` are
for messages CARLOS builds (prescription QR code, ADT^A04 export). The QR code used to be encoded
by one static parser shared across requests; `Hl7ParserUtils.encode` now creates one per call,
since HAPI does not document its parsers as safe to share between threads, and the encoded
prescription (patient name, health number, drugs) is no longer written to the debug log.

### Behaviour that changes on purpose

These differences remain, each reviewed against the fixture text. None alters a value that 1.0.1
produced for a lab type's own handler; each turns a rejection into a result, or concerns a handler
or message shape no lab reaches.

- Messages 1.0.1 rejected now import through the generic handler: HL7 v2.4 (no structures jar),
  v2.5.1, v2.7 and v2.8.1, and an `MSH-2` that carries the v2.7 truncation character. Unknown
  versions such as `9.9` and a blank version are still rejected. A handler for a specific lab that
  is handed such a message still refuses it; its `init` now fails with a `ClassCastException`
  instead of an `HL7Exception`, and every caller (`Factory`, the upload handlers, the upload
  actions) catches both alike.
- An IHA POI `ORU^O01` with two ZDR segments now re-encodes correctly instead of failing.
- `SpireHandler.getZDSName` and `getZDSResultStatus` return an empty string rather than `null` for
  an empty field. The lab page renders both the same way.
- `IHAHandler` (in `lab.ca.all.parsers`; no lab type in `message_config.xml` uses it) reports each
  later request's own requested date. HAPI 1.0.1's Terser found `/.OBR2-14-1` for some messages
  and otherwise fell back to the message date.
- A typed message whose segments fall outside the message structure (in the fixture, Z segments
  before PID and an OBX before any OBR) has those leftover segments placed after the main group
  rather than before it, which changes what the ALPHA, CLS, generic and HHS EMR handlers read from
  it. Only the MDS handler receives this shape, and its output is unchanged.

## Dependency graph

- `xercesImpl` 2.12.2, `xml-apis` 1.4.01 and `commons-cli` 1.0 reached the WAR only through HAPI
  1.0.1 and are gone. CARLOS now uses the JDK's XML implementation everywhere. It supports every
  feature and property CARLOS sets, and it honours the JAXP `ACCESS_EXTERNAL_*` limits that
  `XmlUtils` sets, which Apache Xerces 2.12 did not.
- `joda-time` 2.1, declared by `hapi-base` 2.6.0 but referenced by no HAPI class and no CARLOS
  code, is excluded.
- The vendored HAPI 1.0.1 artifacts under `local_repo/ca/uhn` were removed; 2.6.0 comes from Maven
  Central and is pinned by `dependencies-lock.json`.

## Verification

- `PhsStarHandlerXmlEntityUnitTest`, `HardenedHl7XmlParserUnitTest` and
  `UntrustedHl7MessageParserUnitTest`: every attack class is refused with no file or network
  access, the parser refuses to run when a protection is unavailable, and benign pipe and XML
  messages still parse.
- `LabHandlerHl7ParsingSnapshotUnitTest`: 72 fixture files (vendor samples plus 54 synthetic
  fixtures across every HAPI-backed lab handler, with escapes, UTF-8, repetitions, timestamps,
  embedded documents, generic-model segment runs and deliberate error cases), about 3,500 OBX
  results. Snapshots were recorded with 1.0.1, in UTC, and pass in five time zones.
- A one-off differential outside the test suite ran all 17 HAPI-backed handlers over all 62 HL7
  fixture files (1,054 combinations), each through 1.0.1 and through this change. It found the
  generic-model naming difference above, which the snapshot test had missed because it runs each
  fixture only through its own lab type's handler; the remaining differences are the ones listed
  under "Behaviour that changes on purpose". No message that 1.0.1 parsed is rejected now.
- `IHAHandlerUnitTest` pins the adjacent-request count (as 1.0.1) and the request dates.
- `Hl7MessageEncodingSnapshotUnitTest`: prescription QR code and ADT^A04 output, recorded with
  1.0.1, including free-text doses.
- `LegacyHl7EscapingUnitTest`, `LegacyHl7NoValidationUnitTest`, `Hl7ParsersUnitTest`: expected
  values were produced by running HAPI 1.0.1 on the same input.
- `scripts/lab-upload-xml-entity-playwright-checks.js` (`npm run
  test:lab-upload-xml-entity-playwright`, suite check `lab-upload-xml-entity`) runs the same attack
  classes against a deployed CARLOS: it posts PHS uploads with the session's CSRF token, as a user
  with `_lab` write could, and requires every payload to be refused with no request to an HTTP
  observer it runs on a loopback port, while benign pipe and XML PHS messages are accepted. Run
  against a package built with HAPI 1.0.1 it fails; against this change it passes. It needs the
  server to share its network namespace (loopback `BASE_URL`, as in the devcontainer and the
  Debian validation container) or `LAB_XXE_OBSERVER_HOST`, and skips otherwise.
- The pull request records the full unit, integration and script suites, the dependency-lock
  check and the Debian package install in an Ubuntu 26.04 container on which this check and the
  existing lab upload checks ran.

### Not verified

- Real vendor feeds beyond the samples in this repository. A site that receives a message shape no
  fixture covers could still see a difference; the snapshot test is where to add it.
- Exploitability on any particular deployment (privileges, PHS configuration, outbound network).

## Pre-existing behaviour found, not changed

The fixtures pin these 1.0.1-era behaviours so they cannot change by accident. Each deserves its own
reviewed fix:

- An unescaped `&` in a single-value result truncates it: an IHA blood bank line
  `ORD TESTS: Group & screen, XM` shows as `ORD TESTS: Group`. HAPI 2.x can keep the whole text
  (set the system property above to `true`), but the handlers that re-join subcomponents need
  adjusting first.
- `\H\`/`\N\` highlighting shows as stray `H`/`N` letters rather than formatting.
- `DefaultGenericHandler` drops a character where it joins wrapped lines, and only sees OBX results
  for messages that carry an explicit non-standard structure in MSH-9. In a generic message it
  reports only the first note of a later run of NTE segments, because 1.0.1's names (kept here)
  give each of them its own name.
- `MDSHandler` never finds ZPD/ZFR segments, so blocked and partial flags never take effect.
- `SpireHandler` drops every OBR after the first when a ZDS segment sits inside an OBR group.
- Several handlers fail to format timestamps that carry a time zone offset and show a blank date.
