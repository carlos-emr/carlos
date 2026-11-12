# Document API content policy (release/2026.08)

REST's `DocumentConverter` already returns metadata without reading or populating `fileContents`. The nullable input property remains available for uploads.

## SOAP bulk operations

`getDocumentsUpdateAfterDate`, `getDocumentsByProgramProviderDemographicDate` and `getDocumentsByDemographicIdAfter` on `DocumentWs` keep their published behaviour for every existing client: **a request without the `X-Carlos-Document-List-Profile` header returns each document with its `fileContents` inline**, as it did before the header existed. An unmodified synchronization client does not need to change, and it never receives an empty document in place of a stored one.

The optional HTTP request header `X-Carlos-Document-List-Profile` is an opt-in for clients written against it:

| Request | `fileContents` | A stored file that cannot be read |
| --- | --- | --- |
| No header (default) | Inline | That document is left out of the page, as before; the rest of the page is returned. The server log records the document number and the failure type, never the filename. |
| `metadata` | Omitted; no stored file is read | Not applicable. Every selected document stays in the page. |
| `legacy-inline` | Inline | The whole response is a Server fault with a fixed message, so a page is either complete or refused. |

The header-less default deliberately does not fault a page over one unreadable file. A timestamp-driven client repeats the same request after a fault, so one damaged file would stop it at that page permanently; this is why the stricter outcome is only given to a caller that selects `legacy-inline`. Operators should watch the log for `left out of the bulk response` and repair or re-file the named document.

A blank, unknown, combined or repeated header value is a Client fault before the document query. A response to a request that named a profile repeats it in `X-Carlos-Document-List-Profile`; a response to a header-less request carries no such header. Responses from the document service carry `Cache-Control: no-store`. Inline bulk responses keep their historical byte-array memory cost, so request small pages; a client that only needs to discover documents can select `metadata` and fetch content one document at a time. The published WSDLs and the optional `fileContents` member are unchanged. `getDocument(id)` returns one document with inline content under every profile, and faults with a fixed message when its file cannot be read. Private filenames and exception payloads are excluded from faults and from the log.

## Individual streaming retrieval

`GET /document/{id}/content` is available under both `/ws/rs` and `/ws/services`. It returns exact file bytes as `application/octet-stream`, with `Content-Disposition: attachment`, `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. It does not send a private filename in a header or infer active HTML content types. This endpoint can retrieve stored bytes without passing through the SOAP legacy temporal projection.

The authenticated actor must have `_edoc r` before lookup. Before resolving or opening a file, the endpoint applies the shared stored-document read policy: every distinct demographic link and DOC-routed patient must pass patient-specific `_edoc r` and demographic/eChart restrictions through `isAllowedAccessToPatientRecord`; persisted program restrictions and active queue visibility also apply. Unassigned/nonpatient documents retain these document/program/queue boundaries. Demographic module IDs `0` and `-1` are supported unfiled/inbox sentinels and do not designate patient records. Missing records return 404, invalid IDs return 400 and access denial returns 403. Missing/unreadable stored files and storage failures fail explicitly; they are never replaced with an empty successful download.

The file path must be contained within the existing configured `DOCUMENT_DIR`. Reads use that validated path and refuse a final symlink. The file streams through a bounded transfer buffer and closes on completion or failure; output failures propagate. A failure after HTTP output has begun cannot replace already-sent bytes with a structured error, so clients must treat an interrupted transfer as failure. No document is loaded into a response byte array by this REST endpoint.

`DocumentListContentEndpointTest` exercises the real SOAP pipeline for all three bulk operations: inline bytes and omission of an unreadable document for a header-less request, metadata without file reads for `metadata`, exact bytes and the safe whole-response fault for `legacy-inline`, and profile acknowledgement/refusal. `DocumentListProfileUnitTest` pins the selection rule. `DocumentContentEndpointTest` exercises real REST routing, exact bytes, safe headers, global/patient/chart denial, additional linked and DOC-routed patients, program and queue restrictions, both inbox sentinels, 400/404/500 outcomes, containment and output failure propagation. The existing CDS projection and all fourteen published-WSDL checks remain required.
