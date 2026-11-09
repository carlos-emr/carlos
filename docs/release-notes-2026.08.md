# CARLOS 2026.08 release notes

## Fixed Jasper report downloads

Lab/billing day sheets and Ontario patient end-year statements now load bound,
typed queries before rendering and prohibit PDF cache storage. A failed report
returns an error instead of an empty or partial successful download. Statements
with missing invoice total/paid amounts fail explicitly without substituting zero.

The bundled day-sheet route now returns HTTP 204 for no appointments instead of
HTTP 200 with a blank PDF. Its `input_date` must use `yyyy-MM-dd`; missing, invalid
and non-padded dates return HTTP 400. A configured `Addresslabel.xml` still takes
precedence and retains its date parameter behavior. A present but unreadable or
non-file override now returns an error rather than silently using a bundled report.

## Patient birthday XML compatibility

Patient birthday XML is emitted as `xs:dateTime`, including midnight and the
application zone's offset, for patient records and nested appointment/consultation
responses. Earlier beta6 deployments using MOXy could emit a date-only value;
clients built against that actual output must accept the timestamp and offset.
This restores the published schema shape but is not byte-for-byte compatibility
with every beta6 provider. Existing date-only input remains accepted; REST JSON
is unchanged.

## HCV and MCEDT transport diagnostics

The existing transport logging switches now record fixed request/response events
and numeric HTTP status instead of SOAP bodies, headers or attachment contents.
HCV and MCEDT callers enable these diagnostics when their respective
`hcv.logging.skip` or `mcedt.logging.skip` setting is absent. Transport diagnostics
leave message and attachment streams for their normal consumers.

## REST appointment update compatibility

