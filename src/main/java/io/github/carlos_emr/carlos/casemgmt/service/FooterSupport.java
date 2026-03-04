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

import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPageEventHelper;

/**
 * Base class for PDF page event handlers that render footer content. Provides
 * shared font configuration (family, size) and a configurable vertical offset
 * from the page bottom margin.
 *
 * <p>Subclasses override the appropriate {@link PdfPageEventHelper} callback
 * methods (e.g. {@code onEndPage}) to render their specific footer content
 * using the font and offset provided by this class.
 *
 * <p>Defaults to Helvetica 12pt, WinAnsi encoding, not embedded.
 *
 * @see PageNumberStamper
 * @see PromoTextStamper
 * @since 2012-09-10
 */
public class FooterSupport extends PdfPageEventHelper {

    private int baseOffset;
    private int fontSize;
    private BaseFont font;

    /**
     * Creates a new instance with as the Helvetica 12pt base font.
     */
    public FooterSupport() {
        super();
        setFontSize(12);
        setFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    /**
     * Sets font info that this instance should use
     *
     * @param fontName   Name of the font
     * @param encoding   Text encoding
     * @param isEmbedded Boolean flag indicating if font is embedded
     * @see BaseFont
     */
    public void setFont(String fontName, String encoding, boolean isEmbedded) {
        try {
            font = BaseFont.createFont(fontName, encoding, isEmbedded);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create base font", e);
        }
    }

    /**
     * Gets offset of the text from bottom of the document.
     *
     * @return Returns the offset
     */
    public int getBaseOffset() {
        return baseOffset;
    }

    /** @param baseOffset int the offset in points below the bottom margin */
    public void setBaseOffset(int baseOffset) {
        this.baseOffset = baseOffset;
    }

    /** @return int the font size in points */
    public int getFontSize() {
        return fontSize;
    }

    /** @param fontSize int the font size in points */
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    /** @return BaseFont the font instance used for footer rendering */
    public BaseFont getFont() {
        return font;
    }

    /** @param font BaseFont the font instance to use for footer rendering */
    public void setFont(BaseFont font) {
        this.font = font;
    }
}