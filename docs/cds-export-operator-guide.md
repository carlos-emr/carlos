# CDS export operator guide

Use **CDS Export** at `/form/cdsExport` or the paired `carlos-ctl cds-export`
command. Both call the same durable application services. The implemented export
profile is `omd-5.2` (OntarioMD CDS 5.2). PCON is not an advertised export
profile. Export admission defaults off.

Read the [implementation contract](cds-export.md), [standards review](cds-export-spec-review.json)
and [release process](release-process.md) before enabling a deployment. Passing
XML validation does not establish clinical completeness or OntarioMD certification.

## Prepare the deployment

1. Install the matching application and CLI candidate, apply the normal forward
   Flyway migrations, and retain database, document and durable CDS storage backups
   together. Use Java25 and the same frozen application/dependency/timezone bytes
   for web and CLI work. An accepted job records those compatibility bindings.
   The CLI uses a minimal `C.UTF-8` process locale and sets Java's default time
   zone to the configured clinical zone. Give the web JVM the same effective
   locale and default zone; different runtime bindings prevent shared recovery.
   Original import fingerprints and metadata must never be rewritten to force
   compatibility. After an upgrade, a READY package stays downloadable until it
   expires; it does not depend on the build that made it. A job that was queued or
   still running when the export implementation changed ends in review with
   `CDS_EXPORT_RUNTIME_CHANGED` and must be submitted again. Rebuilding identical
   source (a new build date or build number only) does not change the binding.
2. Keep `cds.export.enabled=false` until the deployment's permissions, policies,
   storage and recipient-tool checks are complete. Import enablement is independent.
   Export does not require enabling import or entering an import maintenance window.
3. Grant the acting provider/account the independent export permission or current
   administration authority, demographic reads and every selected clinical-domain
   and patient-specific read. The ledger migration seeds the administrator export
   permission; it preserves an existing customized grant/denial on replay.
   A saved job ID or Unix root identity does not replace clinical authorization.
   Selecting Laboratory Results also requires read access to `_lab_sensitive`,
   which no role receives by default, selecting ProblemList requires read
   access to `_dxresearch` (the Disease Registry), and selecting Reports requires read
   access to `_eform` (Reports carry every eForm of the chart; the doctor role has it by
   default). Admission checks every selected category's
   role-level right and refuses a missing one by name (CLI exit 3, browser
   "Export refused" notice naming the right) before anything is accepted.
4. Configure `cds.export.clinicalZone` explicitly and consistently with the clinic
   and `cds.import.clinicalZone`. Configure protected selection, retained import,
   export-store and master-key roots. Store/key IDs are immutable bindings for
   accepted jobs; retain the original configured roots/keys while their artifacts
   remain available. Never rotate a missing or invalid existing key as a recovery
   shortcut. An installation that has imported CDS charts also needs the import's
   native-baseline witness key files (`cds.nativeBaseline.keyRoot`, see the import
   configuration) to re-export them; without them the job stops with
   `CDS_EXPORT_NATIVE_BASELINE_KEY_UNAVAILABLE`.
