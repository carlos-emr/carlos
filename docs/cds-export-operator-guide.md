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
   compatibility.
2. Keep `cds.export.enabled=false` until the deployment's permissions, policies,
   storage and recipient-tool checks are complete. Import enablement is independent.
   Export does not require enabling import or entering an import maintenance window.
3. Grant the acting provider/account the independent export permission or current
   administration authority, demographic reads and every selected clinical-domain
   and patient-specific read. The ledger migration seeds the administrator export
   permission; it preserves an existing customized grant/denial on replay.
   A saved job ID or Unix root identity does not replace clinical authorization.
   Selecting Laboratory Results also requires read access to `_lab_sensitive`,
   which no role receives by default. Admission checks every selected category's
   role-level right and refuses a missing one by name (CLI exit 3, browser
   "Export refused" notice naming the right) before anything is accepted.
4. Configure `cds.export.clinicalZone` explicitly and consistently with the clinic
   and `cds.import.clinicalZone`. Configure protected selection, retained import,
   export-store and master-key roots. Store/key IDs are immutable bindings for
   accepted jobs; retain the original configured roots/keys while their artifacts
   remain available. Never rotate a missing or invalid existing key as a recovery
   shortcut.
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
   support agreement is available.
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

Native chart support includes demographics/external physicians, all seven history/note
categories, prescriptions and printed scripts, current/deleted measurements, supported
HL7 ORU results, and document reports with their attachments. Revised notes retain
the original and edits in one clinical occurrence.

Choose all seven history/note categories together: PersonalHistory, FamilyHistory,
PastHealth, ProblemList, RiskFactors, AlertsAndSpecialNeeds and ClinicalNotes. Select
NewCategory when exporting native measurements/labs or printed prescription context.
It carries generic observations, deletion history, complete original lab messages and
printed script history. Excluding a required supporting category requests review;
the exporter does not silently expand the accepted selection.

Database-selected prescriptions require a working configured `drugref_url`. Public
DIN qualification sends the DIN and lookup option only. The clinician's authored name
and complete compound-strength text remain intact; the standard strength uses the
database's first ingredient. Missing/ambiguous reference data requires review, and
an unavailable reference service produces an explicit failure. Inspect diagnostics
and restore that dependency before resuming; no DIN-free fallback hides an outage.

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
| 2 | Configuration/runtime failure or an unconfirmed result; inspect the original durable job. |
| 3 | Authorization denied. |
| 4 | Review, failed or incomplete foreground work, or unavailable artifact. |
| 5 | The package is ready but leaves out held patients; it is not a complete migration. |
| 130 | Waiting interrupted; durable cancellation was not requested. |

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

Populated tables that this release does not know (another tool's bookkeeping
table, a local backup copy) do not block an export. They are listed by name and
estimated row count in the package ReadMe and in a protected log line
(`CDS_EXPORT_UNREVIEWED_LOCAL_TABLES`), and their contents are not exported. The
package-created `_carlos_demo_seed_complete` marker is ruled local operational.

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
