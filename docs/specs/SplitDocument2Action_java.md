# Functional Specification: PDF Document Manipulation Action

> **Clean Room Specification**
>
> This document is a black-box functional specification describing observable behavior only.
> It contains no source code, no internal implementation details, and no copyrightable
> expression from any existing implementation. It is intended to serve as the sole input
> for a clean room reimplementation per the Chinese Wall methodology.
>
> **Ordering principle:** All lists, tables, and enumerated items in this specification
> follow deterministic ordering: alphabetical by display name, or causal (where step B
> depends on step A's output). Independent items are always alphabetical. This prevents
> accidental structural mirroring of any prior implementation.
>
> **SSO/AFC compliance:** This specification has been audited against the
> Structure-Sequence-Organization test (*Whelan v. Jaslow*) and the
> Abstraction-Filtration-Comparison test (*Computer Associates v. Altai*, 1992).
> All non-protectable elements (functional ideas, externally-dictated interface
> contracts, efficiency-driven sequences) have been identified. Remaining content
> describes only observable behavior, using independent organizational choices
> (alphabetical ordering, phase-based grouping, unordered sets for independent
> operations) that do not mirror the structure of any prior implementation.
>
> **Standards applied:**
> - Document structure adapted from IEEE 830-1998 / ISO/IEC/IEEE 29148:2011
> - Clean room methodology per Chinese Wall technique
>
> **Methodology references:**
> - [AI Could Be Your Next Team for Clean Room Development (Copyleft Currents)](https://heathermeeker.com/2025/03/28/ai-could-be-your-next-team-for-clean-room-development/)
> - [Clean-room design (Wikipedia)](https://en.wikipedia.org/wiki/Clean-room_design)
> - [How Clean Room Reverse Engineering Built the Modern Tech Industry (NTARI)](https://www.ntari.org/post/how-clean-room-reverse-engineering-built-the-modern-tech-industry)
> - [IEEE 830-1998 SRS Structure (Rebus Press)](https://press.rebus.community/requirementsengineering/back-matter/appendix-c-ieee-830-template/)
> - [Preventing an IP Infection: Clean Room Development Procedure (IPWatchdog)](https://ipwatchdog.com/2023/04/29/preventing-an-ip-infection-clean-room-development-procedure/id=160187/)

---

## 1. Introduction

### 1.1 Purpose

This specification defines the externally observable behavior of a PDF document manipulation action within an electronic medical records (EMR) system. It serves as the sole input for a clean room reimplementation.

**Traceability note:** This spec corresponds to the component identified as `SplitDocument2Action` in the existing codebase. This name is provided solely for traceability between the specification and the original system; it does not prescribe a class name, method name, or any internal naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Four PDF manipulation operations (remove first page, rotate 180, rotate 90, split) accessible via a single HTTP endpoint
- HTTP request/response contracts for each operation
- Database side effects (document creation, routing, page count updates)
- Filesystem side effects (PDF file creation and modification)
- Client integration expectations

**Out of scope:**
- The split page selection user interface (specified separately as a client-side concern)
- PDF rendering and page thumbnail generation (handled by a separate document viewer component)
- Document upload and initial creation (handled by a separate upload component)
- Authentication and session management (provided by the surrounding framework)
- The internal implementation approach — any architecture that produces the specified observable behavior is acceptable

### 1.3 Document Conventions

- The word **"shall"** indicates a mandatory requirement.
- The word **"should"** indicates a recommended but optional behavior.
- The phrase **"is observed to"** indicates documented behavior of the existing system that the reimplementation should replicate for compatibility, but which is not part of the formal functional contract.
- All lists of independent items are **alphabetically ordered** to prevent structural mirroring.
- Causally dependent steps are numbered sequentially; independent steps use unordered bullets.

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| **Control document record** | A metadata association that links a document to a module (e.g., "demographic") and a module-specific entity ID, with a status field. Used to track which part of the system "owns" the document. |
| **Document type identifier** | The string constant `"DOC"` used as a discriminator in routing tables to distinguish document items from other routable item types (e.g., lab results). All routing operations for documents use this value. |
| **Document metadata** | The database record describing a stored PDF, including fields such as filename, creator, status, page count, content type, and observation date. Distinct from the PDF file itself. |
| **Document storage directory** | A server-side filesystem directory, configured at the application level, where all PDF files are stored as flat files. |
| **Patient routing** | A database association linking a document to a patient (demographic) record, so the document appears in that patient's document history. |
| **Provider** | An authenticated healthcare practitioner who uses the EMR system. Identified by a provider number. |
| **Provider inbox routing** | A database association that causes a document to appear in a provider's inbox for review. Multiple providers can have routing for the same document. |
| **Provider lab routing** | A database association that links a document to a provider through the laboratory routing subsystem, used for documents that require provider acknowledgment. |
| **Queue** | A named organizational container for documents. Documents are assigned to queues for workflow management (e.g., triage, filing). Queue ID `1` is the default queue. |
| **Rendered page cache** | A cache of pre-rendered page images (thumbnails or full-size) for PDF documents. When a PDF is modified, cached renderings become stale and must be invalidated. |

---

## 2. HTTP Interface

### 2.1 Endpoint

The component shall be accessible at a single URL endpoint under the document manager namespace. All four operations shall share this endpoint.

### 2.2 HTTP Method

All requests shall use the POST method.

### 2.3 Method Dispatch

A request parameter named `method` shall select the operation:

| `method` value | Operation invoked |
|----------------|-------------------|
| `removeFirstPage` | Remove first page |
| `rotate180` | Rotate 180 |
| `rotate90` | Rotate 90 |
| `split` | Split |
| *(missing or unrecognized)* | No-op; the component shall return the default success view |

---

## 3. Functional Requirements

### 3.1 Remove First Page

#### FR-RFP-1: Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the PDF to modify |

#### FR-RFP-2: Guard condition

The component shall take no action if the PDF has only one page or zero pages. The HTTP response shall have no body.

#### FR-RFP-3: Page removal

The component shall remove the first page from the PDF and overwrite the original file on disk with the modified PDF.

#### FR-RFP-4: Cache invalidation

The component shall invalidate cached rendered versions for all pages in the document.

#### FR-RFP-5: Page count update

If the file save succeeded, the component shall decrement the document's page count in the database by one.

#### FR-RFP-6: Response

The HTTP response shall have no body.

#### FR-RFP-7: Error handling

Errors shall propagate to the web framework's default error handling.

---

### 3.2 Rotate 180

#### FR-R180-1: Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the PDF to rotate |

#### FR-R180-2: Page transformation

The component shall rotate every page in the PDF by 180 degrees and overwrite the original file on disk with the modified PDF.

#### FR-R180-3: Cache invalidation

The component shall invalidate cached rendered versions for all pages in the document.

#### FR-R180-4: Response

The HTTP response shall have no body. The client is expected to refresh independently.

#### FR-R180-5: Error handling

Errors shall propagate to the web framework's default error handling.

---

### 3.3 Rotate 90

#### FR-R90-1: Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the PDF to rotate |

#### FR-R90-2: Page transformation

The component shall rotate every page in the PDF by 90 degrees clockwise and overwrite the original file on disk with the modified PDF.

#### FR-R90-3: Cache invalidation

The component shall invalidate cached rendered versions for all pages in the document.

#### FR-R90-4: Response

The HTTP response shall have no body.

#### FR-R90-5: Error handling

Errors shall propagate to the web framework's default error handling.

---

### 3.4 Split

#### FR-SPL-1: Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the source PDF document |
| `page` (multi-valued) | String array, each entry formatted as `pageNumber,rotationDegrees` | Yes | Ordered list of pages to extract. Page numbers are 1-based. Rotation is in degrees (0, 90, 180, 270). |
| `queueID` | String (numeric queue ID) | No | Document queue to assign the new document to. Defaults to `"1"` if absent or empty. |

#### FR-SPL-2: Authentication

The component shall obtain the currently authenticated provider from the HTTP session.

#### FR-SPL-3: Source document

The component shall retrieve the source document's metadata record from the database using the provided document ID and open the corresponding PDF file from the document storage directory.

#### FR-SPL-4: Page extraction

The component shall extract each specified page from the source PDF (in the order given in the page selection array), set each page's rotation to the specified absolute value, and assemble the extracted pages into a new PDF document.

> **Note:** The rotation value is absolute (e.g., `90` means "set rotation to 90°"), not additive. This differs from the rotate operations in §3.2 and §3.3, which add to the existing rotation.

#### FR-SPL-5: Guard condition

If the page selection is empty or null, no new document shall be created. The HTTP response shall trigger the close-and-reload view.

#### FR-SPL-6: New document metadata

The component shall create a new document metadata record. Fields with meaningful values (alphabetical):
- Content type: `application/pdf`
- Creator: the currently authenticated provider
- Filename stem: same as the source document (the system prepends a timestamp automatically)
- Module: `"demographic"`, module ID: `"-1"`
- Observation date: current date
- Page count: number of pages in the new PDF
- Responsible party: the source document's original creator
- Status: active
- Visibility: private (not public)

All other metadata fields (description, HTML content, review date/time, reviewer ID, source, type) shall be empty/default.

#### FR-SPL-7: Persist and save

The component shall save the metadata record to the database and save the new PDF file to the document storage directory. After saving, the new document shall have a unique ID and a unique filename.

#### FR-SPL-8: Routing and linking

The following routing tasks are independent of each other. The component shall perform all that apply:

- **Control document cloning:** If the source document has a control document record, the component shall create an equivalent control document record for the new document, preserving the module ID and status from the source.
- **Patient routing:** If the source document is linked to a patient, the component shall create the same patient linkage for the new document, using the document type identifier `"DOC"`.
- **Provider inbox routing:** The component shall copy all existing provider inbox routing entries (identified by document type `"DOC"`) from the source document to the new document. The component shall also add the currently authenticated provider to the new document's inbox routing.
- **Provider lab routing:** If the source document has provider lab routing entries, the component shall route the new document (as type `"DOC"`) to one of the providers found in those entries.
- **Queue assignment:** The component shall link the new document to the specified queue (or queue `1` by default).

#### FR-SPL-9: Response

The response shall depend on the routing state:
- **If provider lab routing AND patient routing both existed for the source document:** The HTTP response shall trigger the close-and-reload view (the parent window reloads and the popup closes).
- **If either routing was absent:** The HTTP response body shall be JSON with content type `application/json`, containing a single field `newDocNum` with the string ID of the newly created document.

#### FR-SPL-10: Error handling

On any error, the HTTP response shall have no body and no error message shall be returned to the client.

---

## 4. Non-Functional Requirements

### 4.1 Security

The following security behaviors are **required for any reimplementation** per CARLOS EMR standards:

- **NFR-SEC-1:** The component shall verify the authenticated user has appropriate read/write privileges on the document security object before executing any operation. The component shall deny access with a security exception if unauthorized.
- **NFR-SEC-2:** The component shall validate all filesystem paths constructed from document metadata to prevent path traversal attacks.
- **NFR-SEC-3:** The component shall validate that the `document` parameter is a numeric value and that `page` parameter values conform to the expected format and bounds.
- **NFR-SEC-4:** The component shall apply context-appropriate output encoding to any user-supplied values included in responses.

### 4.2 Data Integrity

- **NFR-DI-1:** The split operation shall not modify the source document or its PDF file.
- **NFR-DI-2:** Rotate and remove operations shall overwrite the original PDF file in place. The document metadata record shall remain consistent with the file contents (e.g., page count after removal).

### 4.3 Reliability

- **NFR-REL-1:** If a PDF file save fails during a rotate or remove operation, the component should not update the database page count.
- **NFR-REL-2:** PDF file handles shall be closed after use, even when errors occur.

---

## 5. External Dependencies

The component requires the following capabilities from the surrounding system. How these are organized (one service, many services, direct database access, etc.) is an implementation choice.

- Invalidate cached page renderings for a given document and page number
- Query and create control document records
- Look up, create, and update document metadata records
- Read a configured filesystem directory path for document storage
- Create new document records with system-assigned unique IDs; update page counts; produce unique filenames from a given stem
- Query and create patient-demographic linkages for documents
- Read, create, modify, and save PDF documents; manipulate individual pages and their rotation
- Query which providers have routing for a given document; add new routing entries
- Query and create provider-level lab routing entries for documents
- Associate a document with a named queue
- Retrieve the currently authenticated provider's identity from the HTTP session

---

## 6. Response Summary

| Operation | HTTP Response |
|-----------|--------------|
| Remove first page | No body |
| Remove first page (single-page guard) | No body |
| Rotate 180 | No body |
| Rotate 90 | No body |
| Split (both routings exist) | Close-and-reload view |
| Split (either routing absent) | JSON: `{ "newDocNum": "<id>" }` |
| Split (error) | No body |
| Split (no pages selected) | Close-and-reload view |
| Unknown/missing method | Default success view |

---

## 7. Client Integration Contract

### 7.1 Rotate and Remove Operations

The client sends a POST request and expects no response body. The client is responsible for refreshing the document view after completion.

### 7.2 Split Operation

The client sends a POST with `page` multi-value parameters (in `pageNumber,rotation` format), the source `document` ID, and optionally `queueID`. The client shall handle two response types:
- **JSON response** (`application/json`): Extract the `newDocNum` field to obtain the new document's ID.
- **Close-and-reload view**: The parent window reloads and the current view closes.

---

## 8. Assumptions

- The document storage directory exists and is writable by the application server process.
- All referenced document IDs correspond to existing PDF files in the document storage directory.
- The authenticated provider session is valid and contains a provider number.
- The PDF processing library can handle the PDF files stored in the system (standard PDF format, not encrypted or password-protected).

---

## 9. Verification Criteria

An implementation shall be considered correct if it satisfies all of the following observable tests:

| Test ID | Operation | Verification |
|---------|-----------|-------------|
| V-RFP-1 | Remove first page | A 3-page PDF becomes a 2-page PDF after the operation. The former second page is now the first page. The database page count reflects the new count. |
| V-RFP-2 | Remove first page (guard) | A 1-page PDF is unchanged after the operation. |
| V-R180-1 | Rotate 180 | All pages in the PDF are visually upside-down relative to their prior orientation. The file is modified in place. |
| V-R90-1 | Rotate 90 | All pages in the PDF are rotated 90 degrees clockwise relative to their prior orientation. The file is modified in place. |
| V-SPL-1 | Split (pages 2,3 from a 5-page doc) | A new document record is created with 2 pages. The new PDF contains only the selected pages in the specified order. The source document is unchanged. |
| V-SPL-2 | Split (routing) | The new document appears in the same provider inboxes as the source document. The authenticated provider also has inbox routing. |
| V-SPL-3 | Split (patient link) | If the source was linked to a patient, the new document is also linked to the same patient. |
| V-SPL-4 | Split (queue) | The new document is assigned to the specified queue, or queue 1 if none specified. |
| V-SPL-5 | Split (JSON response) | When either provider lab routing or patient routing is absent, the response is JSON containing `newDocNum`. |
| V-SPL-6 | Split (view response) | When both provider lab routing and patient routing exist, the response triggers the close-and-reload view. |
| V-SPL-7 | Split (with rotation) | When extracting a page with rotation value 90, the page in the new PDF has absolute rotation 90 regardless of the page's original rotation in the source PDF. |
| V-R180-2 | Rotate 180 (additive) | A page with existing rotation 90 becomes rotation 270 after the operation (90 + 180 = 270). |
| V-CACHE-1 | Rotate and remove | After any rotate or remove operation, previously cached page renderings are invalidated. |

---

## 10. Observed Behaviors (Non-Normative)

These notes document externally observable characteristics of the existing system that are not captured by the normative requirements above. They are provided for compatibility reference. An implementer should replicate these behaviors unless there is a clear reason to improve upon them.

- **Cache invalidation scope** — Rotate and remove operations are observed to invalidate cached renderings for **all** pages in the document, not just the affected pages. This is observable as a brief delay when re-rendering unmodified pages.
- **Filename inheritance** — The new document created by split is observed to reuse the source document's original filename as a stem, with a date-time prefix prepended for uniqueness. This is observable in the stored filename.
- **Provider lab routing selection** — When the source document has multiple provider lab routing entries, the system is observed to route the new document to the first provider returned by the query. The query ordering is not guaranteed, so this selection is effectively arbitrary.
