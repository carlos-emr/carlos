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

## Executable evidence

`DemographicConverterUnitTest` checks the three named outbound mappings: SIN omitted, HIN retained in detail and list conversions, source records unchanged. `DemographicSearchAuthorizationUnitTest` returns the HIN of readable search rows and leaves restricted patients out of rows and counts. Real CXF `DemographicServiceEndpointTest` cases cover the HIN in `GET /demographics`, both write-profile choices, acknowledgement, validation, authorization, explicit replacement/clear and pre-write conflict refusal. `InboxIdentifierDisclosureEndpointTest` covers the HIN on lab and document inbox items; `EmbeddedRecordSnapshotsUnitTest` and `EmbeddedRecordXmlContractUnitTest` cover it on patients embedded in appointment and consultation responses. `DemographicWriteProfileUnitTest` checks strict parsing and registration on both REST configurations.

`DemographicIdentifierPreservationTransactionIntegrationTest` exercises the production resource transaction annotation through a Spring proxy with JDBC stand-ins: commit, explicit clears, late rollback and competing writers. `DemographicIdentifierPreservationMariaDbIntegrationTest` uses the production JPA DAO and transaction manager on generated, Ontario Flyway and BC Flyway schemas. Its five cases verify a resource-owned transaction, refresh of a preexisting persistence-context snapshot, rollback, explicit clears and refusal to overwrite a row changed after an older repeatable-read snapshot. The manager in these focused fixtures is a write stand-in; they do not claim to qualify the entire installed demographic archival workflow. CI report verification requires every native case in each of the three lanes.

`DemographicUpdateDaoTransactionIntegrationTest` qualifies the update those fixtures stand in for (issue #5298). It runs the production manager, REST resource and DAOs and has the database itself refuse a write: the patient row after its archive copy was inserted, a later extension row, the custom-field row, and the audit row. After each refusal, other connections find no archive, patient, extension, custom-field, waiting-list or audit change. A successful update keeps the patient's merge links, attributes the row and its audit to the signed-in provider, and archives the previous custom fields, which the REST update used to overwrite without an archive row. It also pins the refusals that precede any write: a caller without update, patient or read access, a missing patient record (404) and a failure to lock the row. Two cases rename a stored extension onto a key the patient already holds: the manager raises the database's refusal itself, before the caller's transaction commits, and on MariaDB the REST update answers it with the 409 above. A third stores a request in which a stored extension gives up the key a new one takes, which is valid only in the order given. The eighteen cases run on H2 in the ordinary lane and on physical MariaDB with the generated schema in CI, where each is a named requirement; the release plan also selects them on the Ontario and BC Flyway schemas. `scripts/demographic-rest-update-atomicity-playwright-checks.js` repeats the successful update, a refused new extension, a refused rename of a stored one (both `uk_demo_ext_single_value`) and the in-order update against a packaged install.
