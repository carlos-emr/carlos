# Functional Specification: PDF Document Manipulation Action

> **Clean Room Specification**
>
> This document is a black-box functional specification describing observable behavior only.
> It contains no source code, no internal implementation details, and no copyrightable
> expression from any existing implementation. It is intended to serve as the sole input
> for a clean room reimplementation per the Chinese Wall methodology.
>
> **Methodology references:**
> - [Clean-room design (Wikipedia)](https://en.wikipedia.org/wiki/Clean-room_design)
> - [How Clean Room Reverse Engineering Built the Modern Tech Industry (NTARI)](https://www.ntari.org/post/how-clean-room-reverse-engineering-built-the-modern-tech-industry)
> - [AI Could Be Your Next Team for Clean Room Development (Copyleft Currents)](https://heathermeeker.com/2025/03/28/ai-could-be-your-next-team-for-clean-room-development/)

**Component name:** SplitDocument2Action
**Layer:** Web action (HTTP request handler)
**Framework pattern:** Struts 2 method-dispatch action (2Action convention)
**Domain:** Electronic document management within an EMR system

---

## 1. Purpose

This component provides four PDF manipulation operations accessible via HTTP:

| Operation | Summary |
|-----------|---------|
| **Split** | Extract user-selected pages (with optional per-page rotation) from an existing PDF, producing a new standalone document |
| **Rotate 180** | Rotate every page of an existing PDF by 180 degrees in place |
| **Rotate 90** | Rotate every page of an existing PDF by 90 degrees clockwise in place |
| **Remove first page** | Delete the first page of a multi-page PDF in place |

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
| `split` | Split operation |
| `rotate180` | Rotate 180 operation |
| `rotate90` | Rotate 90 operation |
| `removeFirstPage` | Remove first page operation |
| *(missing or unrecognized)* | No-op; return default success |

---

## 3. Operation Specifications

### 3.1 Split

#### Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the source PDF document |
| `page` (multi-valued) | String array, each entry formatted as `pageNumber,rotationDegrees` | Yes | Ordered list of pages to extract. Page numbers are 1-based. Rotation is in degrees (0, 90, 180, 270). |
| `queueID` | String (numeric queue ID) | No | Document queue to assign the new document to. Defaults to `"1"` if absent or empty. |

#### Behavior

1. **Authentication context:** Obtain the currently authenticated provider from the HTTP session.
2. **Source document lookup:** Retrieve the source document's metadata record from the database using the provided document ID.
3. **PDF reading:** Open the source PDF file from the configured document storage directory on the filesystem.
4. **Page extraction:** For each entry in the page selection array (processed in array order):
   - Parse the page number and rotation value from the comma-separated string.
   - Retrieve the specified page from the source PDF (1-based index).
   - Apply the specified rotation to that page.
   - Append the page to a new PDF document.
5. **Guard:** If no pages were added to the new document, skip all subsequent steps.
6. **New document metadata creation:** Create a new document metadata record with:
   - The same filename stem as the source document (the system prepends a timestamp automatically).
   - The currently authenticated provider as the creator.
   - The source document's original creator as the responsible party.
   - Content type: `application/pdf`.
   - Status: active.
   - Observation date: current date (formatted as `yyyy-MM-dd`).
   - Page count: number of pages in the new PDF.
   - Visibility: private (not public).
   - Module: `"demographic"`, module ID: `"-1"`.
   - Empty description, type, HTML, and source fields.
7. **Persist new document:** Save the metadata record to the database. This generates a new document ID.
8. **Write PDF to filesystem:** Save the new PDF to the document storage directory using the generated filename.
9. **Inbox routing (provider):** Copy all existing provider inbox routing entries from the source document to the new document. Additionally, add the currently authenticated provider to the new document's inbox routing.
10. **Queue assignment:** Link the new document to the specified queue (or queue `1` by default).
11. **Provider lab routing:** If the source document has provider lab routing entries, route the new document to the first provider found in those entries.
12. **Patient routing:** If the source document is linked to a patient (demographic), create the same patient linkage for the new document.
13. **Control document cloning:** If the source document has a control document record (module assignment with status), create an equivalent control document record for the new document, preserving the module ID and status from the source.
14. **Response:** The response behavior depends on the routing state:
    - **If provider lab routing AND patient routing both existed:** Return a named result that triggers the close-and-reload view (the parent window reloads and the popup closes).
    - **If either routing was absent:** Write a JSON response directly to the HTTP output stream with content type `application/json`. The JSON object contains a single field `newDocNum` whose value is the string ID of the newly created document. Return no named result (null), which prevents the framework from rendering a view.

#### Error handling

On any exception during the split operation, log the error and return no named result (null). No error response is sent to the client.

---

### 3.2 Rotate 180

#### Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the PDF to rotate |

#### Behavior

1. **Source document lookup:** Retrieve the document metadata from the database.
2. **PDF reading:** Open the PDF file from the configured document storage directory.
3. **Set file permissions:** Make the file world-readable, world-writable, and world-executable before modification.
4. **Rotation:** For every page in the PDF, add 180 degrees to the page's current rotation value (modulo 360).
5. **Cache invalidation:** For each page, invalidate any cached rendered version of that page.
6. **Save:** Overwrite the original file on disk with the modified PDF.
7. **Response:** Return no named result (null). No response body is written. The client is expected to refresh independently.

#### Error handling

Exceptions propagate to the framework (declared as thrown).

---

### 3.3 Rotate 90

#### Inputs

Same as Rotate 180.

#### Behavior

Identical to Rotate 180, except:
- Each page's rotation is incremented by **90 degrees** (modulo 360) instead of 180.
- File permissions are **not** explicitly set before modification (note: this is an observed behavioral asymmetry).

#### Error handling

Same as Rotate 180.

---

### 3.4 Remove First Page

#### Inputs

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `document` | String (numeric document ID) | Yes | Identifies the PDF to modify |

#### Behavior

1. **Source document lookup:** Retrieve the document metadata from the database.
2. **PDF reading:** Open the PDF file from the configured document storage directory.
3. **Guard:** If the PDF has only one page (or zero pages), take no action and return no named result (null).
4. **Set file permissions:** Make the file world-readable, world-writable, and world-executable.
5. **Cache invalidation:** For every page in the document (before removal), invalidate any cached rendered version.
6. **Page removal:** Remove the first page (index 0) from the PDF.
7. **Save:** Overwrite the original file with the modified PDF.
8. **Page count update:** If the save succeeded, decrement the document's page count in the database by one.
9. **Response:** Return no named result (null). No response body is written.

#### Error handling

Exceptions propagate to the framework (declared as thrown).

---

## 4. External Dependencies

The component requires the following capabilities from the surrounding system:

| Capability | Purpose |
|------------|---------|
| **Document metadata store** | CRUD operations on document records (lookup by ID, persist new records, merge updates) |
| **Document storage directory** | A filesystem directory configured at the application level where PDF files are stored |
| **PDF processing library** | Read, create, modify, and save PDF documents; manipulate individual pages and their rotation |
| **Session/authentication service** | Retrieve the currently authenticated provider's identity from the HTTP session |
| **Provider inbox routing service** | Query which providers have routing for a given document; add new routing entries |
| **Queue-document linking service** | Associate a document with a named queue |
| **Provider lab routing service** | Query and create provider-level lab routing entries for documents |
| **Patient lab routing service** | Query and create patient-demographic linkages for documents |
| **Control document service** | Query and create control document records (module assignment metadata) |
| **Document utility service** | Register new documents in the database (returns generated ID); update page counts; generate timestamped filenames |
| **Cache service** | Invalidate cached page renderings for a given document and page number |

---

## 5. Response Summary

| Operation | Named result returned | Direct HTTP response body |
|-----------|----------------------|---------------------------|
| Split (both routings exist) | `"success"` → close-and-reload view | None |
| Split (either routing absent) | `null` (no view) | JSON: `{ "newDocNum": "<id>" }` |
| Split (error) | `null` | None |
| Split (no pages selected) | `"success"` → close-and-reload view | None |
| Rotate 180 | `null` | None |
| Rotate 90 | `null` | None |
| Remove first page | `null` | None |
| Unknown/missing method | `"success"` → default | None |

---

## 6. Security Requirements

The following security behaviors are **required for any reimplementation** per CARLOS EMR standards (these are normative requirements, not observations of the existing implementation):

1. **Authorization check:** Before executing any operation, verify the authenticated user has appropriate read/write privileges on the document security object (e.g., `_edoc` or `_doc`). Deny access with a security exception if unauthorized.
2. **Path validation:** All filesystem paths constructed from document metadata must be validated using the application's path validation utility to prevent path traversal attacks.
3. **Input validation:** The `document` parameter must be validated as a numeric value. The `page` parameter values must be validated for correct format and bounds.
4. **OWASP encoding:** Any user-supplied values included in responses must be properly encoded for the output context.

---

## 7. Client Integration Contract

### 7.1 Split UI Flow

1. The user opens a split interface that displays thumbnail images of each page in the source document.
2. The user selects pages, reorders them via drag-and-drop, and optionally rotates individual pages.
3. On save, the client sends an AJAX POST with the selected pages (as `page` multi-value parameters in `pageNumber,rotation` format), the source `document` ID, and the `queueID`.
4. On success, if the response is JSON, the client extracts `newDocNum` and opens a document viewer for the newly created document.
5. If the response triggers the close-and-reload view, the parent window reloads and the popup closes automatically.

### 7.2 Rotate and Remove Operations

These are invoked as AJAX calls from document queue management interfaces. The client refreshes the document view after the call completes. No response body is expected.

---

## 8. Behavioral Notes

These notes document observable behavioral characteristics for completeness:

1. **Split produces a new document** — the source document is never modified by the split operation.
2. **Rotate and remove modify in place** — these operations overwrite the original PDF file on disk.
3. **Filename inheritance** — the new document created by split reuses the source document's original filename as a stem; the system prepends a date-time prefix to ensure uniqueness.
4. **Routing duplication** — split copies the source document's routing associations to the new document, ensuring the new document appears in the same inboxes and patient records.
5. **Queue default** — if no queue is specified, the new document is assigned to queue ID 1.
6. **Single-page guard** — the remove-first-page operation silently does nothing if the document has only one page.
7. **Cache invalidation scope** — rotate and remove operations invalidate cached renderings for all pages in the document, not just affected pages.
8. **File permissions** — the rotate-180 and remove-first-page operations set broad file permissions before modifying the file. The rotate-90 operation does not (asymmetry in observed behavior).
9. **Response path divergence in split** — the split operation returns different response types depending on whether the source document had both provider lab routing and patient routing. This determines whether the client receives JSON or a view redirect.
