# Functional Specification: Image-to-PDF Converter

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

This specification defines the externally observable behavior of an image-to-PDF converter component within an electronic medical records (EMR) system. The component converts image files into PDF documents for inclusion in consultation request attachments. It serves as the sole input for a clean room reimplementation.

**Traceability note:** This spec corresponds to the component identified as `ImagePDFCreator` in the existing codebase. This name is provided solely for traceability between the specification and the original system; it does not prescribe a class name, method name, or any internal naming convention for the reimplementation.

### 1.2 Scope

**In scope:**
- Construction from an HTTP request or explicit parameters
- Image format detection and format-specific processing
- Multi-page TIFF image handling
- Output PDF document structure and metadata
- Path security validation against a configured document directory
- Scaling behavior for oversized images
- Single-page image handling for standard formats

**Out of scope:**
- Authentication and session management (provided by the surrounding framework)
- Consultation request assembly (handled by a separate consultation PDF creator)
- Document metadata record creation (handled by calling components)
- The internal implementation approach — any architecture that produces the specified observable behavior is acceptable
- Upload and storage of the original image files (handled by separate components)

### 1.3 Document Conventions

- The word **"shall"** indicates a mandatory requirement.
- The word **"should"** indicates a recommended but optional behavior.
- The phrase **"is observed to"** indicates documented behavior of the existing system that the reimplementation should replicate for compatibility, but which is not part of the formal functional contract.
- All lists of independent items are **alphabetically ordered** to prevent structural mirroring.
- Causally dependent steps are numbered sequentially; independent steps use unordered bullets.

### 1.4 Definitions and Glossary

| Term | Definition |
|------|-----------|
| **Configured document directory** | A server-side filesystem directory, configured at the application level via the `DOCUMENT_DIR` property, where document files are stored. Used as the allowed base directory for path validation. |
| **Image title** | A descriptive string label associated with the image, displayed as a text heading on each page of the generated PDF. |
| **Multi-page image** | An image file format (specifically TIFF) that can contain multiple frames or pages within a single file. Each page is rendered as a separate PDF page. |
| **Page size bounds** | The maximum dimensions (width: 500 points, height: 700 points) within which images are scaled to fit. Images not exceeding these bounds retain their original dimensions. |
| **Path validation** | A security check that verifies a given filesystem path resolves to a location within the configured document directory, preventing path traversal attacks. |
| **Standard image format** | Any image format natively supported by the PDF generation library (e.g., BMP, GIF, JPEG, PNG). Excludes TIFF, which requires special handling. |

---

## 2. Programmatic Interface

### 2.1 Construction

The component shall support two construction modes:

**Mode A — HTTP request construction:**
The component shall accept an HTTP request object and an output stream. It shall read two request attributes:

| Attribute name | Type | Description |
|----------------|------|-------------|
| `imagePath` | String | Absolute filesystem path to the source image file |
| `imageTitle` | String | Descriptive title for the image in the generated PDF |

**Mode B — Direct construction:**
The component shall accept three explicit parameters: an image path string, an image title string, and an output stream.

Both modes shall produce an equivalent component instance ready to generate a PDF.

### 2.2 PDF Generation

The component shall expose a single operation to generate a PDF document from the configured image. This operation writes the resulting PDF to the output stream provided at construction time.

---

## 3. Functional Requirements

### 3.1 Format Detection

#### FR-FMT-1: TIFF detection

The component shall determine whether the source file is a TIFF image by examining the file extension (case-insensitive). The following extensions shall be recognized as TIFF:
- `.tif`
- `.tiff`

All other file extensions shall be treated as standard image formats.

---

### 3.2 Generate PDF from Multi-Page Image

This subsection applies when the source file is a TIFF image (see FR-FMT-1 for format detection).

#### FR-MPI-1: Inputs

The operation uses the image path and image title configured at construction time, plus the output stream provided at construction.

#### FR-MPI-2: Guard conditions

The operation shall raise an error if:
- The TIFF file cannot be opened as an image input stream
- No image reader capable of decoding the TIFF format is available in the runtime
- The TIFF file contains zero readable pages

#### FR-MPI-3: Page iteration

The component shall determine the total number of pages in the TIFF file. For each page (in sequential order from first to last):

1. Read the page as a raster image
2. If the page cannot be read, skip it (the page shall not appear in the output PDF) and log a warning
3. Convert the raster image to a PDF-embeddable image
4. If the image exceeds the page size bounds (width > 500 points OR height > 700 points), scale it to fit within 500 × 700 points while preserving aspect ratio
5. Position the image at absolute coordinates (20, 20) on the PDF page
6. Start a new page in the PDF document
7. Add a text paragraph consisting of the image title followed by ` - page ` and the 1-based page number (e.g., `"CT Scan - page 3"`)
8. Add the image to the page

