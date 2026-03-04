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
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;

/**
 * OpenPDF page event handler that stamps promotional or clinic-specific text
 * centered in the footer of each page. Typically used to display the clinic name,
 * tagline, or other branding information at the bottom of printed documents.
 *
 * @see FooterSupport
 * @see PageNumberStamper
 * @since 2011-04-21
 */
public class PromoTextStamper extends FooterSupport {

    private String text;

    /**
     * Creates a new stamper with the specified promotional text and vertical offset.
     *
     * @param promoText String the text to stamp on each page footer
     * @param offset    int the vertical distance (in points) below the document bottom margin
     */
    public PromoTextStamper(String promoText, int offset) {
        setBaseOffset(offset);
        this.text = promoText;
    }

    /**
     * Writes the promotional text centered in the page footer.
     *
     * @param writer PdfWriter the active PDF writer for the document
     * @param document Document the current OpenPDF document
     */
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        cb.saveState();

        float textBase = document.bottom() - getBaseOffset();
        float width = document.getPageSize().getWidth();
        float center = width / 2.0f;

        cb.beginText();
        cb.setFontAndSize(getFont(), getFontSize());

        cb.setTextMatrix(document.left(), textBase);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, text, center, textBase, 0);
        cb.endText();
        cb.restoreState();
    }


}
