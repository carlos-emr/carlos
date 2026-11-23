# Demographic identifier API policy (release/2026.08)

The published WSDLs and nullable DTO properties remain unchanged. Two identifiers are treated differently:

- **SIN is never sent.** Conversion into `DemographicTo1`, `DemographicTransfer` and `DemographicTransfer2`, and the patient records embedded in appointment and consultation responses, omit it. A client cannot read a SIN through these APIs.
- **HIN is sent wherever 2026.08.0-beta6 sent it.** The health card number is how existing clients identify and match a patient, so an unmodified client keeps receiving it and does not need to change:

| Response | HIN |
| --- | --- |
| REST `GET /demographics` (active patient list) and other `DemographicTo1` lists | Present |
| REST demographic search results (`DemographicSearchResult`) | Present |
| REST inbox items (`GET /inbox/mine`) | Present |
| Patient embedded in appointment and consultation responses, including its `label` and `standardIdentificationHtml` text | Present |
| Patient-specific REST demographic detail | Present |
| SOAP `DemographicTransfer` / `DemographicTransfer2` | Present |

What limits disclosure is which patients a caller may see, not which fields a row carries. List, search and discovery responses contain only patients that pass the caller's demographic read right and the per-patient chart restrictions; a restricted patient is left out of the rows and of the totals, with or without a health card number. Embedded patients are returned only inside records the caller was already authorized to read. These rules do not mutate stored entities, remove input setters or change SOAP schema members. This policy concerns the named converters and response graphs; it is not a global serializer policy for every domain entity.

## Updating a REST demographic without resending identifiers

`PUT /demographics` on either REST server accepts the optional `X-Carlos-Demographic-Write-Profile` header:

| Value | Meaning |
| --- | --- |
| `preserve-hidden-identifiers` (default) | Null or omitted SIN/HIN preserves the current stored value. An explicitly supplied non-null value replaces it; an empty string explicitly clears it. A client can therefore echo a response back (which never contains the SIN) without erasing the stored SIN. |
| `legacy-null-identifiers` | Retains the historical full-record behavior: null or omitted SIN/HIN clears the value. Use only for clients that deliberately depend on that behavior. |

A response acknowledges the selected profile using the same header. Blank, unknown, repeated or combined selections return HTTP 400 before writes. Existing demographic-update and patient-write permissions remain required in either profile. This header does not select an XML/date profile or alter SOAP writes.

The default locks and refreshes the existing database row inside the resource's transaction before preserving omitted identifiers or changing related records. A missing record returns 404. Recognized pre-write concurrency conflicts return 409 with a fixed reload instruction; no related record is changed. Other storage failures propagate through the existing exception handling rather than being reported as success. Related changes and the demographic write share the transaction and roll back on failure: the custom fields and their archive copy, the waiting-list entry, the archive copy of the stored patient, the patient row, each extension with its archive copy, and the audit rows the manager writes. Only the web-service access log row is written outside it, after it has ended, so a failed request is still recorded. A client receiving an uncertain network/commit outcome must reload before deciding whether to submit another update; this is not an idempotency protocol.

When the database refuses a duplicate single-value extension on create or
update, the request rolls back and returns HTTP 409 with the JSON error code
`DUPLICATE_DEMOGRAPHIC_EXTENSION_KEY`. On update this covers both ways of
producing the duplicate: a second new extension with a key already sent or
stored, and a stored extension whose key is changed to one the patient already
holds. The manager writes the extensions in the order given and executes a
changed stored extension as it saves it, so that refusal is answered by the
request and not raised at its commit, and a later extension in the same request
may take a key an earlier one gave up.

Three more refusals of an update return HTTP 409 with a JSON error code, and
change nothing. `DEMOGRAPHIC_EXTENSION_NOT_FOUND`: an extension in the request
carries an id that is not a stored extension of this patient. The answer is the
same for an id that no longer exists and for another patient's id, and it is
given before anything is written, so an update cannot re-point or overwrite a
row in another chart. Every extension is saved under the patient being updated,
whatever patient number it carries. `WAITING_LIST_ENTRY_NOT_FOUND`: the request
names a waiting list on which the patient has no current entry, typically because the
entry was removed after the client loaded the patient. A removal through the
application keeps the entry as history, which does not count, and neither does an
entry whose history flag is unset (the column is nullable, and the patient read
and the list pages show only entries flagged `'N'`): the update used to give such
a patient a current entry on the list, and for a patient who was never on the list
it saved the patient and answered success while dropping the waiting-list note. A
`waitingListID` of 0, or none, still means that no list is named.
`WAITING_LIST_CHANGED`: the update reads the list's current entries with a locking
read and holds them until it commits, because recording the note renumbers the
list. When the database refuses that lock, or reports that an entry changed after
the update first read (another update of the same list overlapped: a lock wait
that timed out, a deadlock, or MariaDB error 1020), this code is answered. In all
three cases the client reloads the patient before retrying.

