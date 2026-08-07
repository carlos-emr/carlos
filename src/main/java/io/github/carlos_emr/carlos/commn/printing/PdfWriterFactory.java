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

import io.github.carlos_emr.CarlosProperties;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.events.PdfPageEventForwarder;

/**
 * Factory for creating pre-configured {@link PdfWriter} instances
 * used throughout CARLOS EMR for clinical PDF generation.
 *
 * <p>Each writer produced by {@link #newInstance} is automatically equipped with a
 * {@link PdfPageEventForwarder} that chains up to three page-event
 * stampers (depending on configuration; all enabled stampers render on every page):</p>
 * <ol>
 *   <li><strong>Confidentiality statement</strong> &mdash; from the {@code confidentialityStatement}
 *       property (displayed 30 points below the bottom margin)</li>
 *   <li><strong>Promotional / clinic text</strong> &mdash; from the {@code FORMS_PROMOTEXT} property
 *       with the current date appended (displayed 20 points below the bottom margin)</li>
 *   <li><strong>Page numbers</strong> &mdash; rendered 10 points below the bottom margin via {@link PageNumberStamper}</li>
 * </ol>
 *
 * <p><strong>Important:</strong> OpenPDF 3.x auto-wraps multiple {@code setPageEvent()} calls
 * in a {@code PdfPageEventForwarder}, but this factory creates the forwarder explicitly for
 * clarity and to guarantee deterministic stamper ordering. Callers that need additional page
 * events (e.g. {@code LabPDFCreator}) should retrieve the existing forwarder via
 * {@code writer.getPageEvent()} and add to it for consistency.</p>
 *
 * @see FontSettings
 * @see PageNumberStamper
 * @see PromoTextStamper
 * @since 2012-09-10
 */
public final class PdfWriterFactory {

    /** Confidentiality statement loaded once from system properties at class init. */
    private static final String confidentialityStatement = CarlosProperties.getConfidentialityStatement();
    /** Promotional text (clinic branding) loaded once from system properties at class init. */
    private static final String promoText = CarlosProperties.getInstance().getProperty("FORMS_PROMOTEXT");

    private PdfWriterFactory() {
        throw new AssertionError("utility class");
    }

    /**
     * Creates a new instance of the PDF writer with promo text, confidentiality
     * statement, and page numbering enabled. Uses {@link PdfPageEventForwarder}
     * to chain multiple page event handlers so all stampers run on each page.
     *
     * @param document Document the PDF document
     * @param stream OutputStream the output stream to write to
     * @param settings FontSettings font settings for the writer (font name, size, encoding, and embedding are all applied to stampers)
     * @return PdfWriter instance configured with all page event stampers
     * @throws DocumentException if the PdfWriter cannot be created
     */
    public static PdfWriter newInstance(Document document, OutputStream stream, FontSettings settings) throws DocumentException {
        java.util.Objects.requireNonNull(document, "document must not be null");
        java.util.Objects.requireNonNull(stream, "stream must not be null");
        java.util.Objects.requireNonNull(settings, "settings must not be null");

        PdfWriter result = PdfWriter.getInstance(document, stream);

        // Use an explicit PdfPageEventForwarder to guarantee deterministic stamper ordering.
        // OpenPDF 3.x auto-chains setPageEvent() calls, but explicit wiring is clearer.
        PdfPageEventForwarder pageEvents = new PdfPageEventForwarder();

        PromoTextStamper pts;

        if (confidentialityStatement != null && !confidentialityStatement.isEmpty()) {
            pts = new PromoTextStamper(confidentialityStatement, 30);
            pts.applyFont(settings);
            pageEvents.addPageEvent(pts);
        }
        if (promoText != null && !promoText.isEmpty()) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String promoTextDate = promoText + " " + simpleDateFormat.format(new Date());
            pts = new PromoTextStamper(promoTextDate, 20);
            pts.applyFont(settings);
            pageEvents.addPageEvent(pts);
        }

        PageNumberStamper pns = new PageNumberStamper(10);
        pns.applyFont(settings);
        pageEvents.addPageEvent(pns);

        result.setPageEvent(pageEvents);

        return result;
    }

}
