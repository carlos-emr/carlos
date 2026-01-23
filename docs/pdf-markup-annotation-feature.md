# PDF Markup and Annotation Feature

> **Feature Branch**: `experimental/pdf-markup`
> **Status**: Implemented
> **Since**: 2026-01-23

## Overview

This feature adds inline PDF annotation capabilities to OpenO EMR, allowing healthcare providers to draw, highlight, type text, and insert signatures directly on PDF documents in the inbox and eChart document views. Annotated documents are saved as new PDF files with annotations permanently flattened into the document.

## Table of Contents

- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [File Structure](#file-structure)
- [Backend Implementation](#backend-implementation)
- [Frontend Implementation](#frontend-implementation)
- [Configuration](#configuration)
- [User Guide](#user-guide)
- [API Reference](#api-reference)
- [Security Considerations](#security-considerations)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## Architecture

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Browser (Frontend)                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌──────────────────┐    ┌────────────────────────┐  │
│  │    PDF.js       │    │    Fabric.js     │    │   pdfAnnotator.js      │  │
│  │  (PDF Render)   │───▶│ (Canvas Drawing) │◀───│   (Controller)         │  │
│  └─────────────────┘    └──────────────────┘    └────────────────────────┘  │
│                                   │                          │               │
│                                   ▼                          ▼               │
│                         ┌──────────────────────────────────────────┐        │
│                         │        showDocument.jsp (UI)              │        │
│                         │  - Annotation Toolbar                     │        │
│                         │  - Document Display                       │        │
│                         │  - Event Handlers                         │        │
│                         └──────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ HTTP POST (JSON annotations)
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Server (Backend)                                │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐    ┌────────────────────────┐                     │
│  │  PdfMarkup2Action    │───▶│  PdfMarkupManagerImpl  │                     │
│  │  (Struts2 Action)    │    │  (Service Layer)       │                     │
│  └──────────────────────┘    └────────────────────────┘                     │
│                                        │                                     │
│                    ┌───────────────────┼───────────────────┐                │
│                    ▼                   ▼                   ▼                │
│          ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│          │  Apache PDFBox  │  │    EDocUtil     │  │  DocumentDao    │     │
│          │ (PDF Flattening)│  │ (Document CRUD) │  │ (Persistence)   │     │
│          └─────────────────┘  └─────────────────┘  └─────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Storage Layer                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐    ┌────────────────────────┐                     │
│  │   DOCUMENT_DIR       │    │   MariaDB/MySQL        │                     │
│  │   (PDF Files)        │    │   document table       │                     │
│  │                      │    │   ctl_document table   │                     │
│  └──────────────────────┘    └────────────────────────┘                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **User clicks "Annotate"** → JavaScript initializes PdfAnnotator
2. **PDF.js loads document** → Renders PDF pages to canvas elements
3. **Fabric.js creates overlay** → Transparent canvas layer on each PDF page
4. **User draws annotations** → Fabric.js captures drawing objects (paths, text, images)
5. **User clicks "Save Annotated Copy"** → Fabric.js JSON serialized and sent to backend
6. **PdfMarkup2Action receives request** → Validates security, delegates to manager
7. **PdfMarkupManagerImpl processes** → Parses JSON, uses PDFBox to draw on PDF
8. **New PDF saved** → Written to DOCUMENT_DIR with new filename
9. **Document record created** → EDocUtil.addDocumentSQL() creates database entry
10. **Response returned** → Frontend receives new document ID, page reloads

---

## Technology Stack

### Frontend Libraries

| Library | Version | License | Purpose |
|---------|---------|---------|---------|
| **PDF.js** | 3.11.174 | Apache 2.0 | Mozilla's PDF rendering library - renders PDF pages to HTML5 canvas |
| **Fabric.js** | 5.3.0 | MIT | Powerful canvas library for interactive drawing and object manipulation |

### Backend Libraries

| Library | Version | License | Purpose |
|---------|---------|---------|---------|
| **Apache PDFBox** | 2.0.35 | Apache 2.0 | PDF manipulation - drawing, text, images on PDF documents |
| **Jackson** | 2.x | Apache 2.0 | JSON parsing for Fabric.js annotation data |

### Why This Stack?

**PDF.js + Fabric.js** was chosen over alternatives like pdf-annotate.js because:

- **Active Maintenance**: Both libraries are actively maintained (pdf-annotate.js last updated 2019)
- **No jQuery Conflicts**: Pure JavaScript, works with existing `jQuery.noConflict()` pattern
- **Traditional Script Loading**: Works with existing `<script>` tag pattern (no bundler required)
- **Rich Drawing Tools**: Fabric.js provides freehand, shapes, text, images natively
- **JSON Serialization**: Fabric.js exports canvas state as JSON for easy storage/transmission
- **Wide Browser Support**: Modern browsers + IE11 fallback

---

## File Structure

### Files Created

```
src/main/java/ca/openosp/openo/
├── managers/
│   ├── PdfMarkupManager.java          # Service interface
│   └── PdfMarkupManagerImpl.java      # PDFBox implementation
└── documentManager/actions/
    └── PdfMarkup2Action.java          # Struts2 action controller

src/main/webapp/
├── documentManager/pdfAnnotator/
│   └── pdfAnnotator.js                # Main annotator JavaScript class
├── css/
│   └── pdfAnnotator.css               # Annotator styling
└── library/
    ├── pdfjs/
    │   ├── pdf.min.js                 # PDF.js core library
    │   └── pdf.worker.min.js          # PDF.js web worker
    └── fabric/
        └── fabric.min.js              # Fabric.js library
```

### Files Modified

```
src/main/webapp/WEB-INF/classes/struts.xml      # Added pdfMarkup action mapping
src/main/resources/applicationContext.xml        # Registered pdfMarkupManager bean
src/main/webapp/documentManager/showDocument.jsp # Added annotation UI
```

---

## Backend Implementation

### PdfMarkupManager Interface

```java
package ca.openosp.openo.managers;

public interface PdfMarkupManager {
    /**
     * Creates an annotated copy of an existing PDF document.
     *
     * @param info LoggedInInfo containing the logged-in user information
     * @param originalDocNo Integer the document ID of the original PDF
     * @param demographicNo Integer the patient demographic number
     * @param annotationsJson String JSON representation of Fabric.js annotations
     * @return Document the newly created Document entity
     * @throws IOException if PDF processing fails
     * @throws SecurityException if user lacks write access
     */
    Document createAnnotatedCopy(LoggedInInfo info, Integer originalDocNo,
                                  Integer demographicNo, String annotationsJson)
                                  throws IOException;
}
```

### PdfMarkupManagerImpl - Key Methods

#### createAnnotatedCopy()

Main entry point that orchestrates the annotation process:

1. Validates security privileges (`_edoc` write access)
2. Loads original PDF document
3. Parses Fabric.js JSON annotations
4. Creates new PDF with annotations flattened
5. Saves new PDF file to DOCUMENT_DIR
6. Creates document record via EDocUtil.addDocumentSQL()
7. Logs action for audit trail

#### drawFabricObjects()

Converts Fabric.js JSON objects to PDFBox drawing operations:

```java
private void drawFabricObjects(PDDocument pdf, PDPage page,
                                JsonNode objects, float scale)
```

Handles object types:
- `path` → Freehand drawing strokes
- `text` / `i-text` → Text annotations
- `rect` → Rectangle/highlight boxes
- `image` → Signature images (base64)

#### Coordinate System Conversion

Fabric.js and PDF use different coordinate systems:

| Aspect | Fabric.js | PDF |
|--------|-----------|-----|
| Origin | Top-left | Bottom-left |
| Y-axis | Increases downward | Increases upward |
| Units | Pixels (scaled) | Points (72 per inch) |

Conversion formula:
```java
float pdfX = fabricX / scale;
float pdfY = pageHeight - (fabricY / scale);
```

### PdfMarkup2Action - Struts2 Action

Follows the 2Action pattern used throughout OpenO EMR:

```java
public class PdfMarkup2Action extends ActionSupport {
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("saveAnnotatedCopy".equals(method)) {
            return saveAnnotatedCopy();
        } else if ("getSignature".equals(method)) {
            return getSignature();
        }
        return SUCCESS;
    }
}
```

#### Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `pdfMarkup.do?method=saveAnnotatedCopy` | Flatten annotations and save as new document |
| GET | `pdfMarkup.do?method=getSignature` | Get provider's signature image (base64) |

---

## Frontend Implementation

### PdfAnnotator Class

The main JavaScript controller class that coordinates PDF rendering and annotation:

```javascript
class PdfAnnotator {
    constructor(containerId, documentNo, demographicNo, contextPath)

    // Document loading
    async loadDocument(pdfUrl)
    async renderPage(pageNum)

    // Drawing modes
    setDrawingMode(enabled)      // Freehand drawing
    setTextMode(enabled)         // Click to add text
    setHighlightMode(enabled)    // Semi-transparent yellow

    // Configuration
    setColor(color)              // Drawing color
    setStrokeWidth(width)        // Line thickness

    // Actions
    insertSignature()            // Load provider signature
    deleteSelected()             // Remove selected objects
    clearAll()                   // Clear all annotations
    saveAnnotatedCopy()          // Save to server
    destroy()                    // Cleanup resources
}
```

### Canvas Layer Architecture

Each PDF page has two canvas layers:

1. **PDF Background Canvas** - Rendered by PDF.js, read-only
2. **Fabric.js Overlay Canvas** - Interactive drawing layer, positioned absolutely over PDF canvas

```html
<div class="pdf-page-wrapper">
    <canvas class="pdf-background"><!-- PDF.js renders here --></canvas>
    <div class="canvas-container"><!-- Fabric.js canvas here --></div>
</div>
```

### Fabric.js Object Types Used

| Fabric.js Type | Use Case | PDFBox Equivalent |
|----------------|----------|-------------------|
| `fabric.Path` | Freehand drawing | `PDPageContentStream.lineTo()` |
| `fabric.IText` | Text annotations | `PDPageContentStream.showText()` |
| `fabric.Rect` | Highlights | `PDPageContentStream.addRect()` |
| `fabric.Image` | Signatures | `PDPageContentStream.drawImage()` |

---

## Configuration

### Struts Configuration (struts.xml)

```xml
<action name="documentManager/pdfMarkup"
        class="ca.openosp.openo.documentManager.actions.PdfMarkup2Action">
    <result name="success">/documentManager/closeWindow.html</result>
</action>
```

### Spring Configuration (applicationContext.xml)

```xml
<bean id="pdfMarkupManager"
      class="ca.openosp.openo.managers.PdfMarkupManagerImpl" />
```

### No Database Schema Changes

This feature requires **no database migrations**. Annotated PDFs are saved as new documents using the existing `EDocUtil.addDocumentSQL()` method, which:

- Creates a `document` table entry
- Creates a `ctl_document` link to the patient demographic
- Uses the same storage pattern as document splitting

---

## User Guide

### Accessing the Annotator

1. Open a PDF document in the inbox or eChart document view
2. Look for the **"Annotate"** button in the document toolbar (near Split, Rotate buttons)
3. Click "Annotate" to enter annotation mode

> **Note**: The Annotate button only appears for PDF documents. Users must have `_edoc` write permission.

### Annotation Toolbar

When annotation mode is active, the toolbar provides:

| Button | Function |
|--------|----------|
| **Draw** | Freehand drawing with selected color/width |
| **Text** | Click on document to add text box |
| **Highlight** | Draw semi-transparent yellow highlights |
| **Signature** | Insert your saved signature |
| **Color Picker** | Choose annotation color |
| **Stroke Width** | Select line thickness (2-8px) |
| **Delete Selected** | Remove selected annotation |
| **Clear All** | Remove all annotations (with confirmation) |
| **Save Annotated Copy** | Save as new document |
| **Cancel** | Exit annotation mode without saving |

### Workflow Example

1. Open a lab result PDF in the inbox
2. Click "Annotate"
3. Select "Draw" and circle an abnormal value in red
4. Select "Text" and click to add a note: "Discuss with patient"
5. Click "Signature" to add your signature
6. Click "Save Annotated Copy"
7. A new document is created with filename: `original_annotated_20260123143022.pdf`
8. The original document remains unchanged

### Signature Setup

To use the signature feature, providers must first set up their signature:

1. Go to **Provider Preferences**
2. Navigate to the **Signature** section
3. Draw or upload your signature
4. Save preferences

The signature is stored in `ProviderExt.signature` as a base64-encoded image.

---

## API Reference

### saveAnnotatedCopy Endpoint

**URL**: `POST /documentManager/pdfMarkup.do?method=saveAnnotatedCopy`

**Content-Type**: `application/x-www-form-urlencoded`

**Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `originalDocumentNo` | Integer | Yes | ID of the original document |
| `demographicNo` | Integer | Yes | Patient demographic number |
| `annotationsJson` | String (JSON) | Yes | Fabric.js canvas data |

**annotationsJson Format**:

```json
[
  {
    "page": 1,
    "width": 918,
    "height": 1188,
    "scale": 1.5,
    "objects": [
      {
        "type": "path",
        "path": [["M", 100, 200], ["L", 150, 250]],
        "stroke": "#FF0000",
        "strokeWidth": 2,
        "left": 100,
        "top": 200
      },
      {
        "type": "i-text",
        "text": "Important note",
        "left": 300,
        "top": 400,
        "fontSize": 16,
        "fill": "#0000FF"
      }
    ]
  }
]
```

**Response (Success)**:

```json
{
  "success": true,
  "newDocumentNo": 12345
}
```

**Response (Error)**:

```json
{
  "success": false,
  "error": "Error message description"
}
```

### getSignature Endpoint

**URL**: `GET /documentManager/pdfMarkup.do?method=getSignature`

**Response**:

```json
{
  "hasSignature": true,
  "signature": "data:image/png;base64,iVBORw0KGgo..."
}
```

---

## Security Considerations

### Access Control

- **Read Access**: Required to view and annotate documents (`_edoc` read privilege)
- **Write Access**: Required to save annotated copies (`_edoc` write privilege)
- Security checks performed via `SecurityInfoManager.hasPrivilege()`

### File Path Security

- All file operations use `PathValidationUtils.validateExistingPath()` to prevent path traversal attacks
- Files are only written to the configured `DOCUMENT_DIR`

### Audit Logging

All annotation saves are logged via `LogAction.addLog()`:

```java
LogAction.addLog(providerNo, LogConst.ADD, LogConst.CON_DOCUMENT,
                 newDocId, info.getIp(), demographicNo.toString());
```

### Data Integrity

- Original documents are never modified
- Annotated copies are new files with clear naming convention
- Full audit trail maintained through existing document infrastructure

---

## Testing

### Manual Testing Checklist

1. **Basic Annotation Flow**
   - [ ] Open PDF document in inbox
   - [ ] Click "Annotate" button
   - [ ] Verify PDF loads in annotation view
   - [ ] Draw freehand annotation
   - [ ] Add text annotation
   - [ ] Add highlight
   - [ ] Insert signature
   - [ ] Save annotated copy
   - [ ] Verify new document created

2. **Tool Functions**
   - [ ] Color picker changes drawing color
   - [ ] Stroke width affects line thickness
   - [ ] Delete Selected removes selected objects
   - [ ] Clear All removes all annotations (with confirmation)
   - [ ] Cancel returns to normal view

3. **Edge Cases**
   - [ ] Multi-page PDF annotations
   - [ ] Large PDF files
   - [ ] PDF with existing annotations
   - [ ] Save with no annotations (should show warning)

4. **Security Testing**
   - [ ] User without `_edoc` write privilege cannot see Annotate button
   - [ ] API rejects requests from unauthorized users
   - [ ] Audit log entries created

### Automated Tests

Unit tests should be created for:

- `PdfMarkupManagerImpl.parseColor()` - Color parsing
- `PdfMarkupManagerImpl.parseColorWithAlpha()` - RGBA parsing
- `PdfMarkupManagerImpl.filterToWinAnsi()` - Text encoding
- `PdfMarkupManagerImpl.createAnnotatedCopy()` - Integration test

---

## Troubleshooting

### Common Issues

#### "Annotate" button not visible

**Causes**:
- Document is not a PDF (check content type)
- User lacks `_edoc` write permission
- Page loaded in non-editable mode

**Solution**: Check user permissions and document type.

#### PDF fails to load in annotator

**Causes**:
- PDF.js worker not loaded
- Network error fetching PDF
- Corrupted PDF file

**Solution**: Check browser console for errors. Verify PDF.js files exist in `/library/pdfjs/`.

#### Annotations not saving

**Causes**:
- Session expired
- Server error
- Large annotation data

**Solution**: Check browser console and server logs. Verify user is still logged in.

#### Signature not appearing

**Causes**:
- Provider has no signature saved
- Signature image corrupted

**Solution**: Set up signature in Provider Preferences.

### Debug Mode

Enable debug logging to troubleshoot issues:

```bash
debug-on
server restart
```

Then check logs:

```bash
server log
```

---

## Future Enhancements

Potential improvements for future versions:

1. **Annotation Templates** - Predefined stamps (Reviewed, Approved, etc.)
2. **Shape Tools** - Circles, arrows, rectangles
3. **Undo/Redo** - History of annotation changes
4. **Collaborative Annotations** - Multiple providers annotating same document
5. **Annotation Comments** - Text notes attached to annotations
6. **Mobile Support** - Touch-friendly annotation interface
7. **OCR Integration** - Searchable text from handwritten annotations

---

## References

- [PDF.js Documentation](https://mozilla.github.io/pdf.js/)
- [Fabric.js Documentation](http://fabricjs.com/docs/)
- [Apache PDFBox Documentation](https://pdfbox.apache.org/)
- [OpenO EMR CLAUDE.md](../CLAUDE.md) - Development guidelines
- [JSP Refactoring Guide](./JSP-REFACTORING-GUIDE.md) - 2Action pattern reference

---

## Changelog

### 2026-01-23 - Initial Implementation

- Created `PdfMarkupManager` interface and `PdfMarkupManagerImpl` service
- Created `PdfMarkup2Action` Struts2 controller
- Created `pdfAnnotator.js` frontend controller
- Added PDF.js 3.11.174 and Fabric.js 5.3.0 libraries
- Integrated annotation UI into `showDocument.jsp`
- Features: Draw, Text, Highlight, Signature, Delete, Clear, Save
