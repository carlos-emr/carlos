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

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.github.carlos_emr.carlos.casemgmt.service.PageNumberStamper;
import io.github.carlos_emr.carlos.casemgmt.service.PromoTextStamper;

import io.github.carlos_emr.OscarProperties;

/**
 * Factory for creating pre-configured {@link org.openpdf.text.pdf.PdfWriter} instances
 * used throughout CARLOS EMR for clinical PDF generation.
 *
 * <p>Each writer produced by {@link #newInstance} is automatically equipped with a
 * {@link org.openpdf.text.pdf.events.PdfPageEventForwarder} that chains three page-event
 * stampers (all rendered on every page):</p>
 * <ol>
 *   <li><strong>Confidentiality statement</strong> &mdash; from the {@code confidentialityStatement}
 *       property (displayed at y-offset 30)</li>
 *   <li><strong>Promotional / clinic text</strong> &mdash; from the {@code FORMS_PROMOTEXT} property
 *       with the current date appended (displayed at y-offset 20)</li>
 *   <li><strong>Page numbers</strong> &mdash; rendered at y-offset 10 via {@link PageNumberStamper}</li>
 * </ol>
 *
 * <p><strong>Important:</strong> OpenPDF's {@code PdfWriter.setPageEvent()} silently overwrites
 * any previously set handler. This factory uses {@code PdfPageEventForwarder} to ensure all
 * stampers coexist. Callers that need additional page events (e.g. {@code LabPDFCreator})
 * should retrieve the existing forwarder via {@code writer.getPageEvent()} and add to it
 * rather than calling {@code setPageEvent()} again.</p>
 *
 * @see FontSettings
 * @see PageNumberStamper
 * @see PromoTextStamper
 * @since 2012-02-20
 */
public class PdfWriterFactory {

    /** Confidentiality statement loaded once from system properties at class init. */
    private static String confidentialtyStatement = OscarProperties.getConfidentialityStatement();
    /** Promotional text (clinic branding) loaded once from system properties at class init. */
    private static String promoText = OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT");

    /**
     * Sets font on a PdfContentByte using the provided FontSettings.
     *
     * @param pdfContentByte PdfContentByte the content byte to configure with the new font
     * @param settings FontSettings specifying font name, code page, embedding, and point size
     * @return PdfContentByte the same content byte instance with the font applied
     * @throws IllegalStateException if {@link org.openpdf.text.pdf.BaseFont#createFont} fails
     *                               (wraps the underlying exception with the font name for diagnostics)
     */
    public static org.openpdf.text.pdf.PdfContentByte setFont(org.openpdf.text.pdf.PdfContentByte pdfContentByte, FontSettings settings) {
        try {
            org.openpdf.text.pdf.BaseFont baseFont = org.openpdf.text.pdf.BaseFont.createFont(settings.getFont(), settings.getCodePage(), settings.isEmbedded());
            pdfContentByte.setFontAndSize(baseFont, settings.getFontSize());
        } catch (Exception e) {
            throw new IllegalStateException("Failed creation of PDF Base Font: " + settings.getFont(), e);
        }
        return pdfContentByte;
    }

    /**
     * Creates a new instance of the PDF writer with promo text, confidentiality
     * statement, and page numbering enabled. Uses {@link org.openpdf.text.pdf.events.PdfPageEventForwarder}
     * to chain multiple page event handlers so all stampers run on each page.
     *
     * @param document the PDF document
     * @param stream the output stream to write to
     * @param settings font settings for the writer
     * @return PdfWriter instance configured with all page event stampers
     * @throws IllegalStateException if the PdfWriter cannot be created
     */
    public static org.openpdf.text.pdf.PdfWriter newInstance(org.openpdf.text.Document document, OutputStream stream, FontSettings settings) {
        org.openpdf.text.pdf.PdfWriter result;
        try {
            result = org.openpdf.text.pdf.PdfWriter.getInstance(document, stream);
        } catch (org.openpdf.text.DocumentException e) {
            throw new IllegalStateException("Unable to create new PdfWriter instance", e);
        }

        // Use PdfPageEventForwarder to chain all stampers — calling setPageEvent()
        // multiple times would silently overwrite the previous handler in OpenPDF.
        org.openpdf.text.pdf.events.PdfPageEventForwarder pageEvents =
                new org.openpdf.text.pdf.events.PdfPageEventForwarder();

        PromoTextStamper pts;

        if (confidentialtyStatement != null && !confidentialtyStatement.isEmpty()) {
            pts = new PromoTextStamper(confidentialtyStatement, 30);
            pts.setFontSize(settings.getFontSize());
            pageEvents.addPageEvent(pts);
        }
        if (promoText != null && !promoText.isEmpty()) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String promoTextDate = promoText + " " + simpleDateFormat.format(new Date());
            pts = new PromoTextStamper(promoTextDate, 20);
            pts.setFontSize(settings.getFontSize());
            pageEvents.addPageEvent(pts);
        }

        PageNumberStamper pns = new PageNumberStamper(10);
        pns.setFontSize(settings.getFontSize());
        pageEvents.addPageEvent(pns);

        result.setPageEvent(pageEvents);

        return result;
    }

}