Recording the note replaces the patient's entry on the list, and the replacement
keeps the start date of the entry it replaces. The update does not read
`onWaitingListSinceDate` from the request. The replacement used to be dated today;
the list is numbered by start date, so a patient whose note was edited dropped
behind everyone who had joined later. Entries with the same start date are numbered
by the place they already hold; the order among them used to be left to the
database, which put the replacement, a newer row, last. Taking a patient off a list through the
application takes the same lock, in one transaction with the change, so a removal
that overlaps an update waits for it and retires the entry the update committed.
The lock is taken on the list's oldest entry first, which no change removes, and
then on its current entries, so the wait does not depend on which entries the
earlier writer replaced. Without the lock the removal retired only the entry the
update had already replaced and reported success while the patient stayed listed.

## Executable evidence

`DemographicConverterUnitTest` checks the three named outbound mappings: SIN omitted, HIN retained in detail and list conversions, source records unchanged. `DemographicSearchAuthorizationUnitTest` returns the HIN of readable search rows and leaves restricted patients out of rows and counts. Real CXF `DemographicServiceEndpointTest` cases cover the HIN in `GET /demographics`, both write-profile choices, acknowledgement, validation, authorization, explicit replacement/clear and pre-write conflict refusal. `InboxIdentifierDisclosureEndpointTest` covers the HIN on lab and document inbox items; `EmbeddedRecordSnapshotsUnitTest` and `EmbeddedRecordXmlContractUnitTest` cover it on patients embedded in appointment and consultation responses. `DemographicWriteProfileUnitTest` checks strict parsing and registration on both REST configurations.

`DemographicIdentifierPreservationTransactionIntegrationTest` exercises the production resource transaction annotation through a Spring proxy with JDBC stand-ins: commit, explicit clears, late rollback and competing writers. `DemographicIdentifierPreservationMariaDbIntegrationTest` uses the production JPA DAO and transaction manager on generated, Ontario Flyway and BC Flyway schemas. Its five cases verify a resource-owned transaction, refresh of a preexisting persistence-context snapshot, rollback, explicit clears and refusal to overwrite a row changed after an older repeatable-read snapshot. The manager in these focused fixtures is a write stand-in; they do not claim to qualify the entire installed demographic archival workflow. CI report verification requires every native case in each of the three lanes.

`DemographicUpdateDaoTransactionIntegrationTest` qualifies the update those fixtures stand in for (issue #5298). It runs the production manager, REST resource and DAOs and has the database itself refuse a write: the patient row after its archive copy was inserted, a later extension row, the custom-field row, and the audit row. After each refusal, other connections find no archive, patient, extension, custom-field, waiting-list or audit change. A successful update keeps the patient's merge links, attributes the row and its audit to the signed-in provider, and archives the previous custom fields, which the REST update used to overwrite without an archive row. It also pins the refusals that precede any write: a caller without update, patient or read access, a missing patient record (404) and a failure to lock the row. Two cases rename a stored extension onto a key the patient already holds: the manager raises the database's refusal itself, before the caller's transaction commits, and on MariaDB the REST update answers it with the 409 above. A third stores a request in which a stored extension gives up the key a new one takes, which is valid only in the order given. Five more cover the refusal of a waiting list the patient is not on, and of one the patient was taken off, whose entry the application keeps as history (and the "no list" value 0, which is not refused), the refusal of another patient's extension id before either chart changes, the binding of every extension to the patient being updated, and the OBEC version-code update, whose archive row must hold the code that was replaced and which must leave the patient's preferred name and alias as stored (the patient copy it updates from used to carry the alias into `pref_name`). Two more cover the waiting list: an entry whose history flag is NULL is no current entry, so an update naming that list is refused and the row is left alone; and two REST updates that start from the same stored entry leave one current entry, because the later one reads the committed entries under its lock even when its transaction first read before the earlier one committed. On MariaDB that case switches `innodb_snapshot_isolation` off for its session: with it on (the server default since MariaDB 11.6.2) the later update is refused at the patient's row lock before it reaches the list. Four more cover the note edit and the removal: an update that records a note leaves the patient's start date and both patients' places on a two-patient list as they were, also when both joined on the same day; a removal whose transaction first read before an update committed retires the entry that update put in place; and a removal that starts on another connection while an update is uncommitted waits for it and leaves the patient off the list. The thirty cases run on H2 in the ordinary lane and on physical MariaDB with the generated, Ontario Flyway and BC Flyway schemas in CI, where each is a named requirement in every lane; the release plan selects the same three. `scripts/demographic-rest-update-atomicity-playwright-checks.js` repeats the successful update, a refused new extension, a refused rename of a stored one (both `uk_demo_ext_single_value`), the in-order update, a refused extension id of another patient, a note update that keeps the start date and place on a two-patient list, and refused updates for a removed waiting-list entry, for one with a NULL history flag and for a missing one against a packaged install. It skips before it opens the database unless `EXCLUSIVE=1`, and its patient is registered before its INSERT, so a patient whose INSERT committed without an answer is found by the run's marker and removed (`scripts/recoverable-patient-fixture.test.js`).
