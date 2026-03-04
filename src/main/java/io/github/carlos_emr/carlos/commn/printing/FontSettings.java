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
package io.github.carlos_emr.carlos.commn.printing;

import org.openpdf.text.pdf.BaseFont;

/**
 * Immutable font configuration for PDF generation throughout CARLOS EMR.
 *
 * <p>Encapsulates OpenPDF {@link BaseFont} parameters (font name, code page, embedding flag,
 * and point size) so that PDF-producing classes share consistent typography without
 * duplicating font-creation logic.</p>
 *
 * <p>Pre-defined constants cover the most common sizes used across clinical reports,
 * lab PDFs, and printed forms:</p>
 * <ul>
 *   <li>{@link #HELVETICA_6PT} &mdash; fine print (footers, page-event stampers)</li>
 *   <li>{@link #HELVETICA_10PT} &mdash; standard body text (lab reports, clinical notes)</li>
 *   <li>{@link #HELVETICA_12PT} &mdash; headings and titles</li>
 * </ul>
 *
 * @see PdfWriterFactory
 * @since 2012-02-20
 */
public class FontSettings {

    /** Helvetica 6 pt &mdash; footers and fine print. */
    public static final FontSettings HELVETICA_6PT = new FontSettings();
    /** Helvetica 10 pt &mdash; standard body text for lab reports and clinical notes. */
    public static final FontSettings HELVETICA_10PT = new FontSettings();
    /** Helvetica 12 pt &mdash; headings and titles. */
    public static final FontSettings HELVETICA_12PT = new FontSettings();

    static {
        HELVETICA_6PT.fontSize = 6;
        HELVETICA_10PT.fontSize = 10;
        HELVETICA_12PT.fontSize = 12;
    }

    private String font = BaseFont.HELVETICA;
    private String codePage = BaseFont.WINANSI;
    private boolean embedded = BaseFont.NOT_EMBEDDED;
    private int fontSize;

    /**
     * Creates a default FontSettings with Helvetica, WinAnsi code page, and no embedding.
     * Point size must be set separately (see pre-defined constants or the full constructor).
     */
    public FontSettings() {
    }

    /**
     * Creates a fully specified FontSettings.
     *
     * @param font     OpenPDF font name (e.g. {@link BaseFont#HELVETICA}, {@link BaseFont#TIMES_ROMAN})
     * @param codePage character encoding (e.g. {@link BaseFont#WINANSI}, {@link BaseFont#CP1252})
     * @param embedded {@code true} to embed the font in the PDF, {@code false} otherwise
     * @param fontSize point size for text rendering
     */
    public FontSettings(String font, String codePage, boolean embedded, int fontSize) {
        this.font = font;
        this.codePage = codePage;
        this.embedded = embedded;
        this.fontSize = fontSize;
    }

    /**
     * Creates the OpenPDF {@link BaseFont} represented by this configuration.
     *
     * @return BaseFont a new base font instance ready for use with {@link org.openpdf.text.pdf.PdfContentByte}
     * @throws RuntimeException if the underlying {@link BaseFont#createFont} call fails
     *                          (e.g. unsupported font name or code page)
     */
    public BaseFont createFont() {
        try {
            return BaseFont.createFont(getFont(), getCodePage(), isEmbedded());
        } catch (Exception e) {
            throw new RuntimeException("Unable to create font", e);
        }
    }

    /**
     * @return int the point size for this font configuration
     */
    public int getFontSize() {
        return fontSize;
    }

    /**
     * @return String the OpenPDF font name (e.g. "Helvetica")
     */
    public String getFont() {
        return font;
    }

    /**
     * @return String the character encoding code page (e.g. "Cp1252")
     */
    public String getCodePage() {
        return codePage;
    }

    /**
     * @return boolean {@code true} if the font should be embedded in the PDF
     */
    public boolean isEmbedded() {
        return embedded;
    }

}
