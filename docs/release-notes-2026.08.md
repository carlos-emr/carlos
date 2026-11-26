# CARLOS 2026.08 release notes

## Dose, strength and quantity numbers are written as plain decimals

ISMP Canada's *Do Not Use* list bans a trailing zero ("1.0 mg" is read as 10 mg) and a
missing leading zero (".5 mg" is read as 5 mg). A review of every place CARLOS turns a
dose, strength or quantity number into text (#6185) found the following, all now written by
one formatter: the exact plain decimal, with no trailing zero, always a leading zero, no
exponent and no rounding.

- **Rounded dose.** The structured dose (take 1-2) was rounded to three decimal places for
  display, so a dose of 0.0625 was shown as "0.062" on the Rx record, the Rx page's staging
  card, the BC health passport forms and the VT form. The Rx page posts that text back as
  the dose, so opening and saving such a prescription stored 0.062. The dose is now shown in
  full and saved unchanged. A dose with no finite decimal form is shown to the precision it
  is stored in: a third of a tablet reads "0.33333334" where it read "0.333". The
  instruction text the prescriber typed ("Take 1/3 tab") is not changed.
- **Quantity.** A quantity typed with a decimal amount and a unit was split at its first
  run of digits: "2.5 mL" was saved, printed and faxed as `Qty:2 .5 mL`. It is now saved as
  `Qty:2.5 mL`. A typed quantity is written as a plain decimal, with or without a unit:
  ".5 mL" is saved as `Qty:0.5 mL`, "30.0 mL" as `Qty:30 mL` and "30.0" as `Qty:30`. The
  default quantity carried over from the previous prescription for the same drug keeps its
  decimal amount.
- **Strength.** DrugRef sends an ingredient strength as "250.0". CARLOS copied that into
  the generic name and strength stored with a new prescription ("amoxicillin 250.0mg
  capsule"), showed it on the Drug Information page and wrote it to the CDS 5.2 export.
  New prescriptions store "250mg". The generic name of a combination product now names
  every ingredient's strength; the strength of a second ingredient was left out when it
  equalled the first's. The REST medication service stored "500.0 mg", or the word "null"
  for a strength or unit the client did not send; it now stores "500 mg" and leaves a
  missing part out.
- **Other pages.** The Edit Favorites and Copy Favorites pages showed "1.0" for a dose of
  1, and the immunization submission review page showed "Dose Qty: 1.0". The BPMH form's
  "How I take it" column left the dose out ("Take TAB BID") when the stored instruction
  text had no direction line; it now reads "Take 1 TAB BID", and "Take 1 to 3 TAB BID" for
  a range.

Prescriptions saved before this change keep the text they were saved with, and the upgrade
rewrites none of it:

- A stored "250.0mg" stays. The medication lists treat it as the same product as a "250mg"
  written afterwards, so a renewal is still listed once. The "discontinued before" notice
  and the check for an existing favourite compare the stored text exactly: the notice can
  show although a later prescription written before the upgrade is still active, and saving
  a favourite again adds a second one.
- A dose that a save had already rounded stays rounded.
- A quantity that was split stays split, and re-prescribing it copies it as it is. It
  cannot be repaired by rule: "2.5 mL" and "2.25 mL" were both stored as the quantity "2"
  with the unit ".5 mL". Review these prescriptions, typically liquids, drops and creams:

  ```sql
  SELECT drugid, demographic_no, quantity, unitName FROM drugs WHERE unitName LIKE '.%';
  ```

REST clients: `quantity` on a drug or a favourite is a whole number. A stored quantity that
is not one ("2.5", or imported text such as "100 mL") is now omitted from the response;
reading such a drug used to fail the whole request. An update that states no quantity keeps
that stored quantity and its unit, and the `instructions` text still states it. `strength`
is now reported for a strength of one digit ("5 mg") and for one stored without a unit. For
a combination it is the first ingredient's, with that ingredient's unit: "5 mg / 20 mg"
reads 5 and "mg", where it read 20 and "mg / ". A strength stored earlier with an exponent
("1.0E7 unit") reads as that number. A strength is read only when the stored text starts
with it. Text in which another number follows the first before any unit ("1,000 mg",
"1 000 mg", "1/2 mg", "1 / 2 mg", "5/20 mg", "5 - 10 mg", "1:1000", or a fraction
character after the digits) has no `strength`: the first digits of such text are not the
amount, and neither is the next ingredient's number. Text that starts with a unit
("mg/5mL", stored when a unit is sent without a strength) is returned as `strengthUnit`
with no `strength`.
For the same product, a REST update that omits the strength or echoes the projected amount
and unit keeps the stored dosage, including additional ingredients. An updated amount, unit
or product uses the replacement's fields.

Rolling back to an earlier 2026.08 build keeps the prescriptions this build saved. The
earlier build's REST drug list fails on a quantity that is not a whole number, as it already
did for one typed without a unit; this build saves "2.5" for a quantity typed as "2.5 mL",
where the earlier build saved "2".

The inventory, with each place checked and the findings that are not number formatting, is
in `docs/testing/dose-text-format-inventory.md`.

## Ontario OHIP billing after a single-site import

OHIP Simulation and Generate OHIP File now show eligible physicians and their
billing disk history when a single-site clinic has no provider-site assignments.
The seeded administrator can use both pages with the existing role configuration.
When multisite mode is enabled, an operator restricted by site alone sees only
providers who share an assigned site. An explicit team billing restriction
continues to apply in either clinic mode.

During an OSCAR 19 migration, compare the source `multisites` setting with
the setting in CARLOS before opening billing to staff. This also applies to
completed imports. A clinic that relied on site assignments to restrict billing
with multisite mode disabled must enable multisite mode and verify access for its
operators. [OSCAR 19 import guide](o19-import-deb.md)

## Unlinking a lab archives the readings it filed

Unlinking a lab report from a patient (Unlink on the lab page) now also archives the
readings the report had filed to that patient's chart. After a wrong patient match is
corrected, flowsheets, the Health Tracker, graphs, decision-support colours and the
prevention and clinical reports no longer show the other patient's values (#6214). The
readings are moved to the same measurement archive that a clinician's deletion and a
superseded report version use: nothing is deleted, and the audit log records the unlink and
each archived reading by its identifier. The lab page then shows how many readings were
archived, in a notice that closes itself. When the report is matched to a patient again,
its current version is filed for that patient, as for a report matched after upload.

Unlink still acts on the one report shown; other versions of the same accession keep their
own patient and readings. It is not refused because of what the chart holds, and the
routing change, the archive and their audit rows are saved together or not at all.
Readings left on a chart by an unlink made before this change, including charts migrated
from OSCAR 19, are not changed by the upgrade: unlinking the same report again archives
them. Unlink now accepts only POST, which is what the lab page sends, and needs the `_lab`
update right on each chart it changes, so a patient-specific lab rule or a locked record
refuses it as it already refuses opening that lab.

## Demographic updates save together or not at all

A patient update through the demographic manager is now one transaction: the archive
copy of the previous record, the patient record and its extensions are saved together.
A failure partway through, such as an extension the database refuses, no longer leaves
an archive row or a changed patient behind. The same applies to saving a patient's
custom fields (alert, notes, nurse, resident, midwife) with their archive copy.

`PUT /ws/rs/demographics` has run as one transaction since 2026.08.0-beta8, including
the custom fields and the waiting-list entry it changes. Inside that transaction the
previous alert and notes were overwritten without an archive row; they are archived
again. When an update is refused or one of its writes fails, the transaction is rolled
back and nothing of it is saved. When the outcome is uncertain, because no response
arrived or the server reported a failure while committing, the update may have been
saved: reload the patient before deciding whether to resubmit. An update whose audit
row cannot be written is not saved. Existing records are unchanged. Custom fields
overwritten through this endpoint on 2026.08.0-beta8 were not archived and cannot be
reconstructed.

Three requests that this endpoint used to accept or fail on unclearly are now refused
with HTTP 409 and a JSON error code, and save nothing: a second extension with a key
the patient already holds (`DUPLICATE_DEMOGRAPHIC_EXTENSION_KEY`), an extension id
that is not one of this patient's stored extensions (`DEMOGRAPHIC_EXTENSION_NOT_FOUND`;
the id alone could re-point another patient's extension to this patient), and a
waiting list on which the patient has no current entry (`WAITING_LIST_ENTRY_NOT_FOUND`; the
patient was saved and the waiting-list note dropped without an error, or, when the entry
had been removed through the application or carried no history flag, the patient was given
a current entry on the list). Every extension
is saved under the patient being updated, whatever patient number the request gives it.
An update that names a waiting list now locks that list's current entries until it is
saved. When another update of the same list is in progress or has just changed them, the
request is refused with HTTP 409 `WAITING_LIST_CHANGED` and nothing is saved.
Integrations that send extension ids or a waiting-list id should reload the patient on
these codes.

An update that records a waiting-list note no longer moves the patient down the list.
The entry that replaces the patient's entry used to be dated today, and a list is
numbered by start date, so the patient was placed behind everyone who had joined
later. The replacement now keeps the start date, and entries with the same start date
keep their order. Entries already re-dated this way
keep the date they were given; the earlier entry, with the original date, is still
stored as history. Removing a patient from a waiting list now waits for an update of
that list that is in progress, instead of reporting success while the update leaves
the patient on the list.

An Ontario OBEC response that invalidates a health-card version code now archives the
patient with the code that was replaced; the archive row used to hold the `##` marker.

Reassigning patients to another MRP (Administration, Update Demographic Provider) no
longer replaces a patient's stored preferred name with the alias. The reassignment
updates from a copy of the patient, and the copy took the name shown, which is the alias
when one is set, as the preferred name. Preferred names replaced this way are not
restored by the upgrade.

## Note lock recovery

When another window takes a note lock, a refused toolbar save now explains that
the lock was lost and tells the clinician to copy their text and reopen the chart.
The draft remains available, and autosave stops for that editor. Opening a different
editor restores its own saving. Close still asks before discarding unsaved text,
dates or issue changes, including after a lock is lost.

## New-appointment day-sheet acknowledgment

Opening or printing the new-appointments day sheet is read-only. Appointment
writers must use **Acknowledge new appointments** to mark the displayed rows as
Daysheet Printed. The form now carries the displayed appointment IDs and versions.
Appointments booked or edited after preview are left unchanged, with a skipped
count in the result; preview again before acknowledging them. Repeating the same
submission does not acquire newly booked appointments or create duplicate archives.
Incomplete, malformed or oversized selections are refused before any update.

## Audit identifiers after model diagnostic redaction

Demographic-extension, consent-type, appointment and disease-registry creation audit
rows now identify their affected records explicitly. Newly saved extensions and
consent types are logged after persistence assigns their ids; a failed consent-type
save emits no creation event. Extension events retain the sanitized field key,
appointments retain patient/provider identifiers, and diagnoses retain the coding
system and entry id. Extension values, appointment text and diagnosis codes are not
added to audit data. Existing historical audit rows are unchanged.

These events no longer depend on entity `toString()` formatting. The model diagnostic
redactions remain in place. See [the audit contract](security-object-diagnostics.md).

## Installation, configuration and release hardening

Packaged starts reconcile the mandatory modern-chart flag before Tomcat, even
after deferred configure or configuration restore. A second check as the service
user validates the effective configuration and original encryption key. Root
never repairs a legacy home override; startup names conflicting files and refuses
an unusable override or missing key. See [key recovery](startup-encryption-key.md)
and [chart cutover](modern-chart-cutover.md).

Upgrades preserve the published beta6/beta7 Tomcat conffile bytes, avoiding a
comment-only dpkg prompt on edited hosts. Hosts that installed intermediate
snapshot payloads can still receive a normal conffile prompt. The unchanged
nginx distribution default-site symlink is retired; custom files/targets are
preserved. Certificate regeneration keeps an operator-stopped nginx stopped.
Unattended installs announce a retained true debconf stop-port-conflicts answer;
fresh defaults remain false and saved/preseeded choices are retained.

Audit purge now verifies and durably stores exact locked cohorts before deleting
them. Failures retain recoverable backups and report partial completion explicitly.
See [audit purge and recovery](audit-log-purge.md) for client/TLS prerequisites.

Release automation now builds drafts and requires exact-tag qualification before
publication, including direct Debian dispatches. Final artifact bytes are promoted
without rebuilding. The existing incomplete release matrix continues to block
readiness; this repair does not certify a release. See the
[publication procedure](testing/release-publication.md). The published carlos-ctl
pin, dependency floor and command interfaces are unchanged.

## Measurement charts, CML revisions and CDM forms

Single-point and constant-value measurement charts now draw useful markers and axes.
Point labels retain Java HALF_EVEN rounding and the server's locale/date conventions.
Graph failures return explicit 422, 500 or 503 responses; embedded lab and medication
graphs provide a visible retry control.
Graphs opened in their own window (measurement history, Health Tracker, flowsheets) say
what went wrong: the drawing service is busy or not running, or an unexpected problem was
logged (each with a Retry button); or which stored values cannot be drawn and why, naming
the measurement type or lab test, such as a blood pressure not in the form 120/80 or a
value with no date (without Retry). The page is styled like the CARLOS error page.
The medication-profile graph keeps its default height up to 20 drugs, then grows 30 px per
drug; 11 to 19 drugs no longer draw a shorter chart than 10.
Measurement, lab and medication graphs are drawn by the bundled render browser that eForm
print and fax use. Graphs have one browser of their own beside the two they share with
eForm PDFs, so a graph no longer waits behind PDF renders while its own browser is free;
while every browser is busy, or the service is down, graphs ask to be retried. A standalone
WAR without the render-browser service shows no graphs.

Qualified bounds such as `<5` continue to plot as 5 pending the clinical decision in
[#6118](https://github.com/carlos-emr/carlos/issues/6118). The parser reads left to right in one pass. It accepts one
number with supported qualifiers/units and rejects titres, dates, exponents, ranges
and malformed grouping, preventing digit groups from being joined into a different
value. Stored results and source reports remain available unchanged.

CML versions are ordered by result stage, comparable OBR-22 revision clocks,
correction status, completeness and arrival. A same-revision partial final delivered
late no longer replaces the complete final. Collection dates do not demote a
correction whose revision clock is absent. Filing retains the whole-report rule:
the current version replaces every earlier version's measurements, including readings
omitted by a partial correction. Those readings remain in the earlier report/PDF.
Acknowledgement files all older versions resolved for that transaction and preserves
prior acknowledgement audit data. Unlink remains a single-report operation.

CDM report tabs keep their own definitions through conversion/validation errors and
corrected resubmission. Invalid or expired forms refuse the report with a recovery
message instead of using another tab's group. Lab history and graph access now
require patient-level `_lab` read rights; unmatched history retains its existing
invalid-demographic message. See the [shipping contract](testing/measurement-integrity-acceptance.md).

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

## Corrections from the promotion review

These entries correct candidate behavior found while reviewing `release/2026.08`
for promotion. Where an earlier section of these notes describes the candidate
behavior differently, this section is the current one.

### Unmodified API clients keep working

- **SOAP document lists return file contents again.** `getDocumentsUpdateAfterDate`,
  `getDocumentsByProgramProviderDemographicDate` and `getDocumentsByDemographicIdAfter`
  return each document's contents inline when the request carries no
  `X-Carlos-Document-List-Profile` header, as they did before the candidate. A client
  that wants metadata only sends `X-Carlos-Document-List-Profile: metadata`.
  `legacy-inline` still selects inline contents and faults the whole page when a stored
  file cannot be read; without the header such a file is left out of the page and logged.
- **SOAP schedule synchronization never refuses a timestamp-only request.**
  `getAppointmentsUpdatedAfterDate`, `getAppointmentArchivesUpdatedAfterDate` and
  `getAppointmentsByProgramProviderDemographicDate` no longer answer with the
  "splits a shared update timestamp" fault. When a page would end between appointments
  updated in the same second, the response also carries the rest of that second, so a
  client that continues from the newest timestamp it received loses no row. A page can
  therefore hold more rows than requested, by the rest of that one second and never
  more than 5,000 rows. A client that must page through more than 5,000 appointments
  updated in one second uses `X-Carlos-Schedule-Sync-Profile: timestamp-id`.
- **REST patient lists and searches include the health card number again.**
  Demographic lists, search results, inbox items and the patients embedded in
  appointment and consultation responses carry `hin` as before. Patients the caller may
  not read are still left out of lists and totals, and SIN is still not returned.
- **Provider billing identifiers are returned without `_billing` read.** `billingNo`,
  `ohipNo`, `hsoNo` and `rmaNo` appear wherever a provider record is returned over REST
  or SOAP. The older REST rule that another provider's record is a summary unless the
  caller is that provider or an administrator is unchanged.
- **`X-Carlos-Appointment-Write-Mode` is honoured on a deployed server.** The header was
  read from the wrong request object behind the service proxy and ignored. `guarded`
  now requires `editVersion` (HTTP 400 without it), `legacy-echo` ignores an echoed
  version, and an unknown mode is refused with HTTP 400 before any update. Requests
  without the header behave as described under
  [REST appointment update compatibility](#rest-appointment-update-compatibility).
- **REST reads no longer fail with HTTP 500 while writing their audit entry.** The
  tickler list and search routes and stored demographic-set reads ran in a read-only
  transaction that the audit insert joined. The REST audit entry is now written in a
  transaction of its own, after the request's transaction has ended, so a failed or
  refused REST write also keeps its `REST WS: FAILURE` entry.

### Report limits are settings

Report by Template and the configured form, antenatal and clinical reports keep their
previous limits by default, and each limit is now a setting in `carlos.properties`
that takes effect at the next restart:

| Setting | Default | Maximum |
|---|---|---|
| `report_by_template.max_rows` | 10000 | 100000 |
| `report_by_template.timeout_seconds` | 30 | 300 |
| `report_by_template.max_html_mb` | 10 | 50 |
| `report_by_template.max_columns` | 256 | 2000 |
| `report_by_template.max_cell_chars` | 65536 | 1048576 |
| `report_by_template.max_csv_mb` | 5 | 50 |
| `configured_report.max_rows` | 10000 | 100000 |
| `configured_report.max_identifier_rows` | 100000 | 1000000 |
| `configured_report.timeout_seconds` | 30 | 300 |
| `configured_report.max_output_chars` | 5242880 | 52428800 |
| `configured_report.max_csv_mb` | 5 | 50 |

A value that is not a whole number of at least 1 keeps the default, a value above the
maximum is capped, and either case is logged once. A report stopped by a limit names
the limit and the setting an administrator can raise. A Report by Template whose CSV
is over the CSV limit is shown again, without its CSV and XLSX downloads. Raise the row
and output limits only with heap to spare: a configured report holds every row until the
page or file is written. See [report limits](test/report-template-budgets.md) and
[report query boundaries](report-query-boundaries.md). Query by Example limits are
unchanged.

Report by Template's **Export to CSV** and **Export to XLSX** are now built on the server
from the report's own result, which is kept in your session for 30 minutes (your three
most recent reports). The result page no longer carries the report's data, so a download
is always exactly what the query returned. Exporting from an expired page, or one left
open across the upgrade, shows "This report result is no longer available" with a **Run
the report again** button. Because the data no longer travels back through the web
firewall, `report_by_template.max_csv_mb` can now be raised to 50. Each user who runs
reports can hold up to three times that setting in server memory until the results
expire, more only while their newest result is a sequence larger than that by itself (with
a 5 MiB CSV limit and a 50 MiB HTML limit, a sequence of four 5 MiB CSVs keeps 20 MiB). All
users together hold at most an eighth of the server's maximum heap (256 MiB with the
packaged 2 GiB); beyond it the oldest results are dropped and their users asked to run the
report again. Downloads are never cached by the browser.

### British Columbia billing

- Administration ▸ Reports ▸ Usage Report works on British Columbia and other
  non-Ontario installs. Its Billing figure counts Ontario invoices, so there it shows
  "n/a" instead of the whole report failing with HTTP 500.
- The legacy Teleplan group export (`NEW_BC_TELEPLAN=no`) sends the claim comment in
  C02 field P58 again; the candidate sent it blank. Claims already exported blank by a
  candidate build are not corrected.
- MSP remittance and remittance summary PDFs no longer answer HTTP 500 when a stored
  Teleplan amount is malformed. The row is printed, the field is shown as an unparsed
  Teleplan amount with its source text, and totals are marked as known amounts only
  with a count of the fields left out. Payments and Refunds reports no longer list
  held (S04) lines as $0.00 rows.

### Laboratory results

A corrected report from a source other than CML is filed to measurements again even
when it carries fewer final results than the report it corrects, and the superseded
version's values are archived. Flowsheets, graphs and decision support then show the
corrected values. Corrections received by a candidate build that skipped them are not
re-filed.

For these sources the report received last is the current one, because only CML reports
carry revision evidence. If such a source re-sends an older version after a newer one, the
older version's values are filed and the newer version's are archived. Only measurements
filed for the same patient are archived, so another patient's report that shares the
accession number is left alone.

### Consultations

The team consultation list no longer comes up empty when one patient on the page is
restricted for the viewer. That patient's requests are left out with a notice, paging
counts the rows that are shown, and a list that cannot be loaded says so
([#5775](https://github.com/carlos-emr/carlos/issues/5775)).

### Chart

- A chart module that refreshes itself after a form or other window is closed no longer
  lengthens its own reload address by one copy of the chart context on every refresh. The
  address, and the links the module prints, stay the same for the life of the chart window.
- Saving a chart note no longer changes the update time of every issue of the patient. An
  issue's update time moves again only when the issue itself changes, so the issue lists that
  show what changed since a given time, and the issues the REST API returns, no longer report
  every issue of the patient as changed by each note save.

### Security and access

- The note and issue history popup prints the patient name and the issue title as
  text. Markup stored in either was previously run in the reader's browser.
- The eForm version list prints each saved form's name and subject as text, the
  patient-independent eForm lists print the saving provider's name as text, and the
  document pages print stored document sub-classes, queue names and provider names as
  text. Markup stored in any of them was previously run in the reader's browser. The
  document information shown by the Document Browser and Browse Notes leaves a missing
  acknowledging provider's name or date, annotation or tickler message blank instead of
  printing `null` ([#3057](https://github.com/carlos-emr/carlos/issues/3057)).
- Hospital reports (HRM) follow the patient's own access rules. A provider who is
  locked out of a patient, or lacks `_demographic` or `_hrm` read for that patient,
  cannot list, open, print, download or change that patient's hospital reports from
  the chart, the HRM list, the inbox or as an attachment, and the inbox sidebar neither
  names that patient nor counts the report. Reports not yet matched to a patient are
  unaffected. Before upgrading, check custom roles: a role that holds `_hrm` but not
  `_demographic` read can no longer open patient-matched hospital reports. The seeded
  roles are not affected; only `doctor` holds `_hrm`, and it holds both.
- Changing your own password from Preferences applies the clinic's password policy on
  the server as well as in the browser. A password that does not meet it, or a new PIN
  shorter than `password_pin_min_length`, is refused with the form's message and
  nothing is saved.

### eForms and printing

- Saving an eForm with a drop-down left blank no longer selects every option that has
  no `value` attribute. A choice made from such a drop-down is restored when the form
  is reopened, printed or faxed, and a multiple-selection list keeps every chosen option.
- A clinic-authored eForm that puts the instance marker `${fdid}` in the value of a named
  field (a hidden input, a text or date box, or a text area) keeps the marker when a new
  form is saved, so the reopened form shows its own id again. The candidate saved such a
  field empty, because a new form posts it blank. A value typed into the field is still saved.
- Printing, faxing or attaching a form, hospital report, message or letter no longer
  falls back to a plain-text copy because the page is deeply nested, has an element
  with very many attributes or contains a very large amount of escaped text. The print
  path now states its own XML limits instead of inheriting the JDK defaults; see
  [HTML to PDF rendering](html-pdf-rendering.md). A page that declares a DOCTYPE with an
  internal subset is refused.

### Stale forms and repeated submissions

- **Edit Appointment.** Saving a form that was opened before someone else changed or
  cancelled the appointment no longer puts the old values back. The save is refused
  with HTTP 409, a message that nothing from the form was stored and a link to reopen
  the appointment. Update, Update and Receipt, the status buttons, Cut, Delete, the
  group actions and the repeat-booking anchor are covered. A second click on a form
  whose first submission was saved ends as saved, on the repeat-booking page as well.
  On the Group Appointment page a second click on Add Group Appointment, Group Update,
  Group Cancel or Group Delete, a click while a slow save is still out, or a reload of the
  result ends on the first save's page and writes nothing again: the page carries a
  one-time submission token, so Group Update is no longer refused as another user's change
  and Add Group Appointment no longer books the ticked providers twice. A request that carries no
  `appointmentVersion` keeps the previous last-writer-wins behavior. Saving, cutting and
  deleting from the popup also apply a patient-specific denial of the appointment's
  patient (an `_appointment$N` restriction or a locked chart), as the REST API does.
- **Ticklers.** Editing a tickler, and Complete, Delete and Activate on the tickler
  lists, refuse a task someone else changed after the page was shown, name the refused
  tasks and still apply the rest. A deleted task can no longer be turned into a
  completed one from a stale list. Legacy ticklers stored without a patient can be
  completed and deleted again by users with the tickler right.
- **Messenger.** A compose page sends its message once, after a double click, a second
  click on a slow response or a reload. A sent page that is changed and sent again is
  shown back unsent with an explanation. A request without the page's submission token
  sends each time, as before.
- **Security Records.** Saving a login from Administration ▸ Security Records on a page
  opened before another administrator saved the same login no longer undoes that change
  (for example a forced password reset, an expiry, a lock or the MFA setting). The stale
  save is refused with HTTP 409, nothing in it is stored (a new password or PIN included),
  and the page offers to reopen the record as it is now. A second click on a form whose
  first save went through ends as saved, unless it carries a new password or PIN. Any save
  to the login after the page was opened counts as a change, including the user changing
  their own password and Reset MFA pressed on the same page; reopen the record to continue.
  A request without the form's `securityVersion` field saves as before.
- **Prescriptions.** When the reply to a save is lost and the prescription is edited and
  saved again, the answer is no longer "already saved". CARLOS says that the earlier
  version was saved and the changes were not (HTTP 409, code
  `RX_SAVED_EARLIER_VERSION`), and the page shows a standing notice with a button to
  reopen prescribing; nothing is written by the refused request. An unchanged repeat is
  still answered `RX_ALREADY_SAVED`, and the notice then also offers the saved
  prescription for printing. See [Rx recovery](rx/release-2026.08-recovery.md).

### Text that does not fit

- A Messenger subject longer than 128 characters, and a document description longer than
  255 (248 for a link, to which CARLOS adds " (link)"), is refused with a message instead of
  being cut short without notice. The typed text is kept where the page already kept it.
  Reply, Reply All and Forward shorten an over-long "Re:" or "Fwd:" subject in the compose
  box. Characters are counted as the database counts them, so an emoji is one character.
- The same applies to every other place a description is typed: the document viewer and
  inbox, filing an incoming document, and the HRM report viewer's Set Description refuse
  a description longer than 255 characters with a message, and nothing is saved.
- Descriptions CARLOS builds itself or receives through the API are shortened to 255
  characters at a character boundary instead of being cut by the database: annotated
  copies (which always keep " (annotated)"), eForm template documents, FHIR referral
  attachments, REST document uploads and consultation attachments (a file name used as
  the description keeps its extension), and OTN eConsult imports. API clients keep
  working, and the upload response shows the stored description.

### Audit trail

- A sign-in is recorded once, naming the facility the session was bound to. A provider
  who chooses among several facilities still has one entry for the sign-in and one
  for the choice.
- Booking, editing, cancelling, changing the status of and deleting an appointment
  are recorded under the appointment and its patient with the user and client
  address, including the Group Appt and Repeat actions. The deletion entry is now action
  `delete`, content `appointment`; it was action `Appointment.delete` with no content.
  Entries written before the upgrade are not rewritten, so a query over a period that
  spans the upgrade must match both forms.
  An edit made on the Edit Appointment popup is recorded once, as action
  `AppointmentManager.updateAppointment` (the entry an edit through the REST API also
  writes), and names a changed status. Adjust saved audit queries.
- Audit entries for allergies, prescriptions, drug reasons, reprints and chart notes
  no longer copy the allergen, drug, instructions, diagnostic code or note text into
  the audit log. They identify the record by id. Opening the chart on a new, unsaved
  note writes no entry.
- Recording, editing and deleting a prevention, entering and deleting a measurement,
  saving an Ontario invoice, opening a patient's Billing History and each run of the
  Demographic Report Tool are now audited.
- Each Query by Example run now writes one audit log entry (`read` /
  `query_by_example`) whatever its outcome: success, rejected, refused, unavailable, timed
  out, failed, disabled or history not saved. The entry names the user and records the
  query's SHA-256 hash, length, duration, row count and outcome, never the SQL or any
  result. The only record before was an INFO log line, which packaged installs (logging at
  WARN) never wrote.
- Viewing a document is recorded once, as `read`/`document` under the patient; the
  second entry with content `Document` is no longer written. An upload filed onto a
  chart is recorded under that patient.
- A refused attempt to open a locked patient's Master Record is recorded in that
  patient's audit trail as action `no right`. Such entries do not add the patient to
  the refused user's recent-patient lists.

### Packaging and database migrations

`release/2026.08` now contains `main` through 2026.09 snapshot 30. Four migrations that
had not been released were renumbered to follow the two that `main` published at the
same versions: `bc/V1.0.59.36__preserve_bc_history_amount`,
`common/V1.0.59.37__rx_favorite_groups`, `common/V1.0.59.38__rx_submission_receipts`
and `common/V1.0.59.39__rx_original_versions`. A database migrated by an unreleased
candidate build that applied them under their old numbers must be rebuilt from a
published release before upgrading. The package now depends on `carlos-ctl` 1.3.3,
which reads the format 3 OSCAR 19 schema manifest this release installs. The bundled
stock eForm asset list records the current `editControl2.js`.

Each library family now ships at one version (#5711). `httpclient5-cache` moves from 5.3.1
to 5.6.5, beside `httpclient5` 5.6.5, and `jackson-datatype-jsr310` from 2.17.1 to 2.22.4,
the version of the rest of Jackson. The text CDS import and export write is unchanged, and
a negative fractional duration now reads back exactly. The build fails when a family splits
again; see [dependency convergence](dependency-convergence.md). The jsr310 jar is part of
the CDS ruleset fingerprint, so finish or cancel CDS imports before upgrading, as for any
upgrade.

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
Messenger's Attach Patient window also offers **All current chart notes and CPP**:
every current note and the CPP from the modern chart, as the chart prints them,
without locked notes. Preview is audited like the chart print, and so is attaching.

Keep the same external file, `AbandonOldChart=true` and the original encryption
key on rollback. Restoring an older false/absent flag while retaining modern-only
writes risks presenting stale historical charts. Generating a replacement key
cannot decrypt existing encrypted data. See [startup encryption keys](startup-encryption-key.md)
and [modern chart cutover and rollback](modern-chart-cutover.md).

### JDBC and packaged installation

The public `OscarTrackingBasicDataSource.debugMap` field has been removed.
Rebuild local Java or JSP extensions that reference it. Use `isConnectionTracked(connection)`
for membership, `trackedConnectionCount()` for the current count, and
`logDebugMapToError()` to log a snapshot of acquisition traces. Code that used
`synchronized (debugMap)` for its own shared state must use its own lock.
The data source owns tracking updates and shutdown cleanup.

MariaDB Connector/J 3.5.11 replaces MySQL Connector/J. The compatibility adapter
accepts legacy driver names and MySQL-style URLs, including those still produced
by the pinned `carlos-ctl` 1.3.1 for **new** packaged installs. WAR/devcontainer
defaults use the native MariaDB driver and URL. In MySQL-style configuration,
Connector/J's `yes`/`no` values for Boolean options keep working; native
`jdbc:mariadb:` URLs follow MariaDB's own Boolean values. Configuration that requests
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
- Graphs use a third render browser of their own, so the packaged render-browser
  unit's memory ceiling rises by one browser's peak, 300 MiB (`MemoryHigh=1324M`,
  `MemoryMax=1836M`). Raise any local override of those limits to match.
- displaytag, JFreeChart, production POI and Selenium are removed from the WAR.
  `/taglibs.jsp` no longer declares `display` or `display-el` prefixes.
  Table paging/sorting is client-side, so old displaytag `d-…-p/s/o` bookmarks
  no longer select a page or ordering. Migrate local JSPs, plugins and reports
  before deployment; restoring removed jars alone is not a supported migration.

The [Java API migration guide](upgrade-2026.08-java-api.md) lists the original
29 changed types plus subsequent removals, replacements and behavior contracts.
It also covers object diagnostics/equality, audit text and JavaScript encoding.

### HTTP responses, permissions and validation

- REST demographic and pharmacy lists reject negative offsets or limits, and
  limits above 500 patients or 5000 pharmacies, with HTTP 400. Clients that used
  `limit=-1` or `offset=-1` must send a supported page size and a nonnegative
  offset instead. See the [REST list contract](api-collections-index.md#rest-list-pagination-and-demographic-validation).
- REST demographic creation and update reject over-length values in fields
  checked by `Demographic.validateFieldLengths()` with HTTP 400 and a `text/plain`
  body naming the fields and limits. Checked fields include names, pronouns,
  gender, title, addresses and contact details. JSON/XML clients
  must handle this plain-text error response; submitted patient values are not
  included.
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
