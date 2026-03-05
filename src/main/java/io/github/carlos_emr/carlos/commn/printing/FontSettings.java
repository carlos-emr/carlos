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

import java.util.Objects;

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
 * @since 2012-09-10
 */
public final class FontSettings {

    /** Helvetica 6 pt &mdash; footers and fine print. */
    public static final FontSettings HELVETICA_6PT =
            new FontSettings(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 6);
    /** Helvetica 10 pt &mdash; standard body text for lab reports and clinical notes. */
    public static final FontSettings HELVETICA_10PT =
            new FontSettings(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 10);
    /** Helvetica 12 pt &mdash; headings and titles. */
    public static final FontSettings HELVETICA_12PT =
            new FontSettings(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 12);

    private final String font;
    private final String codePage;
    private final boolean embedded;
    private final int fontSize;

    /**
     * Creates a fully specified FontSettings.
     *
     * @param font     String OpenPDF font name (e.g. {@link BaseFont#HELVETICA}, {@link BaseFont#TIMES_ROMAN})
     * @param codePage String character encoding (e.g. {@link BaseFont#WINANSI}, {@link BaseFont#CP1252})
     * @param embedded boolean {@code true} to embed the font in the PDF, {@code false} otherwise
     * @param fontSize int point size for text rendering (must be positive)
     * @throws NullPointerException if {@code font} or {@code codePage} is null
     * @throws IllegalArgumentException if {@code fontSize} is not positive
     */
    public FontSettings(String font, String codePage, boolean embedded, int fontSize) {
        this.font = Objects.requireNonNull(font, "font must not be null");
        this.codePage = Objects.requireNonNull(codePage, "codePage must not be null");
        if (fontSize <= 0) {
            throw new IllegalArgumentException("fontSize must be positive, got: " + fontSize);
        }
        this.embedded = embedded;
        this.fontSize = fontSize;
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