5. Configure the root-controlled sender, recipient certificate and audit arrangement
   under `cds.export.policyRoot`. The exact manifest/file hashes, recipient pin,
   validity, scope and approved audit text are rechecked at disclosure boundaries.
   See [registered policy](cds-export.md#registered-disclosure-policy) for file
   shapes and ownership requirements. Request-supplied certificates/paths are not
   accepted.
6. Establish a usable arrangement for complete historical patient audit access
   after migration, including information outside the exported CPP. Record its
   actual access instructions and approved provider/recipient/profile scope in
   the audit registration. Confirm the recipient can use it and agree how long it
   remains accessible. The registration verifies an approved arrangement and
   binds its bytes; software cannot independently prove an external archive or
   support agreement is available. The export carries each patient's current
   demographic snapshot and enrolment history only. Superseded demographic values
   (earlier names, health cards, addresses, phone numbers, physicians, extension
   values, alerts and patient notes kept in `demographicArchive`,
   `demographicExtArchive` and `demographiccustArchive`) are not exported, and this
   arrangement is where they remain available.
7. Verify the receiving recipient tool authenticates CMS AES-256-GCM with RSA-OAEP
   SHA-256/MGF1-SHA-256 and opens the exact resulting ZIP. The recipient keeps its
   private key. The application stores only the registered public certificate.
   Confirm the receiving importer accepts the chosen profile/package before a
   production transfer.
8. Enable admission when those deployment prerequisites and the candidate's
   executed release checks are satisfied. Leave the online worker enabled for
   browser/detached work. A paused or absent compatible web worker leaves a
   durable job waiting; detached acceptance does not mean completion.

The database source gate needs full schema visibility to detect unreadable or
unreviewed sources. Capturing the stock nontransactional Rourke table additionally
requires its bounded READ lock. A missing permission or unknown populated source
requires review; deleting clinical rows or weakening the gate is not a resolution.
Catalogue matching follows MariaDB's existing table-name case mode. Column names, types,
collations and keys remain exact, and unknown or ambiguous table mappings still require review.

Document reports read their files from the configured `DOCUMENT_DIR`. That directory,
each of its parent directories and every directory and file below it must be the real
directory or regular file (never a symbolic link), owned by `root` or the account the
export runs as, and not writable by group or others. Configure `DOCUMENT_DIR` with its
real path: a path that passes through a symbolic link, such as the packaged
`OscarDocument` compatibility link or a symlinked data volume, or a group-writable parent
stops the whole job for review with `CDS_EXPORT_DOCUMENT_STORAGE_UNTRUSTED` before any
document is read. A problem with one document holds only its patient:
`CDS_EXPORT_NATIVE_REPORT_FILE_MISSING` (the file the document row names is not there, for
example after a purge), `CDS_EXPORT_NATIVE_REPORT_FILENAME_UNSUPPORTED` (a stored name
that is absolute, leaves the directory, or contains a colon, backslash or control
character) and `CDS_EXPORT_NATIVE_REPORT_FILE_TOO_LARGE` (larger than
`cds.export.maxDocumentBytes`, 1 GiB by default).

Each patient's file is in a folder named for the patient's primary physician (MRP) and
billing number. A physician with no billing number recorded keeps a folder with the name only,
and patients with no primary physician are in the `Unassigned` folder; neither holds a patient.

## Submit and recover a browser job

Open the normal authenticated CDS Export page. Choose the authorizing provider,
optional facility, either the primary-provider/MRP roster or explicit patient
numbers, the clinical categories, registered recipient and approved audit
arrangement. Demographics are always selected. Roster selection includes every
patient status; it is independent of enrolment.

Keep the generated submission key. Submit once. HTTP 202 confirms an immutable
durable request; processing continues online after the response. Reload or reopen
the saved job ID, or use **Check submission** with the original key after a lost
acknowledgement. Reusing that key cannot create another job with different intent.

Only **Transfer package ready** with complete patient counts and an available
artifact permits package download. The POST download rechecks current clinical
and disclosure authorization. A stale/offline status disables controls until the
server confirms the job again. Download diagnostics separately when a job needs
review; a diagnostic report is not a clinical package.

Cancellation and resume use the observed revision. Lease-only heartbeats preserve that revision;
state, progress and control changes still invalidate stale observations. If a control outcome cannot
be confirmed, refresh the original job before retrying. Logging out or closing
the page does not cancel an accepted export.

## Use the command line

Run as an authorized Unix operator through sudo. Root-issued delegation is bound
to the exact arguments, and the provider still needs current clinical permissions.

Every value the demographic screens write is exported; none holds the patient. The patient
alert (the Alert box) is exported as an AlertsAndSpecialNeeds item when that category is
selected. Details CDS 5.2 Demographics has no place for (preferred name, alias, pronoun, gender
identity, the referral doctor, the nurse, resident and midwife, the "Date Joined" and health card
"EFF Date", ethnicity, consents (including "No Consent" on a contact or pharmacy, which is an
instruction not to contact them), paper-chart status, First Nations details, a site's own extra
fields, and a phone extension or mobile number too long for the CDS phone element) travel exactly
in one NewCategory record per patient,
`CARLOS.NativeDemographic.v1`. Select NewCategory to send them; without it they are not
exported and the ReadMe says so. A care-team provider is sent with the provider number and the
provider's public name. Only a value containing a character no XML file can hold still holds the
patient; correct it on the demographic page and export again.

The same holds for roster, pharmacy and contact values: a fee-for-service or clinic-defined
roster status, a roster entry missing its date, a second preferred pharmacy, Phone 2 or Notes on
a pharmacy, an inactive or internal contact, an e-mail with a "+", a SIN typed with spaces and a
province of "Other" all travel in the `CARLOS.NativeDemographic.v1` record instead of holding the
patient. A specialist or dietician on the health care team (an external contact) is exported as
a Demographics Contact whose purpose is the role ("Specialist", "Dietician"). Removed pharmacy
links and deleted contacts are no longer in the chart and are not exported. A patient
re-rostered without a termination is exported with the current enrolment; the earlier episode's
snapshot stays in the audit arrangement. A roster entry missing its date is not written as an
enrolment: the OntarioMD guide requires the date and CARLOS's own importer refuses one without
it.

A chart that came from an earlier CDS import and was then edited in CARLOS is exported like any
other chart: the patient moved (a new city or postal code on an imported Formatted address, one
with several street lines, or two addresses of one type), a new mobile number where the imported
one had an extension, a phone of a type the file had twice, edited middle names, a value or date
the import kept only in the file (a time-zone offset included), an edited patient note that was
too long to import, re-rostering, a move to fee for service or another physician. The current
CARLOS value is exported and the imported value it replaced stays in the import's protected
source fields, listed as replaced in the export audit; it is not in the package. Unedited
imported charts export exactly as before, and a save that leaves an imported-only value blank
does not erase it. An imported enrolment that CARLOS no longer shows (the patient was moved to
fee for service, non-rostered or another roster status, or re-rostered to another physician,
without a termination) is not exported as a current enrolment: like an earlier roster state on
any chart, it stays in the import's protected source fields and the audit, and the file carries
the current state instead (the new enrolment, or the fee-for-service state in the NewCategory
record). Closed imported enrolments, an imported enrolment that is still current, one ended
with a termination in CARLOS, and the whole history of a chart whose roster was not edited
export as imported. An imported contact deleted in Manage Contacts is not exported, and one
marked Inactive travels in the native demographic record, as for any chart; a person deleted and
added again, or added again with a second relationship, is exported as one contact with the
current relationships. An imported preferred pharmacy removed on the
prescription pharmacy screen is not exported, and when another pharmacy was made the first
preference that pharmacy is exported as PreferredPharmacy and the imported one travels in the
native demographic record. In each case the imported values stay in the import's protected
source fields, listed as replaced in the export audit. Still held: a value with a character no
XML file can hold, a second relationship added to an imported contact whose file gave it
several purposes (CARLOS keeps only a placeholder for them), and an imported chart that matched an
existing CARLOS chart whose values the import cannot prove.

Native chart support includes demographics/external physicians, all seven history/note
categories, prescriptions and printed scripts, current measurements, supported
HL7 ORU results, and document reports with their attachments. A document is exported as
the chart shows it: an uploaded or scanned document as its file, declared with the type its
stored name or content type gives when the file's bytes bear that out (otherwise as the type
the bytes show, or `bin`), and an Add HTML or Add Link document as its stored HTML text. A
deleted document, or one whose patient link was deleted, is not exported and does not hold
the patient. The package does not say how a document was received (Media), because CARLOS
does not record it. Every review of a document is exported as a ReportReviewed with the
reviewer's name, OHIP number (six digits only) and review date: the first reviewer, each
later reviewer, each review record and each provider who acknowledged the document in the
inbox; the same provider reviewing on the same day is written once. The element holds a date
only, so the exact review times, and every inbox comment on the document, are kept in the
report's Notes. Unacknowledged and unclaimed inbox routes are not exported. The reviews of a
deleted document are not exported either, like the document. A document that came from an
earlier CDS import is exported from its imported elements, unchanged; a review recorded in
CARLOS afterwards (acknowledging it in the inbox, with or without a comment, or a review
record) is added after the sending EMR's reviewers as one more ReportReviewed. The review rows,
with their exact times and comments, are added to the report's Notes when the imported report
has none; when the sending EMR already wrote Notes, which are never rewritten, they travel in a
separate Text report, "CARLOS reviews of an imported report", beside it. A Notes text over the
32,000 characters CDS allows holds the patient
(`CDS_EXPORT_IMPORTED_REPORT_REVIEW_NOTES_CAPACITY`). An imported document reviewed or
edited through the document's edit page is exported too: the review is one more
ReportReviewed, an edited class, subclass, source author or facility, observation date or
received date is exported as it now reads, and every edited value (description and type
included) travels with the value it replaced in the same Notes or separate report. When the
file of an imported document was replaced on the edit page, the current file is exported as the
report's content, and the name, type and SHA-256 fingerprint of the file the import brought
travel with it. The imported file itself is still in CARLOS's document directory (the edit
page does not delete it) but is not put in the package, since the chart no longer shows it.

