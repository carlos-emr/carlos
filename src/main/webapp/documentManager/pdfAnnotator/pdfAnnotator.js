/**
 * PDF Annotator for OpenO EMR
 *
 * Integrates PDF.js for PDF rendering and Fabric.js for canvas-based annotations.
 * Allows doctors to draw, highlight, add text, and insert signatures on PDF documents.
 *
 * @since 2026-01-23
 */

/**
 * PdfAnnotator class - Main controller for PDF annotation functionality.
 *
 * Creates a multi-page PDF viewer with Fabric.js canvas overlays for drawing annotations.
 * Annotations can be saved as a new document via the backend API.
 */
class PdfAnnotator {
    /**
     * Creates a new PdfAnnotator instance.
     *
     * @param {string} containerId - The ID of the container element to render into
     * @param {string} documentNo - The document number being annotated
     * @param {string} demographicNo - The patient demographic number
     * @param {string} contextPath - The application context path (e.g., '/oscar')
     */
    constructor(containerId, documentNo, demographicNo, contextPath) {
        this.containerId = containerId;
        this.documentNo = documentNo;
        this.demographicNo = demographicNo;
        this.ctx = contextPath;
        this.fabricCanvases = [];
        this.pdfDoc = null;
        this.scale = 1.5;
        this.currentColor = '#FF0000';
        this.currentStrokeWidth = 2;

        // Set PDF.js worker path
        if (typeof pdfjsLib !== 'undefined') {
            pdfjsLib.GlobalWorkerOptions.workerSrc = this.ctx + '/library/pdfjs/pdf.worker.min.js';
        }
    }

    /**
     * Loads and renders a PDF document for annotation.
     *
     * @param {string} pdfUrl - The URL to fetch the PDF from
     * @returns {Promise<void>}
     */
    async loadDocument(pdfUrl) {
        try {
            // Load PDF with PDF.js
            this.pdfDoc = await pdfjsLib.getDocument(pdfUrl).promise;

            // Render all pages
            for (let i = 1; i <= this.pdfDoc.numPages; i++) {
                await this.renderPage(i);
            }
        } catch (error) {
            console.error('Failed to load PDF:', error);
            alert('Failed to load PDF for annotation. Please try again.');
            throw error;
        }
    }

    /**
     * Renders a single page of the PDF with a Fabric.js canvas overlay.
     *
     * @param {number} pageNum - The page number to render (1-indexed)
     * @returns {Promise<void>}
     */
    async renderPage(pageNum) {
        const page = await this.pdfDoc.getPage(pageNum);
        const viewport = page.getViewport({ scale: this.scale });

        // Create page wrapper div
        const pageWrapper = document.createElement('div');
        pageWrapper.className = 'pdf-page-wrapper';
        pageWrapper.setAttribute('data-page', pageNum);

        // Create PDF canvas (background layer)
        const pdfCanvas = document.createElement('canvas');
        pdfCanvas.width = viewport.width;
        pdfCanvas.height = viewport.height;
        pdfCanvas.className = 'pdf-background';
        pageWrapper.appendChild(pdfCanvas);

        // Render PDF page to background canvas
        await page.render({
            canvasContext: pdfCanvas.getContext('2d'),
            viewport: viewport
        }).promise;

        // Create Fabric.js canvas element (annotation layer)
        const annotationCanvasEl = document.createElement('canvas');
        annotationCanvasEl.id = 'annotationCanvas_' + this.documentNo + '_' + pageNum;
        annotationCanvasEl.width = viewport.width;
        annotationCanvasEl.height = viewport.height;
        pageWrapper.appendChild(annotationCanvasEl);

        // Append to container
        const container = document.getElementById(this.containerId);
        if (container) {
            container.appendChild(pageWrapper);
        }

        // Initialize Fabric.js canvas
        const fabricCanvas = new fabric.Canvas(annotationCanvasEl.id, {
            width: viewport.width,
            height: viewport.height,
            isDrawingMode: false,
            selection: true
        });

        // Set brush defaults
        fabricCanvas.freeDrawingBrush.color = this.currentColor;
        fabricCanvas.freeDrawingBrush.width = this.currentStrokeWidth;

        // Store the PDF canvas as background for visual reference
        fabricCanvas.pdfBackgroundDataUrl = pdfCanvas.toDataURL();

        this.fabricCanvases.push(fabricCanvas);
    }

    /**
     * Enables or disables freehand drawing mode.
     *
     * @param {boolean} enabled - Whether to enable drawing mode
     */
    setDrawingMode(enabled) {
        this.fabricCanvases.forEach(canvas => {
            canvas.isDrawingMode = enabled;
            if (enabled) {
                canvas.freeDrawingBrush.color = this.currentColor;
                canvas.freeDrawingBrush.width = this.currentStrokeWidth;
            }
        });
    }

    /**
     * Enables text mode - clicking on canvas will add a text box.
     *
     * @param {boolean} enabled - Whether to enable text mode
     */
    setTextMode(enabled) {
        if (enabled) {
            this.fabricCanvases.forEach(canvas => {
                canvas.isDrawingMode = false;

                // Remove existing handler if any
                canvas.off('mouse:down');

                // Add click handler for text placement
                canvas.on('mouse:down', (e) => {
                    if (canvas.textModeEnabled) {
                        const pointer = canvas.getPointer(e.e);
                        const text = new fabric.IText('Type here', {
                            left: pointer.x,
                            top: pointer.y,
                            fontSize: 16,
                            fill: this.currentColor,
                            fontFamily: 'Helvetica'
                        });
                        canvas.add(text);
                        canvas.setActiveObject(text);
                        text.enterEditing();
                        canvas.textModeEnabled = false;
                    }
                });
                canvas.textModeEnabled = true;
            });
        } else {
            this.fabricCanvases.forEach(canvas => {
                canvas.textModeEnabled = false;
                canvas.off('mouse:down');
            });
        }
    }

