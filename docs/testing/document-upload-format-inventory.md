# Installed document upload contracts (#5730)

Target: `release/2026.08`, including upstream `d9f0a9551784`. The matrix check
uses the actual patient eDoc form and the stock modern Inbox uploader. SQL only
creates isolated actors/patients and reads independent persistence expectations.
All files are generated synthetic documents. Existing empty-file, forwarding,
collision, permissions and incoming-document checks remain registered.

| Entry point | Release behavior and selected cases |
| --- | --- |
| Patient eDoc: `ViewDocumentReport` → Add | The file input has no format allowlist. Representative valid PDF, PNG, JPEG, plain text and RTF files must preserve exact bytes, posted MIME, creator, status and the owned patient/module link. No provider-routing control is offered, so no DOC route is expected. `.PDF` overrides an octet-stream MIME to PDF and counts pages. |
| eDoc opaque content | Truncated PDF, invalid PNG and PDF bytes named `.png` are accepted by the existing storage contract. This is explicitly opaque storage, with zero pages where appropriate; it is not evidence of content validation or renderability. Their actual document output must still preserve bytes. |
| Modern Inbox: `ViewDocumentUploader` → Pending Docs | Native selection accepts `.pdf` case-insensitively and rejects a representative PNG before dispatch. Accepted documents belong to `demographic/-1`, with the uploader as creator/responsible, plus exactly the selected recipient's DOC route. The fixture selects an offered provider with no active forwarding rules; shared accounts and rules are never changed. |
| Inbox PDF cases | Lowercase, uppercase/octet-stream, mixed-case `.pDf`, truncated PDF and PNG bytes named `.pdf`. Valid PDFs must have one page regardless of extension case; malformed bytes remain opaque, with no invented content-validity claim. The output check reopens each exact Inbox row and downloads through its actual native control. |
| Legacy Inbox profile | `legacy_document_upload_enabled=true` selects `ViewHtml5AddDocuments`, a separate form/action. The default profile selects the modern uploader. This matrix explicitly skips on that alternate profile; it does not claim a legacy execution. Existing legacy-upload checks remain separate. |

The installed Struts `struts.multipart.maxSize` is read before generating size
fixtures. The current package sets **52,428,800 bytes**; nginx's `50m`,
ModSecurity's request-body limit and Tomcat's upload envelope agree. The check
calibrates actual native multipart overhead from a small PDF, then uploads valid
one-page PDFs whose complete requests are limit minus one and exactly the
limit. The plus-one request must be refused with a truthful 413 and no new
row/file. This is evidence of the packaged transport envelope, not application
content validation. Large files are selected from local fixture paths through
the real file control; they are not submitted with an API client. Poppler must
independently read each padded PDF before submission. Draft metadata and a
small valid retry are checked after refusals.

Every accepted eDoc file is reopened through its native display/download link;
Inbox files use their actual Inbox row and viewer download button. Stored and
returned SHA-256 values are compared independently of JSON success. An unrelated
control's full document/link/routing rows and file hash must remain unchanged.
Cleanup captures IDs before assertions, guards every patient/provider link,
removes exact owned notes/queue/cache/file/row residue and retains parents if
ownership or cleanup is uncertain. `EXCLUSIVE=1` is required. Poppler and the
locked `pngjs` dependency are prerequisites; missing prerequisites cannot pass
strict release qualification.

The fresh snapshot30 ON and BC retakes reach all nine eDoc formats, empty refusal with
retained draft metadata and valid retry, all five Inbox uploads, exact full-request
size boundaries with retained draft/retry, and every native Inbox reopen/download.
Valid PDF previews decode. The two deliberately malformed opaque PDFs receive
exactly one 500 render response apiece and a visible native “could not be loaded”
status; their originals still download byte-for-byte. Only those observed owned
GET failures are acknowledged after asserting their identity, status and fallback.
This verifies truthful render failure, not upload rejection/content validation.
Unrelated browser errors stay fatal.

The final check fails only because the valid mixed-case `.pDf` has zero stored
pages, tracked as [#5818](https://github.com/carlos-emr/carlos/issues/5818). No new
expected-failure pin was added. Exact cleanup and an independent SQL audit show
zero owned documents/files/routes/patients/providers in both provinces.
Snapshot30 application package SHA-256 is
`bdc240d0e9581c57290e76634a5ab3e62322e3a295236863d9851009fd2b8e32`;
its source checkout is `52b5b9c80752ea3d3732ee9e92bb98c6a3cd69ed`.

Two development fixture corrections remain relevant to recovery: the first
recipient had a pre-existing forwarding rule, so asserting one recipient was
wrong; cleanup retained those fixtures rather than deleting an unknown route.
Explicit recovery verified all 17 owned IDs/files and their captured routes,
removed the retained parents, and preserved shared rules. The check now selects
an offered provider without active forwarding rules. The first large-file
selection also hit the browser protocol timeout before submission; selecting an
actual local fixture path removes that unnecessary base64 transport. Neither
failed development attempt is counted as a pass.