A document restricted to a program is exported when the exporting provider, and the operator
when one runs the export, both work in that program (are members of it under Program
Management), which is who the chart shows it to; a document flagged as restricted but without a
program is shown to everyone and exported. When either does not work in the program, the
patient is held with `CDS_EXPORT_DOCUMENT_PROGRAM_SCOPE`: add the provider to the program, or
run the export as a provider who works in it, and export the patient again.

Hospital Report Manager (HRM) reports filed to a chart are exported as Reports entries and no
longer hold the patient. Each report is exported from its stored HRM message as the report
viewer shows it: the report text, or the attached file (PDF, image) of a binary report; its
class and subclass, event and received dates, author, sending facility and facility report
number, accompanying observations (OBR content), result status, message ID and recipient.
Each provider sign-off with a sign-off time is a ReportReviewed with the provider's name, OHIP
number and date; the exact sign-off times, the comments on the report (with their authors'
names), its subclasses and the details CARLOS keeps about it (description, category, duplicate
count, the patient identity the report named) are in the report's Notes. A deleted comment and
unsigned inbox routing are not exported. A report whose stored message file is missing, or not
recorded, is still exported from what CARLOS recorded about it (class, subclass, dates, facility
report number, observations), with a Notes statement that its content could not be read; a
stored file that is not a readable HRM message is attached exactly as stored (`xml`). Only an
unusual link holds the patient: a link whose chart or report number is spelled other than
CARLOS writes it (`CDS_EXPORT_HRM_REPORT_LINK_REQUIRES_REVIEW`), a message holding more than one
report (`CDS_EXPORT_HRM_REPORT_MULTIPLE_REPORTS_REQUIRES_REVIEW`), a sign-off state CARLOS never
writes (`CDS_EXPORT_HRM_SIGN_OFF_STATUS_REQUIRES_REVIEW`), or more report details than Notes can
carry (`CDS_EXPORT_HRM_REPORT_METADATA_CAPACITY`). HRM files are read from `DOCUMENT_DIR` under the
same storage rules as document files. HRM reports not yet matched to any chart are still left
out and counted as unlinked inbox rows (see below).

Revised notes retain
the original and edits in one clinical occurrence. A clinical note whose latest
revision is archived (removed from the active record) is still exported, because the
encounter list still shows it, but its text begins with a statement that it is
archived and not current: ClinicalNotes has no status field, and the receiving
importer, CARLOS included, files it as an ordinary note carrying that statement. An
archived family history, past health, problem, risk factor or alert item likewise has Notes
that begin with a statement that it is archived and not current; its archive state also
travels in `CARLOS.History.Archived.v1`, from which a CARLOS importer restores it. A
history item longer than its description element (250 characters, 120 for a risk
factor) has no description; its complete text is in Notes, and also in the versioned
`CARLOS.History.Narrative.v1` residual (whitespace escaped), from which a CARLOS
importer restores the item's exact text. Personal history, which CDS allows only as
residual data, travels in the same carrier, so its line breaks survive; other
schema-aware receivers collapse whitespace in residual content.

Note access is decided per note, in the note's own program, because an export has no
selected chart program. A note filed under a program needs the provider's single
membership in that program, the note role's read grant (same role, role default or
program grant) and, when facility filtering is on, the export's facility. A note
filed with no program (`program_no` empty or `0`, written by a provider without a
program) needs only the patient's program domain, as in the chart when no program
is selected. The chart opened with a selected program applies that program's role
grants to every note and hides role-less unscoped notes, so the export can include
notes such a chart view hides. A patient issue filed under a program is exported when
it is the program of its linked notes or one the provider works in; an issue from a
foreign program holds the patient for review.