    /**
     * Enables highlight mode - freehand drawing with semi-transparent yellow.
     *
     * @param {boolean} enabled - Whether to enable highlight mode
     */
    setHighlightMode(enabled) {
        this.fabricCanvases.forEach(canvas => {
            canvas.isDrawingMode = enabled;
            if (enabled) {
                canvas.freeDrawingBrush.color = 'rgba(255, 255, 0, 0.3)';
                canvas.freeDrawingBrush.width = 20;
            }
        });
    }

    /**
     * Sets the drawing color.
     *
     * @param {string} color - CSS color string
     */
    setColor(color) {
        this.currentColor = color;
        this.fabricCanvases.forEach(canvas => {
            if (canvas.freeDrawingBrush) {
                canvas.freeDrawingBrush.color = color;
            }
        });
    }

    /**
     * Sets the stroke width for drawing.
     *
     * @param {number} width - Stroke width in pixels
     */
    setStrokeWidth(width) {
        this.currentStrokeWidth = width;
        this.fabricCanvases.forEach(canvas => {
            if (canvas.freeDrawingBrush) {
                canvas.freeDrawingBrush.width = width;
            }
        });
    }

    /**
     * Fetches and inserts the current provider's signature.
     * The signature can be moved and resized by the user.
     *
     * @returns {Promise<void>}
     */
    async insertSignature() {
        try {
            const response = await fetch(this.ctx + '/documentManager/pdfMarkup.do?method=getSignature');
            const result = await response.json();

            if (result.hasSignature && result.signature) {
                // Add signature to the first (currently visible) canvas
                const canvas = this.fabricCanvases[0];
                if (!canvas) {
                    alert('No page available to add signature');
                    return;
                }

                // Create image from base64 signature
                fabric.Image.fromURL(result.signature, (img) => {
                    // Scale signature to reasonable size
                    const maxWidth = 150;
                    if (img.width > maxWidth) {
                        img.scaleToWidth(maxWidth);
                    }

                    img.set({
                        left: 50,
                        top: 50,
                        selectable: true,
                        hasControls: true
                    });

                    canvas.add(img);
                    canvas.setActiveObject(img);
                    canvas.renderAll();
                });
            } else {
                alert('No signature found. Please set up your signature in Provider Preferences.');
            }
        } catch (error) {
            console.error('Failed to load signature:', error);
            alert('Failed to load signature');
        }
    }

    /**
     * Deletes the currently selected object(s) from the active canvas.
     */
    deleteSelected() {
        this.fabricCanvases.forEach(canvas => {
            const activeObjects = canvas.getActiveObjects();
            if (activeObjects && activeObjects.length > 0) {
                activeObjects.forEach(obj => {
                    canvas.remove(obj);
                });
                canvas.discardActiveObject();
                canvas.renderAll();
            }
        });
    }

    /**
     * Clears all annotations from all pages.
     */
    clearAll() {
        if (confirm('Clear all annotations from all pages?')) {
            this.fabricCanvases.forEach(canvas => {
                canvas.clear();
                canvas.renderAll();
            });
        }
    }

    /**
     * Saves the annotated PDF as a new document.
     * Sends the Fabric.js JSON to the backend for flattening into a new PDF.
     *
     * @returns {Promise<void>}
     */
    async saveAnnotatedCopy() {
        // Check if there are any annotations
        let hasAnnotations = false;
        for (const canvas of this.fabricCanvases) {
            if (canvas.getObjects().length > 0) {
                hasAnnotations = true;
                break;
            }
        }

        if (!hasAnnotations) {
            alert('No annotations to save. Please add some annotations first.');
            return;
        }

        // Build annotation data from all canvases
        const annotations = this.fabricCanvases.map((canvas, idx) => ({
            page: idx + 1,
            width: canvas.width,
            height: canvas.height,
            scale: this.scale,
            objects: canvas.toJSON(['selectable', 'hasControls']).objects
        }));

        try {
            // Use URL-encoded format (matching existing patterns in showDocument.jsp)
            const params = new URLSearchParams();
            params.append('method', 'saveAnnotatedCopy');
            params.append('originalDocumentNo', this.documentNo);
            params.append('demographicNo', this.demographicNo);
            params.append('annotationsJson', JSON.stringify(annotations));

            const response = await fetch(this.ctx + '/documentManager/pdfMarkup.do', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            });

            const result = await response.json();

            if (result.success) {
                alert('Annotated copy saved as new document #' + result.newDocumentNo);
                window.location.reload();
            } else {
                alert('Error: ' + (result.error || 'Failed to save'));
            }
        } catch (error) {
            console.error('Save failed:', error);
            alert('Failed to save annotated copy: ' + error.message);
        }
    }

    /**
     * Cleans up resources when the annotator is closed.
     * Disposes of Fabric.js canvases and clears the container.
     */
    destroy() {
        // Clean up Fabric.js canvases
        this.fabricCanvases.forEach(canvas => {
            canvas.dispose();
        });
        this.fabricCanvases = [];

        // Clear container
        const container = document.getElementById(this.containerId);
        if (container) {
            while (container.firstChild) {
                container.removeChild(container.firstChild);
            }
        }

        this.pdfDoc = null;
    }
}

// Export for use in showDocument.jsp
if (typeof window !== 'undefined') {
    window.PdfAnnotator = PdfAnnotator;
}
