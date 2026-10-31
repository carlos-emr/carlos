/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a finished PDF before it is handed to a caller (the "check" step of an attempt). An invalid
 * result fails the attempt, which the ladder then retries on a simpler rung, so a caller never gets
 * bytes that are not a US Letter PDF.
 *
 * <p>The checks: the bytes start with {@code %PDF-}; PDFBox can load them and finds at least one page;
 * every page is 612 x 792 pt; there is no AcroForm (a flattened document has none, and a form field
 * would survive into the fax and the document store). Messages name the failed check only, never
 * content.</p>
 *
 * @since 2026-10-29
 */
@Component
class RenderedPdfValidator {

    private static final byte[] PDF_HEADER = {'%', 'P', 'D', 'F', '-'};

    /** A page size may differ from Letter by this much (points) before it fails. */
    private static final float PAGE_SIZE_TOLERANCE_PT = 0.01f;

    /** Pages named in the messages; a document with hundreds of wrong pages names the first few. */
    private static final int MAX_PAGE_MESSAGES = 5;

    /**
     * The outcome of the checks.
     *
     * @param messages one line for each failed check, empty when the PDF is valid
     * @param valid whether every check passed
     */
    record Result(List<String> messages, boolean valid) {
        Result {
            messages = List.copyOf(messages);
        }
    }

    /**
     * Runs the checks.
     *
     * @param pdf the finished document
     * @return the result; never throws for bad input
     */
    Result validate(byte[] pdf) {
        List<String> messages = new ArrayList<>();
        if (!startsWithPdfHeader(pdf)) {
            messages.add("The output is not a PDF (no %PDF- header).");
            return new Result(messages, false);
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() == 0) {
                messages.add("The PDF has no pages.");
            }
            int reported = 0;
            int pageNumber = 0;
            for (PDPage page : document.getPages()) {
                pageNumber++;
                PDRectangle box = page.getMediaBox();
                if (!isLetter(box) && reported++ < MAX_PAGE_MESSAGES) {
                    messages.add("Page " + pageNumber + " is " + box.getWidth() + " x " + box.getHeight()
                            + " pt, not US Letter (612 x 792 pt).");
                }
            }
            if (document.getDocumentCatalog().getAcroForm() != null) {
                messages.add("The PDF contains an AcroForm.");
            }
        } catch (IOException | RuntimeException e) {
            messages.add("The PDF cannot be read (" + e.getClass().getSimpleName() + ").");
        }
        return new Result(messages, messages.isEmpty());
    }

    private static boolean startsWithPdfHeader(byte[] pdf) {
        if (pdf == null || pdf.length < PDF_HEADER.length) {
            return false;
        }
        for (int i = 0; i < PDF_HEADER.length; i++) {
            if (pdf[i] != PDF_HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLetter(PDRectangle box) {
        return Math.abs(box.getWidth() - PageZoomCalculator.LETTER_WIDTH_PT) <= PAGE_SIZE_TOLERANCE_PT
                && Math.abs(box.getHeight() - PageZoomCalculator.LETTER_HEIGHT_PT) <= PAGE_SIZE_TOLERANCE_PT;
    }
}
