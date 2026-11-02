<!-- SPDX-License-Identifier: GPL-2.0-or-later -->
<!-- Copyright (c) 2026 CARLOS Contributors -->

# Library conventions for new CARLOS code

Use these choices for **new code** on CARLOS's Java 25 baseline. They keep common jobs consistent
without adding another helper or dependency. This page does **not** authorize rewriting existing
call sites or removing dependencies: legacy data and integrations may depend on different behavior.
Existing-code cleanup must preserve the contracts in
[#5483](https://github.com/carlos-emr/carlos/issues/5483) and
[#5484](https://github.com/carlos-emr/carlos/issues/5484), with regression tests for the actual inputs.

## Quick reference

| Job | Use in new code | Avoid in new code / compatibility notes |
|---|---|---|
| Base64 encoding | `java.util.Base64.getEncoder()` | Avoid commons-codec `Base64.encodeBase64*`. Choose alphabet, padding and line wrapping deliberately; MIME and URL encodings are different contracts. |
| Base64 decoding of new, well-formed data | `java.util.Base64.getDecoder()` for the standard alphabet; `getUrlDecoder()` for an explicitly URL-safe contract | Keep commons-codec's lenient `decodeBase64` for legacy stored HL7 bodies, keys, signatures and images. JDK decoders are not drop-in replacements; see below. |
| Base32 | commons-codec `Base32` | There is no JDK equivalent. |
| Hashing / hex | `MessageDigest` and `HexFormat.of()` | Avoid commons-codec `DigestUtils` and handwritten `%02x` loops. Use an explicit charset (`StandardCharsets.UTF_8` for text) and choose the algorithm for the protocol; use the existing password-hashing service for passwords. |
| Stream copying / reading all | `InputStream.transferTo`, `readAllBytes`, try-with-resources | Avoid `IOUtils.copy`, `toByteArray` and `closeQuietly`. Bound untrusted input before buffering it; close only resources the caller owns. |
| Upload / user file names | `PathValidationUtils`; `FilenameUtils.getName` when only the final name is needed | Do not use `Path.getFileName()` on raw user input: Linux does not treat `\` as a separator. Name extraction alone is not path validation. |
| File helpers | `java.nio.file.Files`, after the appropriate `PathValidationUtils` validation | Avoid new `FileUtils` calls. Specify parent creation, recursive deletion, charset/error handling and metadata preservation explicitly. |
| Ordered maps / multi maps / pairs | `LinkedHashMap` / `SequencedMap`, `Map<K, List<V>>`, `Map.entry` or records | Avoid commons-collections4 for these jobs. `Map.entry` rejects nulls and is immutable; use a record or `AbstractMap.SimpleEntry` when that contract is unsuitable. |
| Dates and times | `java.time`, `DateTimeFormatter`; `ZoneId.systemDefault()` when no zone is specified by the contract | Avoid new `SimpleDateFormat`, lang3 `DateFormatUtils` / `DateUtils`, or Ant `DateUtils` calls. Choose a locale for textual output; preserve explicit protocol zones. |
| Null-safe string checks | `org.apache.commons.lang3.StringUtils` | Avoid CXF's and Spring's `StringUtils`, and adding methods to CARLOS `util.StringUtils`. Use CARLOS's legacy helpers only when their special semantics are intended; see below. |
| ASCII-only case folding (protocols, identifiers) | Guava `Ascii.toLowerCase` / `toUpperCase` | Unicode `toLowerCase(Locale.ROOT)` is not ASCII-only folding. Use `Ascii` where the contract is ASCII; do not apply it to human-language text. See [#2496](https://github.com/carlos-emr/carlos/issues/2496). |
| HTTP client | Apache HttpClient 5 (`org.apache.hc.client5.*`) | Do not introduce another client for a new integration. Existing JDK `java.net.http.HttpClient` integrations remain; see the decision below. |
| JSON | Jackson | Avoid Gson and `org.json` for new CARLOS code. Reuse the relevant configured mapper so date, null and domain serialization contracts stay consistent. |
| XML parsing | `utility.XmlUtils` secure factories for JDOM2 / DOM / SAX | Avoid raw `DocumentBuilderFactory`, `SAXParserFactory` or `SAXBuilder` construction. Keep DTD/entity restrictions in place. |
| HTML parsing / sanitizing | jsoup | Parsing alone does not sanitize. Use an explicit safelist for untrusted HTML and CARLOS's null-safe encoders for output contexts; see [CLAUDE.md](https://github.com/carlos-emr/carlos/blob/main/CLAUDE.md#owasp-encoding--xss-prevention). |
| CSV | commons-csv | Avoid `StringEscapeUtils.escapeCsv` and hand-joined CSV. Choose delimiter, quoting, charset and line endings for the consumer; handle spreadsheet formula risks separately. |
| Logging | Log4j API (`LogManager` / `MiscUtils.getLogger()`) | Avoid SLF4J `LoggerFactory` in CARLOS code. Use parameterized messages; never log PHI or credentials. Sanitize necessary operational identifiers with `LogSafe`. |
| Model / DTO `toString()` | Explicitly selected, non-sensitive fields only | Avoid lang3 `ReflectionToStringBuilder` / `reflectionToString`: reflection dumps every field, including nested credentials and patient data. |
| OTP / MFA | JDK `javax.crypto.Mac`, through the existing TOTP implementation | Do not reintroduce `java-otp`. Preserve the algorithm, digits, time step and verification window; see [#5473](https://github.com/carlos-emr/carlos/issues/5473). |

## HTTP-client decision

**Use Apache HttpClient 5 for new outbound HTTP integrations.** It is already a direct dependency
and supports the custom DNS resolver used by `utility.ValidatedHttpEndpoint`. This lets a new
configurable integration reuse CARLOS's endpoint validation and pin connections to the validated
addresses, rather than validate one DNS result and connect using a second lookup. The JDK client
does not expose the equivalent per-client DNS resolver hook. Keep existing HttpClient 5 and JDK
client integrations on their current transport; this decision is not a migration request.

For a new integration:

- Validate configurable destinations, including scheme, host and resolved addresses. Use
  `ValidatedHttpEndpoint` where its contract fits, and its pinned resolver in the connection
  manager, as `MiddlewareFaxProviderClient` does. Validation without connection pinning leaves a
  DNS-rebinding gap. Do not automatically allow private destinations.
- Set finite connection, connection-pool acquisition and response timeouts. Make redirect and
  retry policies explicit: redirects must not bypass destination validation or forward credentials,
  and retries must not duplicate a non-idempotent clinical operation.
- Keep TLS certificate and hostname verification enabled. Use response handlers or
  try-with-resources so response bodies and connections are released; manage the client's lifetime
  at the integration's owning service boundary. Do not log authorization headers, credentials or
  clinical request/response bodies.

## Compatibility boundaries

- **Base64:** commons-codec `decodeBase64` accepts missing padding, ignores non-alphabet characters,
  and accepts URL-safe `-` / `_`. The JDK basic decoder rejects inputs codec accepts; the MIME
  decoder can silently discard `-` / `_`. Retain codec for those stored-data contracts.
- **Strings:** CARLOS `util.StringUtils.noNull`, `filled`, `empty` and `isNullOrEmpty` treat trimmed,
  case-insensitive `"null"` text as empty. lang3 `defaultString` / `isBlank` do not. CARLOS
  `isNumeric` uses `Double.valueOf` after that empty check (accepting values such as `1e5`, `NaN`
  and `Infinity`); lang3 `isNumeric` checks Unicode digits. Choose the semantics intentionally.
- **Streams and files:** changing `IOUtils.closeQuietly` to try-with-resources can expose close
  exceptions previously swallowed. `IOUtils.copy` returns an `int` (including `-1` on overflow),
  whereas `transferTo` returns a `long`. `FileUtils` may create parents, delete recursively,
  preserve timestamps or replace malformed text where the apparent JDK equivalent does not.
- **Collections:** insertion order is not an indexed-map API. Check indexed access, nulls,
  mutability and multi-map add/get/remove behavior before replacing an existing collection.
- **Dates:** a new formatter must not silently redefine a legacy parser. Leniency, prefix parsing,
  locale, default zone and DST behavior can all differ; retain existing behavior unless a separate
  change explicitly revises the contract.

Background: [#5485](https://github.com/carlos-emr/carlos/issues/5485) records the conventions task;
[#5480](https://github.com/carlos-emr/carlos/issues/5480) records the dependency-audit context.