A note that CARLOS attached to another record is exported with the other notes, and says
what it is attached to. CARLOS attaches a note when a document is filed ("Document ...
created at ..."), a prescription is discontinued (the reason), an eForm or a tickler is
saved with a note, or an e-mail is sent; a CDS import made by an older OSCAR release
attached its data notes (text starting `imported.cms4.2011.06`) to other notes, allergies,
prescriptions, preventions or the patient record. Each such note is exported once, as the
encounter shows it: a ClinicalNotes entry whose text begins with a line such as
`[CARLOS: this note is attached to another record in the sending system: document 12.]`,
or, when the note is filed in a CPP section, that CPP item, which keeps the same facts in
its supporting data. The record type and number of every revision's link travel exactly in
the note's source details. The note is not copied into the allergy, medication, lab or
report it is attached to: it stays under the note's own program access rules. Charts with
such notes are no longer held. A link whose note no longer exists, or whose note names a
patient number with no chart, still holds every chart for review.

A CAISI group note is exported once, with the chart it was written in, never with the
other charts it was shared with. In a recipient chart the shared note is a read-only
display of another chart's note (group notes often name the other members), so it is not
part of the recipient's package, and sharing a note no longer holds the recipient chart.

Choose all seven history/note categories together: PersonalHistory, FamilyHistory,
PastHealth, ProblemList, RiskFactors, AlertsAndSpecialNeeds and ClinicalNotes. Select
NewCategory when exporting native measurements/labs or printed prescription context.
It carries generic observations, complete original lab messages and printed script
history. Deleted measurements (a clinician's deletion or an HL7 result superseded by a
newer version) are not exported, as with the legacy exporter. Lab results that CARLOS
copies into the measurement flowsheets travel only with their HL7 report under
LaboratoryResults, never as care elements: without LaboratoryResults they are not exported.
With it, a copied result whose report is not routed to the patient holds the patient
(`CDS_EXPORT_NATIVE_CARE_LABORATORY_RESULT_UNROUTED`); correct the lab's patient match
and export again. Excluding a required supporting category requests review; the exporter
does not silently expand the accepted selection.

A lab's inbox acknowledgements are exported. Every provider who acknowledged an HL7 report
(Acknowledge in the inbox or lab view) appears as a ResultReviewer on every result of that
report, with the provider's name, the OHIP number when the provider record holds a six-digit
OHIP billing number, and the acknowledgement date and time. An acknowledgement or inbox
comment is never lost: the routing row that carries it travels whole in the report's
NewCategory supporting record (`CARLOS.NativeLaboratory.v1`), with its provider number,
status and time. Routes that are new, filed without acknowledging, removed from an inbox or
waiting in the unclaimed inbox are inbox workflow and are not exported. A route with a
status CARLOS does not write (anything but new, filed, removed, acknowledged or blank) or an
acknowledgement by a provider number with no provider record holds the patient. A lab that
came from an earlier CDS import is exported from its imported elements, which keep the
reviewers the sending EMR recorded; a provider who acknowledged it in CARLOS is added after
them as one more ResultReviewer, and an inbox comment on it travels whole in a NewCategory
record (`CARLOS.ImportedLaboratoryReview.v1`), so NewCategory must be selected for such a
chart.

Lab feeds are exported as CARLOS received them. A structured numeric result (such as ">60"
for eGFR, or a titre "1:20") is the result text with its unit. An embedded document (the PDF
pathology, cytology and histology reports LifeLabs sends through Excelleris) is a Lab Report
under Reports, attached as the decoded file, with the report's accession number in its Notes
and the report's inbox acknowledgements as its reviewers; select Reports with
LaboratoryResults, or the patient is held. A time the laboratory sent only to the minute or
hour is exported as its date (CDS requires seconds, which are never invented); the exact time
is in the NewCategory record. An ordered test with no result yet (a pending, cancelled or fee
line) adds no result and travels only in that record. A result the laboratory sent without a
collection time (no OBX-14 or OBR-7, as in Epsilon's general results) is exported with the
report date the chart lists for that lab, at the precision the laboratory sent it, and the
NewCategory record says that the message carried no collection time and which field the date
came from. Only a report the chart has no date for at all would still hold
(`CDS_EXPORT_NATIVE_LAB_COLLECTION_REQUIRED`); CARLOS's lab upload never stores one. A
malformed structured numeric still holds the patient
(`CDS_EXPORT_NATIVE_LAB_VALUE_COMPONENTS_REQUIRES_REVIEW`), as does a message CARLOS's own lab
handlers could not have stored (for example "ORU" without its R01 trigger:
`CDS_EXPORT_NATIVE_LAB_MESSAGE_REQUIRES_REVIEW`).

Vaccines export under Immunizations. Prevention screenings (the Screenings types and
OtherB) export only under PastHealth, so a package without the history categories carries no
screening history; its ReadMe says so. Superseded or removed prevention versions are not
exported: when a prevention is edited, CARLOS keeps the earlier version as a deleted row, and a
deleted prevention is also only flagged. Only the current version of each prevention is sent,
as the legacy exporter did, and the earlier versions do not hold the patient for review.

A prevention restricted to a program (data from OSCAR 19 or CAISI; CARLOS itself never sets
one) follows the document rule: it is exported when the exporting provider, and the operator when
one runs the export, both work in that program (are members of it under Program Management). A
prevention flagged as restricted but without a program, or explicitly unrestricted, is exported
as any other. When either does not work in the program, or the restriction flag holds a value
other than 0, 1 or empty, the patient is held with `CDS_EXPORT_PREVENTION_PROGRAM_SCOPE`: add the
provider to the program, or run the export as a provider who works in it, and export the patient
again. The flag and program travel with the exported entry in its CARLOS residual.

A vaccine imported without a configured ImmunizationType is filed in CARLOS as `OtherA`
("Other immunization or injection"). It is exported as an immunization, from its original
imported elements. With the shipped prevention configuration `OtherA` is itself classified as an
immunization (see below); a configuration that files `OtherA` elsewhere relies on the import's
own sealed receipt, which proves where the row came from and keeps it out of a PastHealth-only
export (it is a vaccine, not a screening) without holding the patient. There, if the receipt
cannot be verified (an import from before native-baseline witnesses, or a receipt that no longer
matches), the patient is held. If the vaccine's type was changed after the import, the patient
is held. A receipt that cannot be opened because the native-baseline key is missing
stops the job with `CDS_EXPORT_NATIVE_BASELINE_KEY_UNAVAILABLE`: restore the key files
([native-baseline witness key](cds-import-configuration.md#native-baseline-witness-key)) and
resume.

A vaccine that came from an earlier CDS import and was then edited in CARLOS is exported as the
chart shows it now: CARLOS keeps the imported entry as an earlier version, and the current version
is exported like any vaccine recorded in CARLOS. The imported values stay in the import's
protected source fields, listed as replaced in the export audit. An imported vaccine deleted in
CARLOS is not exported, as any deleted prevention is not. Neither holds the patient.

An `OtherA` entry recorded in CARLOS itself ("Other immunization or injection") is exported as
an immunization without an ImmunizationType, with its name, lot, route, site and dose; an entry
recorded without a name is named "Other immunization or injection". The CARLOS importer files
such an entry as `OtherA` again. This applies when the prevention configuration files `OtherA` as
the shipped one does (an injection item under the Other heading). Another injection item without
a declared type, or an `OtherA` your configuration files elsewhere, still holds the patient with
`CATEGORY_NOT_PROVEN`. Such items exist only if the server's prevention file (`PREVENTION_ITEMS`)
was changed; no CARLOS screen creates one. Declare a `healthCanadaType` on that item of the file
(ImmunizationType is written only for a CT-020 value) and export the held patients again in a new
job. An Rh immune globulin injection recorded with the Rh Immune Globulin form is exported as an
immunization named "Rh immune globulin", with its lot, route and site; its product, dosage and
reason are in the CARLOS residual.

Disease Registry entries and episodes (pregnancies included) export under ProblemList, after
the Ongoing Concerns items. Before this release any chart with either was held. An active or
resolved registry entry is sent with its code, the code's description from the ICD-9, ICD-10,
ICHPPC or MSP code table, its start date at the precision the registry shows (a year, a
year-month or a full date) and, when resolved, the day it was resolved. A current or completed
episode is sent with its dates, code, description and notes. Registry entries deleted from the
registry and episodes saved as Deleted are not exported and do not hold the patient. A code the
code table does not list, or from a system without a code table here (the SNOMED CT pregnancy
codes), is still sent with its code and no standard description. A code its table describes on
more than one row (the bundled BC MSP table continues some long descriptions on a second row) is
sent with each description, in table order, joined by " / ". An entry saved without a start date
is sent without an onset date. Two cases hold the patient: an entry or episode with a status
CARLOS does not write (`CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_STATUS_REQUIRES_REVIEW`,
`CDS_EXPORT_NATIVE_EPISODE_STATUS_REQUIRES_REVIEW`), and a start date or precision the registry
cannot show (`CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_DATE_REQUIRES_REVIEW`,
`CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_DATE_PRECISION_REQUIRES_REVIEW`,
`CDS_EXPORT_NATIVE_EPISODE_DATE_REQUIRES_REVIEW`). A diagnosis that is both an Ongoing Concern
and a registry entry is two records in CARLOS and is sent as two ProblemList items. Selecting
ProblemList requires read access to `_dxresearch` (the Disease Registry), which by default only
the doctor role has; admission refuses a missing one by name.

A prescription's reasons (indications, the Reason link on the Rx page) are exported with it.
Every reason the chart shows travels in full in the CARLOS extension
`CARLOS.Medication.Reasons.v1`: its coding system, code, comments, primary flag, the provider who
recorded it and the date. The standard ProblemCode holds a single ICD-9 code with no coding
system, so it carries the code of the reason that is the prescription's indication: the reason
marked primary, or the only reason when none is marked (the Rx page does not mark the reasons it
records). It is left out when several reasons compete, or the code is not ICD-9 or does not fit
its ten characters; the reasons are still in the extension. Archived reasons are shown nowhere in
CARLOS and are not exported; they do not hold the patient. On a medication that came from an
earlier CDS import, the sending EMR's ProblemCode is kept as it was and reasons recorded in CARLOS
are added beside it. A reason whose patient differs from its prescription's patient, or whose
prescription no longer exists, holds both charts (`CDS_EXPORT_AMBIGUOUS_SOURCE`); CARLOS does not
write such a reason, so it means the data was changed outside CARLOS and needs correcting.

Database-selected prescriptions require a working configured `drugref_url`. Public
DIN qualification sends the DIN and lookup option only. The clinician's authored name
and complete compound-strength text remain intact; the standard strength uses the
database's first ingredient. A prescription whose DIN DrugRef no longer lists (a product
removed from the installed drug dictionary), or a catalogue pick saved without a DIN, is
exported without the DIN element, with its name and strength as written and the stored
identifier in the CARLOS residual; it no longer holds the patient. A malformed or mismatched
reference answer still requires review, and an unavailable reference service produces an
explicit failure. Inspect diagnostics and restore that dependency before resuming; no DIN-free
fallback hides an outage.

Ordinary prescription, allergy and CPP entries no longer hold a patient when the screen saved
values a CDS element cannot hold: "Past medication" Yes beside status Active (both are sent as
saved); a treatment type that disagrees with the short-term box; the as-needed box without a
frequency (sent as frequency `PRN`); a dispense interval over 10 or a protocol over 20 characters
(left out of its element, kept in the CARLOS residual); a prescription recorded for an outside
provider (sent without a prescriber; the outside name and number are in the CARLOS residual); an
allergy reaction over 120 characters
(sent in full in the allergy's Notes); a brand allergy saved without a DIN; a CPP item with two
or more assigned diagnoses (all listed in its Notes, none in the single code element); a
free-text Age at Onset or an over-long Treatment, Exposure, Relationship or Problem Status (in the
item's Notes). An item imported from a CDS file and then archived, edited, given a new issue or
CPP date, or (for an encounter note) given a new observation date in CARLOS is also exported. An
imported CPP item moved into another CPP section is exported under its new section, with the
elements of its imported record kept in the CARLOS residual. An allergy reaction over 120
characters is also sent in a CARLOS residual that a receiving CARLOS restores in full.

A protected UTF-8 selection file directly inside the configured selection root
has this shape:

```json
{"version":1,"patients":[27,28]}
```

Submit selected patients in the foreground:

```sh
sudo carlos-ctl cds-export --provider 123456 \
  --patients-file /etc/carlos-emr/cds-selections/transfer.json \
  --profile omd-5.2 --categories Demographics,AllergiesAndAdverseReactions \
  --recipient registered-recipient --audit-plan approved-audit \
  --submission-key transfer-2026-11-08
```

Replace the example provider, protected path and registered identifiers with
approved deployment values. To discover a physician roster, replace
`--patients-file ...` with `--physicians 123456,234567`. Omit
`--categories` to request all sixteen categories. Add `--detach` to return
after durable acceptance and let a compatible online worker continue.

Existing jobs keep their original intent:

```sh
sudo carlos-ctl cds-export --provider 123456 --status-submission transfer-2026-11-08
sudo carlos-ctl cds-export --provider 123456 --status RUN_ID
sudo carlos-ctl cds-export --provider 123456 --report RUN_ID
sudo carlos-ctl cds-export --provider 123456 --resume RUN_ID
sudo carlos-ctl cds-export --provider 123456 --cancel RUN_ID
sudo carlos-ctl cds-export --provider 123456 --download RUN_ID \
  --artifact package --output /var/lib/carlos-emr/recipient-delivery/transfer.cms
```

Replace `RUN_ID` with the saved UUID. The download directory must already be a
private service-owned directory that passes the protected-directory checks. The
destination must be new; an existing file is refused unchanged. The CLI waits in
the foreground unless detached. Stopping a wait does not request durable
cancellation. Resume is subject to the original binding and current authorization.

| Exit | Meaning |
| --- | --- |
| 0 | Completed foreground export, or a successful observation/control/detached acceptance. Read the durable result; these are different outcomes. |
| 1 | Invalid/conflicting arguments. |
| 2 | Configuration/runtime failure or an unconfirmed result (`CDS_EXPORT_UNCERTAIN`); inspect the original durable job. `CDS_EXPORT_NOT_STARTED: <code>` means this command did not submit or change any job (for example a missing `cds.export.clinicalZone` or an unknown submission key). |
| 3 | Authorization denied. `CDS_EXPORT_DELEGATION_NOT_CONFIGURED` means the operator has no `cds.export.operator.<user>.providers` delegation; `CDS_EXPORT_DELEGATION_DENIED` means the delegation does not cover the requested provider or facility; `lack read access to _object` names a role right the provider is missing; `CDS_EXPORT_AUTHORIZATION` is any other current permission refusal. |
| 4 | Review, failed or incomplete foreground work, or unavailable artifact. |
| 5 | The package is ready but leaves out held patients; it is not a complete migration. |
| 130 | Waiting interrupted (Ctrl-C); durable cancellation was not requested. The lease is released before the process exits, so the job can be resumed at once. A SIGTERM-stopped wait exits 143 with the same meaning. |

### Held patients

A selected patient whose chart this release cannot represent completely (an
unmapped value, a chart that changed during capture, access restricted for that
patient) is held back: it is left out of the package and named, never silently
omitted, and the other selected patients are still exported. The job reaches
READY with a nonzero held count; the browser labels it "Transfer package ready
without N held patient(s) — not a complete migration", and the CLI prints
`CDS_EXPORT_PATIENTS_HELD` and exits 5. The diagnostic report (`--report`, or the
browser's report download) lists each held chart number with its safe code, and
the encrypted package ReadMe lists the same patients for the recipient. Correct
or review those charts and export them in a new job. A failure that concerns the
whole job (storage, recipient policy, audit arrangement, limits, runtime change)
still stops it, and a job in which every patient was held produces no package.

A provider record's format never holds a patient. A billing number (`ohip_no`) that is not
exactly six digits (a BC MSP number, a placeholder, a longer number or one with spaces) is
exported as no OHIP number; the provider is still named. A practitioner number with no
"Practitioner No. Type", or a CPSO number with spaces or more than six characters, is not sent
as PrimaryPhysicianCPSO. Receivers use the CPSO number to match the physician, so set the type
and number on the provider record (Administration > Providers) before exporting if you want
them sent. Only a provider name with a control character no CARLOS screen can enter still holds
the patients that name it.

A single chart with more measurements than one export holds
(`cds.export.maxNativeRows` observations, default and maximum 8,192, or
`cds.export.maxIndexBytes` of captured measurement rows, default and maximum 64 MiB)
is held with `CDS_EXPORT_NATIVE_CARE_PATIENT_LIMIT_EXCEEDED`; the other patients are
still exported.

An appointment that came from an earlier CDS import and was later deleted, or cut to be
pasted elsewhere, is not exported, as the day sheet no longer shows it; CARLOS's archive copy of
the booking is what proves the removal, and a booking that disappeared without one still holds
the patient (`CDS_EXPORT_APPOINTMENT_DELETION_REQUIRES_REVIEW`). A reason-list item chosen
later for an imported appointment is written before its imported purpose, as the day sheet
shows it. When the label and a long imported purpose do not fit the 250 characters CDS allows
together, the imported purpose is kept whole and the label is written as the first line of the
appointment notes (`Reason: <label>`); neither is cut.

A care value that came from an earlier CDS import and was later deleted in the
chart (the way CARLOS corrects a measurement) is not exported, as any deleted
measurement is not; the corrected value recorded afterwards is exported like any other
measurement. The imported value stays in the import's protected source fields, listed
as replaced in the export audit (accounted as removed when every value of that imported
care entry was deleted), and the patient is no longer held.

Every chart the export read is in the normal chart audit (`log`): an `EXPORT`
row for each patient of a prepared package, and a `read` row marked
`CDS_EXPORT_CHART_ACCESS ... outcome=HELD` for a held patient or `outcome=STOPPED`
for a job that stopped after reading the chart. The job outcome itself stays in
the export ledger. A failed, refused or cancelled job's staged files are released
to the retention sweep at once (within about a minute while the online worker
runs), not kept for the attempt's seven-day ceiling.

A source the export cannot close for any patient (a changed pinned table, or a
populated must-be-empty table with no patient link) stops the whole job with
`CDS_EXPORT_CLINIC_SOURCE_UNREVIEWED`, once, instead of holding every patient. A
row in a patient-linked must-be-empty table holds only that patient
(`CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED`). Either way the blocking table names
are printed on the technical channel (CLI standard error, web application log)
as `CDS_EXPORT_SOURCE: {"code":...,"tables":[...]}`, with no row values.

Chart records that CDS 5.2 has no element for (encounter forms, eForms,
consultations and the earlier versions CARLOS keeps of each edited consultation request, ticklers, e-mails, faxes, messages, consents, the cumulative
patient profile, the patient photo and similar) do not hold a patient. When
Reports is selected, each record is exported as a Reports entry holding a PDF
"CARLOS data summary": every stored field of the record and its related rows
with the exact value, clearly marked as not an original document. The one
exception is the password and password hint of an encrypted e-mail: the summary
says a value is held but never prints it. Each saved
encounter form is one record, labelled "revision n of m" (Notes and the summary
title add "saved <time>" when the revision stores its save time). The ReadMe counts each
patient's data summaries by kind.

Two kinds of record also travel as the clinician prints them, as a second Reports
entry beside their data summary (which stays the complete record):

- each consultation request: the consultation letter CARLOS prints (Report class
  Other Letter, SubClass "Consultation request letter", the referral date and the
  referring provider);
- the current (latest saved) Rourke Baby Record 2017 or 2020 revision: its four
  printed pages (Report class Medical Records Report, SubClass "Rourke Baby Record
  2017"/"2020", the form date and the form's provider). Earlier revisions stay data
  summaries only.

The printed entry is made only when the exporting provider (and the operator, for a
delegated export) holds read rights on consultations (`_con`) or forms (`_form`) for
the patient; without them the record still travels as its data summary. A printed
form that cannot be produced never holds the patient: the data summary's Notes say
"could not be produced" with a reason category (`NOT_AUTHORIZED`, `TEXT_OVERFLOW`,
`UNSUPPORTED_CHARACTER`, `FONT_UNAVAILABLE`, `FORM_MAPPING_MISMATCH`,
`CONSULTANT_CONTACT_UNSUPPORTED`, `RENDER_FAILED`), never a value. A consultation
letter whose stored signature image or installed clinic logo
(`faxLogoInConsultation`) cannot be read is still exported, without it, and both
Notes say so (`SIGNATURE_UNAVAILABLE`, `LOGO_UNAVAILABLE`); nothing is substituted.
When the configured logo file is not installed (the packaged sample path is not),
the letter prints without a logo, as the clinic's own Print does, and nothing is
noted. A request whose consultant is a
health-care-team contact rather than a directory specialist is not printed. The
Rourke pages print in the bundled DejaVu fonts, but the Java runtime still needs
its font libraries (freetype and fontconfig) and at least one installed system font
to load them; a server image without fonts exports these records as data summaries
with `FONT_UNAVAILABLE`. When Reports is not selected these records are
not exported, like any other unselected category. Long form histories (years of
Rourke visits, for example) export in full. Only a chart with more than 100,000
rows or 400,000 fields in one such table, or whose summary cannot be printed
completely, is held with a `CDS_EXPORT_DATA_SUMMARY_*` code.

eForms get more than a data summary. Every eForm the chart lists also travels as the
form itself: a second Reports entry holding a PDF of the saved eForm as CARLOS displays
it (SubClass the form name, the form's date, the provider who saved it as author, and
the "additional information" in Notes), rendered when the export runs by the same
Chromium renderer that prints and faxes eForms. The data summary stays: it is the
record of every stored value. A render never holds a patient. When the form cannot be
rendered completely, its data summary carries a short note saying so and why (the
renderer could not show all of the form's content, rendering timed out, the rendering
browser failed or was unavailable, the eForm changed while the export ran, the
rendered form was too large, or the export ran outside the web application); the
export never approves an incomplete form the way a clinician can when printing. eForms
the chart no longer lists (deleted, or replaced by an edit) and patient-independent
eForms are not exported; the ReadMe counts them per patient as "Patient records not
exported".

Rendering needs the web application's renderer (see "eForm rendering" below), so
browser-submitted exports render eForms and command-line exports hand their job to the
web application when it is running.

Records written by older OSCAR releases, which installations upgraded from them still
hold, travel the same way instead of holding the patient:

- the pre-CME encounter (eChart: social, family and medical history, ongoing concerns,
  reminders and encounter text), one "Legacy encounter" record per saved revision,
  "eChart revision n of m";
- the encounter immunization schedule that predates preventions, one "Immunization
  schedule" record per saved revision;
- patient relationships entered on the classic demographic page, one "Patient
  relationship" record in the chart that shows the relationship (the related patient's
  chart shows its own inverse entry; the summary names the related patient by CARLOS
  patient number only);
- legacy laboratory reports: MDS ("MDS laboratory report", with all of its segments),
  CML ("CML laboratory report", with its test lines and upload header) and BC PathNet
  ("PathNet laboratory report", with its order, request and result rows), each with
  Report class Lab Report, plus the "Laboratory requisition link" that ties a CML or HL7
  report to the requisition it answered.

A legacy laboratory report belongs to the chart its lab routing names, exactly the chart
in which CARLOS lists it; a report not matched to any patient is in no chart and is not
exported (its inbox routes are counted as unlinked, below). These reports travel only
with Reports: a job that selects LaboratoryResults without Reports holds a patient who
has one (`CDS_EXPORT_LEGACY_LAB_REPORTS_NEED_REPORTS`) rather than send an incomplete
laboratory history; select Reports and export the patient again.

A program admission whose admission notes, discharge notes or discharge reason are
filled in is exported as a "Program admission" data summary (program, dates, status
and the narratives) instead of holding the patient. Admissions without them are
program membership and are not exported. Program membership still decides who may
read the chart, exactly as before.

Records of removed CARLOS features (the old study, survey, CAISI form and HSFO
modules and similar) are never exported and never hold a patient. The encrypted
ReadMe lists, for each patient, each such table holding the patient's rows and
how many, and, for the whole job, each such table with no patient link that holds
rows.

Inbox items that are linked to no patient yet are not part of any chart and no longer
hold every patient: an HL7 lab or HRM report the inbox has not matched to a chart (or
a lab unlinked from its chart), and the routing, acknowledgement and reviewer rows of
an incoming document (fax, scan, upload) not yet filed to a chart. They are not
exported. Each job counts them per table (`providerLabRouting`, `document_review`,
`DocumentExtraReviewer`, `HRMDocument`, `HRMDocumentComment`, `HRMDocumentSubClass`,
`HRMDocumentToProvider`) when its capture begins: the counts are recorded in the
export ledger as `UNLINKED_SOURCE_ROWS` events, listed as `unlinkedSourceRows` in the
diagnostic report (`--report`, or the browser's report download), written to the
package ReadMe ("Unlinked inbox tables not exported", one table and row count each)
and to the application log as `CDS_EXPORT_UNLINKED_SOURCE_ROWS`. Table names and
counts only, never identifiers. File or match those items in the inbox before the
transfer if the receiving clinic needs them. A row whose link is broken rather than
absent (a link to a chart number that does not exist, a document filed under another
module, a lab type spelled differently from its patient link, a parent row that is
missing) could belong to any chart, so it still holds every patient
(`CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED`, naming the table).

Populated tables that this release does not know (another tool's bookkeeping
table, a local backup copy) do not block an export. They are listed by name and
estimated row count in the package ReadMe and in a protected log line
(`CDS_EXPORT_UNREVIEWED_LOCAL_TABLES`), and their contents are not exported. The
package-created `_carlos_demo_seed_complete` marker is ruled local operational.

### eForm rendering

The export renders eForms in the web application, through the eForm renderer of
`docs/eform-browser-pdf-renderer.md` (on the packaged install the
`carlos-emr-render-browser` service). Check it with the renderer's startup probe in the
application log before a large export. Renders run one at a time, so a clinician's print
or fax always keeps the renderer's other slot; each takes a few seconds and is bounded
by the renderer's own limits (30 seconds to wait for a slot, 30 to start the browser,
90 to render). After three browser failures or timeouts in a row the export stops
asking the renderer for five minutes and those eForms carry the "could not be rendered"
note. The application log records, for each chart with eForms, one line
`CDS_EXPORT_EFORM_RENDERS rendered=<n> unavailable=<n> renderMs=<total> longestMs=<max>`
(counts and times only).

| Property | Default | Meaning |
| --- | --- | --- |
| `cds.export.eformRenderIntervalMillis` | `250` | Pause between two export renders (0 to 60000). |
| `cds.export.maxDocumentBytes` | 1 GiB | Also bounds one rendered eForm; a larger one is noted, not held. |
| `cds.export.cliRendererHandoffSeconds` | `120` | How long a foreground command-line export that selects Reports leaves its job to the web application before running it itself (0 to 86400; 0 never hands off). |
| `eform_pdf_browser_base_url` | derived | Where the renderer reaches the application. A render without a request uses `http://127.0.0.1:<port>/<context>` from Tomcat's single plain-HTTP connector (18080 on the packaged install); set it when Tomcat has several plain connectors or only TLS. |

The command-line process cannot render. A foreground `--export` or `--resume` that
selects Reports prints `{"event":"HANDOFF",...}` and waits while the running web
application claims the job (it looks for queued jobs every few seconds) and exports it,
eForms rendered. If no web application claims the job within
`cds.export.cliRendererHandoffSeconds` (the web application is stopped, export is
disabled there, or it is busy with another export), the command prints
`{"event":"HANDOFF_FALLBACK",...}` and exports the job itself: every eForm is still in
the package through its data summary, noted "the export ran outside the web
application". `--detach` always leaves the job to the web application.

## Receive and decrypt the package

The delivered file is authenticated CMS, containing a ZIP with per-physician
patient XML and supporting ReadMe/event files. ZIP entries use STORED compression
so repetitive clinical text does not violate receiving archive-ratio safeguards.
The private staging store is separately encrypted; no plaintext fallback is used
when recipient/key verification fails.

A compatible OpenSSL recipient command is:

```sh
openssl cms -decrypt -inform DER -in transfer.cms \
  -recip recipient-certificate.pem -inkey recipient-private-key.pem \
  -out recipient-private-work/transfer.pending.zip
```

Run in a private recipient-owned work directory with a new output name. Treat
any nonzero exit as failed authentication/decryption and discard that newly
created partial output. Promote the output to its final new name only after
success; never pass a partial ZIP to an importer or overwrite an existing
transfer. The mandatory interoperability tests run this OpenSSL command and
compare the recovered ZIP byte for byte before the real receiving parser reads
it. The exact installed CLI/browser qualification independently decrypts and
checks actual completed job downloads. For large packages, choose a recipient
with a streaming CMS parser and verify its resource budget. The large-byte
qualification uses a separate streaming recipient; OpenSSL interoperability
uses a bounded fixture.

Validate the ReadMe's full paths, sender/group contact, software/schema identity,
physician/patient totals and audit access instructions. Reconcile event/category
counts with the selected transfer and receiving import results. ReadMe and event
information is inside the encrypted delivery, because it can identify patients.
Use the receiving importer's protected input procedure; do not upload recipient
private keys to CARLOS or send keys/passwords through the export form.

## Review, retention and support

An unsupported, inconsistent, ambiguous, inaccessible or unrepresented clinical
fact prevents a complete migration. Preserved native history and deletion context
are supported; unsupported legacy formats and archival carriers can require review.
Known patient/parent relationships keep unrelated charts outside the selection.
An unresolved owner or an unclassified clinical source can still require clinic-wide
review because its relationship to the selected charts cannot be established.
The exporter neither
silently omits those facts nor inserts them into generic notes. Review the safe
diagnostic code and source/mapping evidence with an authorized clinical reviewer.
Do not delete source rows, restamp original import metadata or manually set a
job to READY. Submit a new approved request after a genuine source/policy change.

Artifact availability is time limited. Retention leases keep active downloads
separate from cleanup. Expired/cancelled artifacts may become unavailable even
when an earlier file was downloaded; preserve the recipient's verified transfer
and agreed audit-access arrangement according to its retention obligations.
Retained artifacts continue to require their original configured store/key
binding. Status and diagnostics remain recovery tools even when new admission
is disabled or active clinical settings are invalid.

CARLOS does not define a paid migration service, vendor price list or response-time
promise in this feature. Before transfer, the deploying organization must identify
its responsible support contact and agree the scope and cost of assistance,
clinical review, receiving-system work and audit access with its support/recipient
providers. Record the agreed contact, coverage hours and charges with the transfer
plan; do not infer that custom conversion or vendor support is free. Public
project issue reports must omit patient data, credentials and private keys.

See the [implementation contract](cds-export.md) for detailed source, job,
publication, compatibility and recorded verification boundaries.

The approved V1.0.59.41 forward migration seeds independent `_cds_import=x` only for an existing `admin` role with `_admin=x` or `_admin=w`, preserving every existing import grant or denial. Other roles receive no grant. `provider_no` is permission-row metadata, not a scope restriction used by the privilege evaluator. This durable independent import grant requires separate revocation if administration is later removed.

The clinic `site` directory and `providersite` membership are reference configuration, like `clinic` and `program`; normal web startup creates them. The exporter still pins their entire physical schema, while patient/provider facts and sender context use their owning projections. Populated unsupported clinical tables continue to require review. Protected logs identify the pinned source-table name that blocks closure, without row identifiers or values.

Reviewed provider preferences, report definitions, referral directories and messaging
locks do not block a clinical export merely by existing. Their contents are not
published or executed. The [source rulings](cds-export-source-rulings.md) identify
the exact tables and owning evidence; clinical instances remain separately gated.

## The legacy CDS 4 export

The older demographic export (Demographic Export, template CMS4) remains available and is not
this exporter. It now names the laboratory without the HL7 type suffix and writes the
laboratory's test name as TestName when no other name was imported, exports weights whose unit
text was edited ("kg", "Kilograms") and converts weights recorded in pounds to kilograms (to
0.01 kg), and puts the patient alert first in NoteAboutPatient (#3959). A document larger than
`cds.legacyExport.maxEmbeddedDocumentBytes` (bytes; default 64 MB, at most 1 GB) is not read
into memory: its report names the file and the export lists a warning to transfer it
separately (#3968). Each generated file is re-read with the JDK's predefined-entity ceilings
lifted, so a large chart's escaped notes are no longer reported as failing the schema, and a
file that cannot be re-read is reported as such rather than as a schema failure (#5845).
