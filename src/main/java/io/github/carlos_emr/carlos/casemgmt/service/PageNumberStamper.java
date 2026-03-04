/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.casemgmt.service;

import org.openpdf.text.Document;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;

/**
 * OpenPDF page event handler that stamps "Page X of Y" centered in the footer
 * of each page. Uses a deferred {@link PdfTemplate} to fill in the total page
 * count when the document is closed.
 *
 * <p>The stamper works in three phases:
 * <ol>
 *   <li>{@link #onOpenDocument} -- creates a blank template for the total count</li>
 *   <li>{@link #onEndPage} -- writes "Page N of " plus the template placeholder on every page</li>
 *   <li>{@link #onCloseDocument} -- fills the template with the actual total page count</li>
 * </ol>
 *
 * @see FooterSupport
 * @see PromoTextStamper
 * @since 2012-09-10
 */
public class PageNumberStamper extends FooterSupport {

    /** Deferred template that receives the total page count when the document closes. */
    protected PdfTemplate total;

    /**
     * Creates a new stamper with the specified vertical offset from the page bottom.
     *
     * @param offset int the vertical distance (in points) below the document bottom margin
     */
    public PageNumberStamper(int offset) {
        setBaseOffset(offset);
    }

    /**
     * Writes the "Page X of " text centered on the page footer and appends
     * the deferred total-page-count template.
     *
     * @param writer PdfWriter the active PDF writer for the document
     * @param document Document the current OpenPDF document
     */
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        cb.saveState();

        String text = "Page " + writer.getPageNumber() + " of ";

        // height where text starts
        float textBase = document.bottom() - getBaseOffset();
        float textSize = getFont().getWidthPoint(text, getFontSize());
        float width = document.getPageSize().getWidth();
        float center = width / 2.0f;

        cb.beginText();
        cb.setFontAndSize(getFont(), getFontSize());

        cb.setTextMatrix(document.left(), textBase);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, text, center, textBase, 0);
        cb.endText();
        cb.addTemplate(total, center + (textSize / 2.0f), textBase);

        cb.restoreState();
    }

    /**
     * Fills the deferred template with the final total page count. Called once
     * by OpenPDF when the document is closed.
     *
     * @param writer PdfWriter the active PDF writer
     * @param document Document the document being closed
     */
    public void onCloseDocument(PdfWriter writer, Document document) {
        total.beginText();
        total.setFontAndSize(getFont(), getFontSize());
        total.setTextMatrix(0, 0);
        total.showText(String.valueOf(writer.getPageNumber()));
        total.endText();
    }

    /**
     * Initializes the deferred template used for the total page count.
     * Called once by OpenPDF when the document is first opened.
     *
     * @param writer PdfWriter the active PDF writer
     * @param document Document the document being opened
     */
    public void onOpenDocument(PdfWriter writer, Document document) {
        total = writer.getDirectContent().createTemplate(100, 100);
        total.setBoundingBox(new Rectangle(-40, -40, 100, 100));
    }

    public PdfTemplate getTotal() {
        return total;
    }

    public void setTotal(PdfTemplate total) {
        this.total = total;
    }

}