#### FR-MPI-4: Response

The generated PDF shall be written to the output stream. The PDF shall contain one page per successfully read TIFF page.

#### FR-MPI-5: Error handling

- If the image input stream cannot be created, the component shall raise a document error
- If no compatible image reader is found, the component shall raise a document error
- If the TIFF contains no readable pages, the component shall raise a document error
- Individual unreadable pages shall be skipped without aborting the entire operation

---

### 3.3 Generate PDF from Standard Image

This subsection applies when the source file is NOT a TIFF image (see FR-FMT-1 for format detection).

#### FR-STD-1: Inputs

The operation uses the image path and image title configured at construction time, plus the output stream provided at construction.

#### FR-STD-2: Image loading

The component shall load the image file from the validated filesystem path. If the image cannot be loaded, the component shall raise a document error.

#### FR-STD-3: Image scaling and placement

1. If the image exceeds the page size bounds (width > 500 points OR height > 700 points), scale it to fit within 500 × 700 points while preserving aspect ratio
2. Position the image at absolute coordinates (20, 20) on the PDF page
3. Add the image to the current page

#### FR-STD-4: Response

The generated PDF shall be written to the output stream. The PDF shall contain a single page with the image.

#### FR-STD-5: Error handling

If the image file cannot be loaded (corrupt file, unsupported format, I/O error), the component shall raise a document error wrapping the underlying cause.

---

### 3.4 Path Validation

#### FR-PV-1: Null or empty path

If the image path is null or empty, the component shall raise a document error before any file access occurs.

#### FR-PV-2: Document directory configuration

The component shall read the configured document directory from the application's `DOCUMENT_DIR` configuration property. If this property is not configured (null or empty), the component shall log an error and raise a document error.

#### FR-PV-3: Path traversal prevention

The component shall validate that the image file path resolves to a location within the configured document directory. If the path resolves outside the allowed directory, the component shall log an error and raise a document error with a generic message that does not disclose filesystem details.

---

### 3.5 PDF Document Properties

#### FR-PDF-1: Page size

The generated PDF shall use US Letter page size.

#### FR-PDF-2: Creator metadata

The generated PDF shall contain creator metadata set to `"CARLOS EMR"`.

#### FR-PDF-3: Document lifecycle

The PDF document and its writer shall be properly closed after generation completes, regardless of whether the operation succeeds or fails. The document shall be closed before the writer.

---

## 4. Non-Functional Requirements

### 4.1 Security

- **NFR-SEC-1:** The component shall validate all image file paths against the configured document directory before any file I/O occurs, preventing path traversal attacks.
- **NFR-SEC-2:** Error messages exposed to callers shall not disclose internal filesystem paths or directory structures.

### 4.2 Reliability

- **NFR-REL-1:** The PDF document and writer resources shall be closed after generation completes, ensuring cleanup even when errors occur during image processing.
- **NFR-REL-2:** Image reader resources used for TIFF processing shall be disposed of after use, even when errors occur.

### 4.3 Robustness

- **NFR-ROB-1:** When processing multi-page TIFF files, individual unreadable pages shall be skipped rather than aborting the entire conversion. This allows partial results when some pages are corrupted.

---

## 5. External Dependencies

The component requires the following capabilities from the surrounding system. How these are organized (one service, many services, direct database access, etc.) is an implementation choice.

- Convert raster images to PDF-embeddable image objects
- Create PDF documents with configurable page size, creator metadata, absolute image positioning, and text paragraphs
- Read a configured filesystem directory path (the `DOCUMENT_DIR` property) from application configuration
- Read multi-page TIFF image files, determining page count and extracting individual pages as raster images
- Read standard image files (BMP, GIF, JPEG, PNG) and convert to PDF-embeddable images
- Validate that a filesystem path resolves within an allowed base directory (path traversal prevention)

---

## 6. Response Summary

| Scenario | Output |
|----------|--------|
| Empty image path | Document error raised |
| Image load failure (standard format) | Document error raised |
| Multi-page TIFF (all pages readable) | PDF with N pages, each titled and containing one TIFF page |
| Multi-page TIFF (some pages unreadable) | PDF with only the readable pages; unreadable pages skipped |
| No TIFF reader available | Document error raised |
| Path outside document directory | Document error raised |
| Standard image (within size bounds) | Single-page PDF with image at original size |
| Standard image (oversized) | Single-page PDF with image scaled to fit 500 × 700 points |
| TIFF with zero pages | Document error raised |
| Unconfigured document directory | Document error raised |

---

## 7. Client Integration Contract

### 7.1 Construction

