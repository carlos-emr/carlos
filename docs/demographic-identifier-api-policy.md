# Demographic identifier API policy (release/2026.08)

The published WSDLs and nullable DTO properties remain unchanged. Normal conversion into `DemographicTo1`, `DemographicTransfer` and `DemographicTransfer2` omits SIN. This policy concerns these named converters; raw entities embedded in other responses remain a separate requirement tracked by #2811.

Bulk demographic conversion, active demographic lists and authorized search results omit HIN. Inbox listings also omit HIN. Patient-specific REST demographic detail retains HIN after the existing demographic and chart-read checks, supporting clinical identification without distributing identifiers through bulk discovery. This does not grant additional chart access. These rules do not mutate stored entities, remove input setters or change SOAP schema members.

## Updating a minimized REST demographic

`PUT /demographics` on either REST server accepts the optional `X-Carlos-Demographic-Write-Profile` header:

| Value | Meaning |
| --- | --- |
| `preserve-hidden-identifiers` (default) | Null or omitted SIN/HIN preserves the current stored value. An explicitly supplied non-null value replaces it; an empty string explicitly clears it. |
| `legacy-null-identifiers` | Retains the historical full-record behavior: null or omitted SIN/HIN clears the value. Use only for clients that deliberately depend on that behavior. |

A response acknowledges the selected profile using the same header. Blank, unknown, repeated or combined selections return HTTP 400 before writes. Existing demographic-update and patient-write permissions remain required in either profile. This header does not select an XML/date profile or alter SOAP writes.

The default locks and refreshes the existing database row inside the resource's transaction before preserving omitted identifiers or changing related records. A missing record returns 404. Recognized pre-write concurrency conflicts return 409 with a fixed reload instruction; no related record is changed. Other storage failures propagate through the existing exception handling rather than being reported as success. Related changes and the demographic write share the transaction and roll back on failure. A client receiving an uncertain network/commit outcome must reload before deciding whether to submit another update; this is not an idempotency protocol.

## Executable evidence

`DemographicConverterUnitTest` checks the three named outbound mappings, bulk HIN omission, retained patient-specific HIN and unchanged source records. `DemographicSearchAuthorizationUnitTest` retains readable rows/counts while removing search HIN. Real CXF `DemographicServiceEndpointTest` cases cover both profile choices, acknowledgement, validation, authorization, explicit replacement/clear and pre-write conflict refusal. `DemographicWriteProfileUnitTest` checks strict parsing and registration on both REST configurations.

`DemographicIdentifierPreservationTransactionIntegrationTest` exercises the production resource transaction annotation through a Spring proxy with JDBC stand-ins: commit, explicit clears, late rollback and competing writers. `DemographicIdentifierPreservationMariaDbIntegrationTest` uses the production JPA DAO and transaction manager on generated, Ontario Flyway and BC Flyway schemas. Its five cases verify a resource-owned transaction, refresh of a preexisting persistence-context snapshot, rollback, explicit clears and refusal to overwrite a row changed after an older repeatable-read snapshot. The manager in these focused fixtures is a write stand-in; they do not claim to qualify the entire installed demographic archival workflow. CI report verification requires every native case in each of the three lanes.
