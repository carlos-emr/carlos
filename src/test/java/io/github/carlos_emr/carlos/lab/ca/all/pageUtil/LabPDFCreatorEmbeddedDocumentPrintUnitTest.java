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
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.DefaultGenericHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.ExcellerisOntarioHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * The printed lab treats an ED OBX of any lab type as the lab views and the download endpoint do,
 * through {@link EmbeddedLabDocumentLoader}: a standards-compliant ED.5 PDF is appended to the
 * report (Excelleris used to queue its empty OBX-5.1, which failed the whole print), declared
 * text prints as text, and a non-PDF prints a note rather than failing the assembled PDF.
 *
 * @since 2026-10-02
 */
@DisplayName("LabPDFCreator embedded documents in print")
@Tag("unit")
@Tag("lab")
class LabPDFCreatorEmbeddedDocumentPrintUnitTest {

    private File tempSource;

    @AfterEach
    void tearDown() throws IOException {
        if (tempSource != null) {
            Files.deleteIfExists(tempSource.toPath());
        }
    }

    private static String message(String version, String trigger, String obx) {
        return String.join("\r\n",
                "MSH|^~\\&|GENLAB|CARLOSTEST|HTTPCLIENT|carlos|20261002101500||" + trigger + "|PW4118PRT|P|" + version + "|||ER|AL",
                "PID||9999999999|4118||PLAYWRIGHT^PRINT||19800102|F",
                "ORC|RE||ACC4118|||||||||99999^FAKE-ORDERING^DOC",
                "OBR|1|PL4118|ACC4118|RPT^Report||20261002100000|20261002100000|||||||20261002100000"
                        + "||99999^FAKE-ORDERING^DOC||||||20261002100000||CHEM4|F",
                "OBX|1|NM|GLU^Glucose||5.2|mmol/L|3.3-7.7|N|||F|||20261002100000",
                obx);
    }

    private static ExcellerisOntarioHandler excelleris(String obx) throws Exception {
        ExcellerisOntarioHandler handler = new ExcellerisOntarioHandler();
        handler.init(message("2.3.1", "ORU^R01", obx));
        return handler;
    }

    private static byte[] onePagePdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("should append a standards-compliant Excelleris ED.5 PDF to the printed lab")
    void shouldAppendEd5Pdf_forExcelleris() throws Exception {
        byte[] pdf = onePagePdf();
        ExcellerisOntarioHandler handler = excelleris("OBX|2|ED|RPT^Report||^TEXT^PDF^Base64^"
                + Base64.getEncoder().encodeToString(pdf) + "||||||F|||20261002100000");
        LabPDFCreator creator = new LabPDFCreator();

        assertThat(handler.getOBXResult(0, 1)).as("OBX-5.1 is the empty source application").isEmpty();
        assertThat(creator.embeddedDocumentResult(handler, 0, 1)).isEqualTo(LabPDFCreator.APPENDED_PDF_NOTE);
        assertThat(creator.embeddedDocumentCount()).isEqualTo(1);

        tempSource = PathValidationUtils.createSecureTempFile("lab-print-test-", ".pdf");
        Files.write(tempSource.toPath(), onePagePdf());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        creator.addEmbeddedDocuments(tempSource, out);
        try (PDDocument printed = Loader.loadPDF(out.toByteArray())) {
            assertThat(printed.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("should append an ED.5 PDF from any lab type, not only Excelleris and PATHL7")
    void shouldAppendEd5Pdf_forGenericHandler() throws Exception {
        DefaultGenericHandler handler = new DefaultGenericHandler();
        handler.init(message("2.3", "ORU^Z01", "OBX|2|ED|RPT^Report||^TEXT^PDF^Base64^"
                + Base64.getEncoder().encodeToString(onePagePdf()) + "||||||F|||20261002100000"));
        LabPDFCreator creator = new LabPDFCreator();

        assertThat(creator.resultText(handler, 0, 1)).isEqualTo(LabPDFCreator.APPENDED_PDF_NOTE);
        assertThat(creator.embeddedDocumentCount()).isEqualTo(1);
        // An ordinary result prints as before and queues nothing.
        assertThat(creator.resultText(handler, 0, 0)).isEqualTo("5.2");
        assertThat(creator.embeddedDocumentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should print declared ED text with its Excelleris sub-ID label and queue nothing")
    void shouldPrintEmbeddedText_withExcellerisSubId() throws Exception {
        ExcellerisOntarioHandler handler = excelleris("OBX|2|ED|RPT^Report|C|^TEXT^PLAIN^A^Line one\\.br\\Line two||||||F|||20261002100000");
        LabPDFCreator creator = new LabPDFCreator();

        assertThat(creator.embeddedDocumentResult(handler, 0, 1)).isEqualTo("C) Line one<br />Line two");
        assertThat(creator.embeddedDocumentCount()).isZero();
    }

    @Test
    @DisplayName("should print a note for a non-PDF ED document instead of failing the printed lab")
    void shouldPrintNote_forNonPdfDocument() throws Exception {
        String png = Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        MessageHandler handler = excelleris("OBX|2|ED|RPT^Report||^IM^PNG^Base64^" + png + "||||||F|||20261002100000");
        LabPDFCreator creator = new LabPDFCreator();

        assertThat(creator.embeddedDocumentResult(handler, 0, 1)).isEqualTo(LabPDFCreator.NOT_PRINTABLE_NOTE);
        assertThat(creator.embeddedDocumentCount()).isZero();
    }
}
