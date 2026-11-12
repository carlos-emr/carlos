# Demographic identifier API policy (release/2026.08)

The published WSDLs and nullable DTO properties remain unchanged. Two identifiers are treated differently:

- **SIN is never sent.** Conversion into `DemographicTo1`, `DemographicTransfer` and `DemographicTransfer2`, and the patient records embedded in appointment and consultation responses, omit it. A client cannot read a SIN through these APIs.
- **HIN is sent wherever it was sent before 2026.08.** The health card number is how existing clients identify and match a patient, so an unmodified client keeps receiving it and does not need to change:

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

The default locks and refreshes the existing database row inside the resource's transaction before preserving omitted identifiers or changing related records. A missing record returns 404. Recognized pre-write concurrency conflicts return 409 with a fixed reload instruction; no related record is changed. Other storage failures propagate through the existing exception handling rather than being reported as success. Related changes and the demographic write share the transaction and roll back on failure. A client receiving an uncertain network/commit outcome must reload before deciding whether to submit another update; this is not an idempotency protocol.

## Executable evidence

`DemographicConverterUnitTest` checks the three named outbound mappings: SIN omitted, HIN retained in detail and list conversions, source records unchanged. `DemographicSearchAuthorizationUnitTest` returns the HIN of readable search rows and leaves restricted patients out of rows and counts. Real CXF `DemographicServiceEndpointTest` cases cover the HIN in `GET /demographics`, both write-profile choices, acknowledgement, validation, authorization, explicit replacement/clear and pre-write conflict refusal. `InboxIdentifierDisclosureEndpointTest` covers the HIN on lab and document inbox items; `EmbeddedRecordSnapshotsUnitTest` and `EmbeddedRecordXmlContractUnitTest` cover it on patients embedded in appointment and consultation responses. `DemographicWriteProfileUnitTest` checks strict parsing and registration on both REST configurations.

`DemographicIdentifierPreservationTransactionIntegrationTest` exercises the production resource transaction annotation through a Spring proxy with JDBC stand-ins: commit, explicit clears, late rollback and competing writers. `DemographicIdentifierPreservationMariaDbIntegrationTest` uses the production JPA DAO and transaction manager on generated, Ontario Flyway and BC Flyway schemas. Its five cases verify a resource-owned transaction, refresh of a preexisting persistence-context snapshot, rollback, explicit clears and refusal to overwrite a row changed after an older repeatable-read snapshot. The manager in these focused fixtures is a write stand-in; they do not claim to qualify the entire installed demographic archival workflow. CI report verification requires every native case in each of the three lanes.
