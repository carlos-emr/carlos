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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Service;

/**
 * Renders one data summary to PDF through the one HTML-to-PDF module ({@link
 * HtmlPdfRenderService}: bundled DejaVu fonts only, no network, no host font), then proves the
 * bytes are a loadable PDF with at least one page before anything is staged. A summary that cannot
 * be printed completely holds only its patient: {@code CDS_EXPORT_DATA_SUMMARY_RENDER_FAILED} when
 * no PDF could be made or the bytes are not a PDF, {@code
 * CDS_EXPORT_DATA_SUMMARY_RENDER_INCOMPLETE} when characters fell back to the replacement glyph or
 * an embedded photo or signature did not print. A text-only or safe-mode render still prints every
 * character and is accepted.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportDataSummaryPdfService {
    static final String FAILED = "CDS_EXPORT_DATA_SUMMARY_RENDER_FAILED";
    static final String INCOMPLETE = "CDS_EXPORT_DATA_SUMMARY_RENDER_INCOMPLETE";

    private final HtmlPdfRenderService renderer;
    private final CdsExportDataSummaryHtmlComposer composer = new CdsExportDataSummaryHtmlComposer();

    public CdsExportDataSummaryPdfService(HtmlPdfRenderService renderer) {
        this.renderer = Objects.requireNonNull(renderer);
    }

    /** The validated PDF of one data summary. */
    byte[] render(
            CdsExportDataSummaryPlanner.Group group, CdsExportDataSummaryHtmlComposer.Patient patient)
            throws IOException {
        String html = composer.compose(group, patient);
        // Owner-only, so nobody else can place a resource in the directory the renderer allows.
        Path empty =
                Files.createTempDirectory(
                        "cds-data-summary",
                        java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                                java.nio.file.attribute.PosixFilePermissions.fromString(
                                        "rwx------")));
        try {
            io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult result;
            try {
                // The summary references nothing outside itself; the only allowed directory is
                // empty.
                result =
                        renderer.render(
                                new HtmlPdfRenderRequest(
                                        html,
                                        null,
                                        List.of(empty.toAbsolutePath().normalize()),
                                        HtmlPdfRenderSource.CDS_DATA_SUMMARY));
            } catch (PDFGenerationException | RuntimeException failed) {
                throw new CdsExportMappingReviewException(FAILED);
            }
            var lost = result.degradations();
            if (lost.contains(HtmlPdfRenderDegradation.FALLBACK_FONT_UNAVAILABLE)
                    || group.hasImage()
                            && (lost.contains(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE)
                                    || lost.contains(
                                            HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY)))
                throw new CdsExportMappingReviewException(INCOMPLETE);
            byte[] pdf = result.pdf();
            requirePdf(pdf);
            return pdf;
        } finally {
            Files.deleteIfExists(empty);
        }
    }

    /** {@code %PDF-} header, loadable by PDFBox, at least one page. */
    static void requirePdf(byte[] pdf) throws IOException {
        if (pdf == null
                || pdf.length < 5
                || !Arrays.equals(
                        Arrays.copyOf(pdf, 5), "%PDF-".getBytes(StandardCharsets.US_ASCII)))
            throw new CdsExportMappingReviewException(FAILED);
        int pages;
        try (var document = Loader.loadPDF(pdf)) {
            pages = document.getNumberOfPages();
        } catch (IOException unreadable) {
            throw new CdsExportMappingReviewException(FAILED);
        }
        if (pages < 1) throw new CdsExportMappingReviewException(FAILED);
    }
}