Callers construct the component by providing:
- An image path (either as an HTTP request attribute named `imagePath` or as an explicit string parameter)
- An image title (either as an HTTP request attribute named `imageTitle` or as an explicit string parameter)
- An output stream to receive the generated PDF bytes

### 7.2 PDF Generation

Callers invoke the PDF generation operation. On success, the output stream contains a valid PDF document. On failure, a document error is raised. Callers are responsible for managing the output stream lifecycle (opening before construction, closing after generation).

### 7.3 Wire Protocol Values

| Value | Context | Description |
|-------|---------|-------------|
| `CARLOS EMR` | PDF creator metadata | Embedded in generated PDF documents |
| `DOCUMENT_DIR` | Application configuration property | Filesystem path to the allowed document directory |
| `imagePath` | HTTP request attribute name | Absolute filesystem path to the source image |
| `imageTitle` | HTTP request attribute name | Descriptive title for the image |

---

## 8. Assumptions

- The `DOCUMENT_DIR` configuration property points to a valid, readable directory.
- Image files referenced by callers exist on the filesystem at the specified paths.
- The output stream provided by callers is open and writable.
- The runtime environment includes an image reader capable of decoding TIFF files (e.g., a TIFF plugin for the image I/O subsystem).
- The standard image formats (BMP, GIF, JPEG, PNG) are supported by the PDF generation library.

---

## 9. Verification Criteria

An implementation shall be considered correct if it satisfies all of the following observable tests:

| Test ID | Scenario | Verification |
|---------|----------|-------------|
| V-FMT-1 | TIFF detection (`.tif`) | A file named `image.tif` is processed using the multi-page TIFF path. |
| V-FMT-2 | TIFF detection (`.tiff`) | A file named `image.tiff` is processed using the multi-page TIFF path. |
| V-FMT-3 | TIFF detection (case-insensitive) | A file named `image.TIF` is processed using the multi-page TIFF path. |
| V-FMT-4 | Non-TIFF format | A file named `photo.jpg` is processed using the standard image path. |
| V-MPI-1 | Multi-page TIFF | A 3-page TIFF file produces a 3-page PDF. Each page contains the image title with the correct page number suffix (e.g., `"Title - page 1"`, `"Title - page 2"`, `"Title - page 3"`). |
| V-MPI-2 | TIFF with unreadable page | A 3-page TIFF where page 2 is corrupted produces a 2-page PDF containing pages 1 and 3 (renumbered as page 1 and page 3 in the title text). |
| V-MPI-3 | No TIFF reader | When no TIFF image reader is available, a document error is raised. |
| V-MPI-4 | Empty TIFF | A TIFF file with zero pages raises a document error. |
| V-PDF-1 | Page size | The generated PDF uses US Letter page dimensions. |
| V-PDF-2 | Creator metadata | The generated PDF's creator field contains `"CARLOS EMR"`. |
| V-PDF-3 | Resource cleanup | After generation (success or failure), the PDF document and writer are closed. |
| V-PV-1 | Null image path | A null image path raises a document error before any file access. |
| V-PV-2 | Empty image path | An empty string image path raises a document error before any file access. |
| V-PV-3 | Path traversal blocked | An image path resolving outside the configured document directory raises a document error. |
| V-PV-4 | Unconfigured document directory | When `DOCUMENT_DIR` is not configured, a document error is raised. |
| V-SCL-1 | Oversized image | A 2000×3000 pixel image is scaled to fit within 500×700 points in the output PDF. |
| V-SCL-2 | Normal-sized image | A 400×600 pixel image retains its original dimensions in the output PDF. |
| V-STD-1 | JPEG to PDF | A JPEG image produces a single-page PDF containing the image. |
| V-STD-2 | PNG to PDF | A PNG image produces a single-page PDF containing the image. |
| V-STD-3 | Corrupt image | An unreadable image file raises a document error. |

---

## 10. Observed Behaviors (Non-Normative)

These notes document externally observable characteristics of the existing system that are not captured by the normative requirements above. They are provided for compatibility reference. An implementer should replicate these behaviors unless there is a clear reason to improve upon them.

- **No title for standard images** — Standard (non-TIFF) images are observed to be added to the PDF without a title paragraph. Only multi-page TIFF images receive title text with page numbering. An implementer may choose to add titles for standard images for consistency.
- **Page event participation** — The component is observed to extend a PDF page event handler base type, though it does not override any page lifecycle callbacks. This inheritance is an implementation choice and is not required for correct behavior.
- **TIFF page numbering preserves source indices** — When a TIFF page is skipped due to being unreadable, the page number in the title text reflects the original source page index (1-based), not the sequential output page number. For example, if page 2 of 3 is skipped, the output pages are titled "page 1" and "page 3".
