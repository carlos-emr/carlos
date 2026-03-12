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
> **Methodology references:**
> - [AI Could Be Your Next Team for Clean Room Development (Copyleft Currents)](https://heathermeeker.com/2025/03/28/ai-could-be-your-next-team-for-clean-room-development/)
> - [Clean-room design (Wikipedia)](https://en.wikipedia.org/wiki/Clean-room_design)
> - [How Clean Room Reverse Engineering Built the Modern Tech Industry (NTARI)](https://www.ntari.org/post/how-clean-room-reverse-engineering-built-the-modern-tech-industry)

**Component name:** SplitDocument2Action
**Layer:** Web action (HTTP request handler)
**Framework pattern:** Struts 2 method-dispatch action (2Action convention)
**Domain:** Electronic document management within an EMR system

---

## 1. Purpose

This component provides four PDF manipulation operations accessible via HTTP:

| Operation | Summary |
|-----------|---------|
| **Remove first page** | Delete the first page of a multi-page PDF in place |
| **Rotate 180** | Rotate every page of an existing PDF by 180 degrees in place |
| **Rotate 90** | Rotate every page of an existing PDF by 90 degrees clockwise in place |
| **Split** | Extract user-selected pages (with optional per-page rotation) from an existing PDF, producing a new standalone document |

---

## 2. HTTP Interface

### 2.1 URL

The action is mapped to a single URL endpoint under the document manager namespace. All four operations share this endpoint and are distinguished by a request parameter.

### 2.2 HTTP Method

POST (all operations mutate state).

### 2.3 Method Dispatch

A request parameter named `method` selects the operation:

| `method` value | Operation invoked |
|----------------|-------------------|
| `removeFirstPage` | Remove first page operation |
| `rotate180` | Rotate 180 operation |
| `rotate90` | Rotate 90 operation |
| `split` | Split operation |
| *(missing or unrecognized)* | No-op; return default success |

---

## 3. Operation Specifications

### 3.1 Remove First Page

#### Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the PDF to modify |

#### Behavior

1. **Source document lookup:** Retrieve the document metadata from the database.
2. **PDF reading:** Open the PDF file from the configured document storage directory.
3. **Guard:** If the PDF has only one page (or zero pages), take no action and return no named result (null).
4. **Cache invalidation:** For every page in the document (before removal), invalidate any cached rendered version.
5. **File permissions:** Make the file world-readable, world-writable, and world-executable.
6. **Page removal:** Remove the first page (index 0) from the PDF.
7. **Save:** Overwrite the original file with the modified PDF.
8. **Page count update:** If the save succeeded, decrement the document's page count in the database by one.
9. **Response:** Return no named result (null). No response body is written.

#### Error handling

Exceptions propagate to the framework (declared as thrown).

---

### 3.2 Rotate 180

#### Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the PDF to rotate |

#### Behavior

1. **Source document lookup:** Retrieve the document metadata from the database.
2. **PDF reading:** Open the PDF file from the configured document storage directory.
3. **Page transformation:** For every page in the PDF:
   - Add 180 degrees to the page's current rotation value (modulo 360).
   - Invalidate any cached rendered version of that page.
4. **File permissions:** Make the file world-readable, world-writable, and world-executable before saving.
5. **Save:** Overwrite the original file on disk with the modified PDF.
6. **Response:** Return no named result (null). No response body is written. The client is expected to refresh independently.

#### Error handling

Exceptions propagate to the framework (declared as thrown).

---

### 3.3 Rotate 90

#### Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the PDF to rotate |

#### Behavior

Identical to Rotate 180, except:
- Each page's rotation is incremented by **90 degrees** (modulo 360) instead of 180.
- File permissions are **not** explicitly set before modification (note: this is an observed behavioral asymmetry).

#### Error handling

Exceptions propagate to the framework (declared as thrown).

---

### 3.4 Split

#### Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the source PDF document |
| `page` (multi-valued) | String array, each entry formatted as `pageNumber,rotationDegrees` | Yes | Ordered list of pages to extract. Page numbers are 1-based. Rotation is in degrees (0, 90, 180, 270). |
| `queueID` | String (numeric queue ID) | No | Document queue to assign the new document to. Defaults to `"1"` if absent or empty. |

#### Behavior

**Phase 1 — Read source and build new PDF** (steps are causally ordered):

1. **Authentication context:** Obtain the currently authenticated provider from the HTTP session.
2. **Source document lookup:** Retrieve the source document's metadata record from the database using the provided document ID.
3. **PDF reading:** Open the source PDF file from the configured document storage directory on the filesystem.
4. **Page extraction:** For each entry in the page selection array (processed in array order):
   - Parse the page number and rotation value from the comma-separated string.
   - Retrieve the specified page from the source PDF (1-based index).
   - Apply the specified rotation to that page.
   - Append the page to a new PDF document.
5. **Guard:** If no pages were added to the new document, skip all subsequent steps.

**Phase 2 — Register new document** (steps are causally ordered):

6. **New document metadata creation:** Create a new document metadata record with the following fields (alphabetical):
   - Content type: `application/pdf`
   - Creator: the currently authenticated provider
   - Description: empty
   - Filename stem: same as the source document (the system prepends a timestamp automatically)
   - HTML content: empty
   - Module: `"demographic"`, module ID: `"-1"`
   - Observation date: current date (formatted as `yyyy-MM-dd`)
   - Page count: number of pages in the new PDF
   - Responsible party: the source document's original creator
   - Source: empty
   - Status: active
   - Type: empty
   - Visibility: private (not public)
7. **Persist new document:** Save the metadata record to the database. This generates a new document ID.
8. **Write PDF to filesystem:** Save the new PDF to the document storage directory using the generated filename.

**Phase 3 — Routing and linking** (the following tasks are independent of each other and may execute in any order):

- **Control document cloning:** If the source document has a control document record (module assignment with status), create an equivalent control document record for the new document, preserving the module ID and status from the source.
- **Patient routing:** If the source document is linked to a patient (demographic), create the same patient linkage for the new document.
- **Provider inbox routing:** Copy all existing provider inbox routing entries from the source document to the new document. Additionally, add the currently authenticated provider to the new document's inbox routing.
- **Provider lab routing:** If the source document has provider lab routing entries, route the new document to the first provider found in those entries.
- **Queue assignment:** Link the new document to the specified queue (or queue `1` by default).

**Phase 4 — Response:**

The response behavior depends on the routing state:
- **If provider lab routing AND patient routing both existed:** Return a named result that triggers the close-and-reload view (the parent window reloads and the popup closes).
- **If either routing was absent:** Write a JSON response directly to the HTTP output stream with content type `application/json`. The JSON object contains a single field `newDocNum` whose value is the string ID of the newly created document. Return no named result (null), which prevents the framework from rendering a view.

#### Error handling

On any exception during the split operation, log the error and return no named result (null). No error response is sent to the client.

---

## 4. External Dependencies

The component requires the following capabilities from the surrounding system:

| Capability | Purpose |
|------------|---------|
| **Cache service** | Invalidate cached page renderings for a given document and page number |
| **Control document service** | Query and create control document records (module assignment metadata) |
| **Document metadata store** | CRUD operations on document records (lookup by ID, persist new records, merge updates) |
| **Document storage directory** | A filesystem directory configured at the application level where PDF files are stored |
| **Document utility service** | Register new documents in the database (returns generated ID); update page counts; generate timestamped filenames |
| **Patient lab routing service** | Query and create patient-demographic linkages for documents |
| **PDF processing library** | Read, create, modify, and save PDF documents; manipulate individual pages and their rotation |
| **Provider inbox routing service** | Query which providers have routing for a given document; add new routing entries |
| **Provider lab routing service** | Query and create provider-level lab routing entries for documents |
| **Queue-document linking service** | Associate a document with a named queue |
| **Session/authentication service** | Retrieve the currently authenticated provider's identity from the HTTP session |

---

## 5. Response Summary

| Operation | Named result returned | Direct HTTP response body |
|-----------|----------------------|---------------------------|
| Remove first page | `null` | None |
| Rotate 180 | `null` | None |
| Rotate 90 | `null` | None |
| Split (both routings exist) | `"success"` → close-and-reload view | None |
| Split (either routing absent) | `null` (no view) | JSON: `{ "newDocNum": "<id>" }` |
| Split (error) | `null` | None |
| Split (no pages selected) | `"success"` → close-and-reload view | None |
| Unknown/missing method | `"success"` → default | None |

---

## 6. Security Requirements

The following security behaviors are **required for any reimplementation** per CARLOS EMR standards (these are normative requirements, not observations of the existing implementation):

1. **Authorization check:** Before executing any operation, verify the authenticated user has appropriate read/write privileges on the document security object (e.g., `_edoc` or `_doc`). Deny access with a security exception if unauthorized.
2. **Input validation:** The `document` parameter must be validated as a numeric value. The `page` parameter values must be validated for correct format and bounds.
3. **OWASP encoding:** Any user-supplied values included in responses must be properly encoded for the output context.
4. **Path validation:** All filesystem paths constructed from document metadata must be validated using the application's path validation utility to prevent path traversal attacks.

---

## 7. Client Integration Contract

### 7.1 Rotate and Remove Operations

These are invoked as AJAX calls from document queue management interfaces. The client refreshes the document view after the call completes. No response body is expected.

### 7.2 Split UI Flow

1. The user opens a split interface that displays thumbnail images of each page in the source document.
2. The user selects pages, reorders them via drag-and-drop, and optionally rotates individual pages.
3. On save, the client sends an AJAX POST with the selected pages (as `page` multi-value parameters in `pageNumber,rotation` format), the source `document` ID, and the `queueID`.
4. On success, if the response is JSON, the client extracts `newDocNum` and opens a document viewer for the newly created document.
5. If the response triggers the close-and-reload view, the parent window reloads and the popup closes automatically.

---

## 8. Behavioral Notes

These notes document observable behavioral characteristics for completeness:

- **Cache invalidation scope** — rotate and remove operations invalidate cached renderings for all pages in the document, not just affected pages.
- **File permissions asymmetry** — the rotate-180 and remove-first-page operations set broad file permissions before modifying the file. The rotate-90 operation does not.
- **Filename inheritance** — the new document created by split reuses the source document's original filename as a stem; the system prepends a date-time prefix to ensure uniqueness.
- **In-place modification** — rotate and remove operations overwrite the original PDF file on disk. The source document is never modified by split.
- **Queue default** — if no queue is specified, the new document is assigned to queue ID 1.
- **Response path divergence** — the split operation returns different response types depending on whether the source document had both provider lab routing and patient routing. This determines whether the client receives JSON or a view redirect.
- **Routing duplication** — split copies the source document's routing associations to the new document, ensuring the new document appears in the same inboxes and patient records.
- **Single-page guard** — the remove-first-page operation silently does nothing if the document has only one page.