Existing schedule integrations can continue updating appointments without sending
`editVersion`; omitted, null or blank tokens retain last-writer-wins behavior.
Clients that round-trip the full response already send the token and therefore
participate in conflict detection; after a successful write, fetch a fresh
appointment before reusing it, and handle HTTP 409 by reloading and reconciling
the intended change. Clients can explicitly use conflict detection by echoing
`editVersion` from the `AppointmentTo1` response to `POST /schedule/getAppointment`;
the day and `fetchDays` summaries do not include it. A supplied stale token returns
HTTP 409 if another writer has changed the appointment. Both paths preserve
transactional archiving and server-managed audit fields. This applies to full appointment updates and the status, type and
urgency update routes. See the [REST appointment contract](api-collections-index.md#rest-appointment-edit-compatibility-and-conflict-detection)
and [#5663](https://github.com/carlos-emr/carlos/issues/5663).

## Inbox document types

Document rows in the Inbox now always show the document's type (for example `others`)
in the Discipline column, so they sort and group the same way in every view. The Inbox
Hub's first, unfiltered list and its documents-only view, and the legacy Inbox page's
documents view, previously showed the document's saved description there instead.
Views that mix labs and documents, the patient and name searches, and the REST inbox
(`/ws/rs/inbox/mine` and `/ws/services/inbox/mine`, whose document items carry the type
in `discipline`) already showed the type and are unchanged, except that a document saved
with no type is now listed with an empty value. Previously such a document ended the
list, hiding every document after it. See
[#5685](https://github.com/carlos-emr/carlos/issues/5685).

## Upgrade notes

### Startup configuration and mandatory modern chart

Every deployment must load a durable external `carlos.properties`. Classpath-only
configuration is refused even if `encryption.util.secret.key` is valid. Startup
persists `AbandonOldChart=true` to the loaded external file when absent/false;
read-only deployments must provision that setting and a valid key beforehand.
Missing keys are generated only into writable external configuration. Packaged
Debian installs and the devcontainer already select external files; manual,
demo and CI WAR installs must select one, for example with
`-Dcarlos_override_properties=/absolute/path/to/carlos.properties`.

Modern chart notes and CPP changes no longer update historical `eChart` rows.
Query by Example and custom reports reading those rows see historical data,
not subsequent chart changes. Messenger encounter attachments are labelled
**Historical encounter** snapshots, consultation/form prefills use CPP, and
legacy encounter-editor writes return HTTP 410. Historical chart print access
remains read-only and patient-scoped.

Keep the same external file, `AbandonOldChart=true` and the original encryption
key on rollback. Restoring an older false/absent flag while retaining modern-only
writes risks presenting stale historical charts. Generating a replacement key
cannot decrypt existing encrypted data. See [startup encryption keys](startup-encryption-key.md)
and [modern chart cutover and rollback](modern-chart-cutover.md).

### JDBC and packaged installation

MariaDB Connector/J 3.5.11 replaces MySQL Connector/J. The compatibility adapter
accepts legacy driver names and MySQL-style URLs, including those still produced
by the pinned `carlos-ctl` 1.3.1 for **new** packaged installs. WAR/devcontainer
defaults use the native MariaDB driver and URL. Configuration that requests
non-UTF-8 `characterEncoding`, non-utf8mb4 `connectionCollation`, DNS SRV (`dnsSrv`),
or explicit FIPS/JSSE provider selection is refused rather than silently ignored.
Review overrides and TLS requirements against the
[MariaDB connector migration guide](mariadb-connector-migration.md) before upgrade.

The debconf `carlos-emr/stop-port-conflicts` default is now **false**.
Non-interactive installs leave competing services running unless explicitly
preseeded true, so a port conflict leaves the installation **NOT ready**.
Interactive configuration asks again whenever it finds a conflict. Free the
ports and run `sudo carlos-ctl finish-install`; see [installation](install-deb.md).

### XML, SOAP and PDF consumers

The JAXB/provider migration changes XML serialization and generated WSDL/XSD
contracts. #5722 restores declared date-times for cleared calendars, present
empty map containers, valid anonymous map schemas and the beta6 REST appointment
sequence. Numeric versus literal character protocols and the beta6 Glassfish
drug sequence versus the published Prescription WSDL still need supported-client
or version/migration decisions (#5721); they must not be treated as accepted
compatibility. Namespace declarations and lexical forms may also differ.
Stored XML is now written with `standalone="no"`. Validate clients against their
actual previous provider and the birthday change above; see
[release verification](testing/release-2026.08-verification.md).

Xerces is no longer bundled: `org.apache.xerces.*`, `org.apache.xml.serialize.*`,
`org.apache.html.dom.*` and `org.apache.wml.*` imports in local customizations
need migration. Default JAXP discovery uses the JDK `java.xml` provider and its
JDK 25 limits; see [XML provider policy](cds-xml-providers.md).

The old OpenPDF `com.lowagie.*` API is removed; migrate custom PDF code to
`org.openpdf.*`. Jasper-generated PDFs now use a PDF 2.0 header. Rehearse local
printer, fax and PDF-tool workflows, especially if they depended on PDF 1.x.
See [Jasper/OpenPDF migration](jasperreports-openpdf3.md).

### Reports, charts and local customizations

- BC CSV exports use ungrouped decimal money (for example `1234.50`) and
  `dd-MMM-yy` dates. Update downstream parsers that assumed localized values.
- Dx Research XLSX exports use string cells. Report by Template and Clinical
  Reports **Export to XLS** now download OOXML `oscarReport.xlsx` rather than
  BIFF8 `oscarReport.xls`. Adjust filename, media-type and cell-type assumptions.
  The Jasper CSV/XLS exporters and `OscarDocumentCreator.CSV`/`EXCEL` modes are
  removed; use the dedicated [spreadsheet exporters](spreadsheet-exports.md).
- Measurement-chart images require the render-browser service and return 503
  when rendering is unavailable. Check that service in standalone deployments.
  Restored upstream Chart.js defaults also show gridlines on Dashboard, Growth
  Chart and Health Tracker charts.
- displaytag, JFreeChart, production POI and Selenium are removed from the WAR.
  `/taglibs.jsp` no longer declares `display` or `display-el` prefixes.
  Table paging/sorting is client-side, so old displaytag `d-…-p/s/o` bookmarks
  no longer select a page or ordering. Migrate local JSPs, plugins and reports
  before deployment; restoring removed jars alone is not a supported migration.

The [Java API migration guide](upgrade-2026.08-java-api.md) lists the original
29 changed types plus subsequent removals, replacements and behavior contracts.
It also covers object diagnostics/equality, audit text and JavaScript encoding.

### HTTP responses, permissions and validation

- REST list pagination rejects negative offsets with HTTP 400.
- Email-resolution requests distinguish invalid input (400), missing records
  (404) and conflicting identity (409). Manage Emails requires `_email` READ
  for viewing and WRITE for mutations; update custom role assignments.
- Dashboard tickler dates/times are parsed strictly. Demographic add/edit share
  text limits, and merges refuse missing patient records.
- Patient merge/unmerge is transactional, locks participant rows in stable
  order and rolls back links and audits together. The REST unmerge DAO's
  reversed parent/child bindings are corrected.
- Document additional-review actions derive the reviewer from the authenticated
  session and preserve the first reviewer/time; forged hidden reviewer fields
  no longer select an actor.
- Both clinical risk calculators refuse HDL greater than or equal to total
  cholesterol and clear stale results until inputs are corrected.
- `FormBCAR2020Text` now maps to the shipped `TEXT` column instead of generated
  `VARCHAR(255)`, preserving long form text in generated schemas.

The last four items include production corrections delivered in `8e70d7a8`
(#5701), despite that commit's `test(release)` title. The
[verification guide](testing/release-2026.08-verification.md) separates implemented
regressions from installed, vendor and client qualification still required.

## REST and SOAP hardening (candidate)

Schedule date path parameters now require a complete valid `yyyy-MM-dd` value.
Invalid days/months, trailing characters and date-time strings return HTTP 400
instead of silently selecting another day. The documented `today` day selector
continues to work. Missing appointment IDs return 400; nonexistent appointments
and search configurations return 404 rather than server errors.

REST prevention responses now carry the stored refusal/ineligibility, deletion
and never flags and original creation/update dates. Clients must use these flags
when interpreting immunization records; a refused or ineligible record is not a
completed immunization. Response field names and types are retained.

Optional measurement SOAP date elements accept empty/whitespace text as null.
Nonempty malformed dates remain errors, and the published date-time schema is
unchanged. Active demographic lists use unique patient identifier ordering;
this makes unchanged-table paging deterministic and does not create a snapshot
across concurrent changes.

The complete candidate acceptance and outstanding work is recorded in
`docs/rest-soap-release-hardening-ledger.md`; these corrections alone do not
establish full API or release qualification.

REST uncaught access, directive, validation/conversion and internal exceptions now
return structured JSON errors with `code`, a generic `message`, an optional safe
`details` object and a UTC `timestamp`. Clients should use status and code for
handling errors; raw exception text is not a public contract. Existing explicit
HTTP responses and domain/OAuth-specific mappings retain their statuses and
headers. Successful response representations and the OAuth token exchange
format are unchanged.

Patient-list rows and totals now respect patient-specific read restrictions.
Pagination applies to the permitted active-patient set. SOAP incremental
patient exports omit restricted records; explicit bulk ID reads are refused
before fetching records if any requested patient is denied.

Explicit XML profiles preserve historical MOXy date-only birthdays and Glassfish
numeric characters without guessing from ambiguous values. See
[XML compatibility profiles](rest-soap-xml-profiles.md) for request headers,
response verification and deployment/rollback requirements. Malformed XML
binding values now fail explicitly instead of silently becoming null.

Merge/unmerge now enforce manager-level write permissions for every participant
before locking or writing, and stamp actor/date metadata. Unmerge changes only
current links and returns HTTP 409 when any requested pair has no current link;
repeated requests do not fabricate new unmerge audit events. The historical
lookup method remains available for consumers that need merge history.

Appointment callers can explicitly choose guarded writes or legacy echo-client
compatibility without changing existing token-aware behavior. See
[appointment API write modes](appointment-api-write-modes.md). Unknown modes
fail before mutation, and legacy writes no longer emit a WARN on every request.

OAuth XML configuration factories now parse well-formed XML and reject malformed,
DTD-bearing or ambiguous duplicate/nested fields. Both public configuration APIs
and their distinct roles remain; see [OAuth configuration contracts](oauth-configuration-contracts.md).

SOAP login tokens are now random credentials stored as hashes, with bounded
expiry and an authenticated revocation operation. Existing numeric deterministic
tokens must be discarded at coordinated client/server cutover; password login
and published response schemas remain. See [SOAP token lifecycle](soap-token-lifecycle.md)
for configuration, refresh, multi-node deployment, revocation and recovery gates.

Prescribing rejects any medication whose patient differs from the prescription
patient before creating the header or drugs, including new medications without
a stored source row. Access to both charts does not make a mixed-patient batch
valid.

OAuth JSON date properties now use declared calendar/clock semantics even on the epoch day or at non-midnight times. The session numeric contract remains. Clients requiring historical inference can select the explicit profile in [REST JSON date contracts](rest-json-dates.md). Review the [required production integration configuration](production-integration-overrides.md) before enabling integrations. The [allergy identity decision](allergy-api-identity-decision.md) records the release contract limitation without changing published response shapes.

`DrugTransfer` JSON members remain alphabetical on both REST surfaces. `AppointmentExtTo` retains its restored explicit order. Integrations comparing JSON text should follow the [JSON property-order notice](rest-json-order-notice.md).

Demographic name/attribute SOAP searches and REST search pages/totals now respect per-patient read restrictions. Offsets count permitted patients, and blocked raw pages do not terminate an authorized search. Name/attribute request sizes are retained while internal scans use bounded batches.
