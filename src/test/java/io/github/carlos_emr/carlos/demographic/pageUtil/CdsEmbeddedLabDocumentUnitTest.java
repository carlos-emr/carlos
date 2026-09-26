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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import cds.OmdCdsDocument;
import cds.ReportsDocument.Reports;
import cdsDt.ReportClass;
import cdsDt.ReportFormat;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Unit tests for {@link CdsEmbeddedLabDocument}: an HL7 {@code ED} lab OBX exported as an
 * OMD CDS {@code Reports} entry instead of a truncated or oversized lab result (#3946).
 *
 * <p>Every produced element is validated against the OntarioMD schema, so a mapping that emits a
 * value longer than the schema allows (sub-class, facility, message id) fails here rather than in
 * the receiving EMR.</p>
 *
 * <p>Extends {@link CarlosUnitTestBase} only because the mapping formats dates through the
 * export's {@link Util}, whose static initializer looks up a DAO.</p>
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("fast")
@Tag("demographic")
class CdsEmbeddedLabDocumentUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerExportUtilDependencies() {
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
    }

    static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << >>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    private static Reports newReport() {
        return OmdCdsDocument.Factory.newInstance().addNewOmdCds().addNewPatientRecord().addNewReports();
    }

    private static Map<String, String> lab(String payload) {
        Map<String, String> lab = new HashMap<>();
        lab.put("measureData", payload);
        lab.put("identifier", "PDF");
        lab.put("name", "Pathology Report");
        lab.put("labname", "CARLOS TEST LAB");
        lab.put("datetime", "2026-09-01 10:00:00");
        lab.put("accession", "PW3946-ACC");
        return lab;
    }

    private static List<XmlError> validate(Reports report) {
        List<XmlError> errors = new ArrayList<>();
        XmlOptions options = new XmlOptions();
        options.setErrorListener(errors);
        report.validate(options);
        return errors;
    }

    @Test
    @DisplayName("should export a base64 PDF as a binary Lab Report with the decoded bytes")
    void shouldExportBinaryLabReport_whenPayloadIsBase64Pdf() {
        // HL7 senders wrap base64; the wrapped form must still decode.
        String wrapped = Base64.getMimeEncoder(20, "\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(PDF);
        Reports report = newReport();

        CdsEmbeddedLabDocument.Outcome outcome = CdsEmbeddedLabDocument.writeReport(lab(wrapped), report);

        assertThat(outcome.format()).isEqualTo(ReportFormat.BINARY);
        assertThat(outcome.warning()).isNull();
        assertThat(report.getFormat()).isEqualTo(ReportFormat.BINARY);
        assertThat(report.getContent().getMedia()).isEqualTo(PDF);
        assertThat(report.getContent().isSetTextContent()).isFalse();
        assertThat(report.getFileExtensionAndVersion()).isEqualTo(".pdf");
        assertThat(report.getClass1()).isEqualTo(ReportClass.LAB_REPORT);
        assertThat(report.getSubClass()).isEqualTo("Pathology Report");
        assertThat(report.getSourceFacility()).isEqualTo("CARLOS TEST LAB");
        assertThat(report.getMessageUniqueID()).isEqualTo("PW3946-ACC");
        assertThat(report.getEventDateTime().getFullDateTime()).isNotNull();
        assertThat(validate(report)).isEmpty();
    }

    @Test
    @DisplayName("should export a non-base64 ED payload as a text Lab Report instead of dropping it")
    void shouldExportTextLabReport_whenPayloadIsNotBase64() {
        Reports report = newReport();

        CdsEmbeddedLabDocument.Outcome outcome =
                CdsEmbeddedLabDocument.writeReport(lab("See scanned report in chart."), report);

        assertThat(outcome.format()).isEqualTo(ReportFormat.TEXT);
        assertThat(report.getContent().getTextContent()).isEqualTo("See scanned report in chart.");
        assertThat(report.getFileExtensionAndVersion()).isEqualTo(".txt");
        assertThat(validate(report)).isEmpty();
    }

    @Test
    @DisplayName("should treat a short word that happens to be valid base64 as text")
    void shouldExportText_whenShortPayloadMerelyLooksLikeBase64() {
        Reports report = newReport();

        CdsEmbeddedLabDocument.Outcome outcome = CdsEmbeddedLabDocument.writeReport(lab("NEGATIVE"), report);

        assertThat(outcome.format()).isEqualTo(ReportFormat.TEXT);
        assertThat(report.getContent().getTextContent()).isEqualTo("NEGATIVE");
    }

    @Test
    @DisplayName("should export an unrecognised binary without an extension and report it")
    void shouldWarnWithoutExtension_whenBinarySignatureIsUnknown() {
        byte[] unknown = new byte[96];
        for (int i = 0; i < unknown.length; i++) {
            unknown[i] = (byte) (i * 7 + 3);
        }
        Reports report = newReport();

        CdsEmbeddedLabDocument.Outcome outcome =
                CdsEmbeddedLabDocument.writeReport(lab(Base64.getEncoder().encodeToString(unknown)), report);

        assertThat(outcome.format()).isEqualTo(ReportFormat.BINARY);
        assertThat(report.getContent().getMedia()).isEqualTo(unknown);
        assertThat(report.isSetFileExtensionAndVersion()).isFalse();
        assertThat(outcome.warning()).contains("unrecognised file type");
        assertThat(validate(report)).isEmpty();
    }

    @Test
    @DisplayName("should strip invalid XML characters and cut text to the schema lengths")
    void shouldCleanAndTruncateText_toSchemaLimits() {
        Map<String, String> lab = lab(Base64.getEncoder().encodeToString(PDF));
        lab.put("name", "Report\u000B" + "N".repeat(80));
        lab.put("labname", "Lab\u0000" + "F".repeat(200));
        lab.put("accession", "A".repeat(300));
        lab.put("comments", "Reviewed<br />by lab\u001B");
        Reports report = newReport();

        CdsEmbeddedLabDocument.writeReport(lab, report);

        assertThat(report.getSubClass()).hasSize(CdsEmbeddedLabDocument.SUB_CLASS_MAX).startsWith("ReportN");
        assertThat(report.getSourceFacility()).hasSize(CdsEmbeddedLabDocument.SOURCE_FACILITY_MAX).startsWith("LabF");
        assertThat(report.getMessageUniqueID()).hasSize(CdsEmbeddedLabDocument.MESSAGE_UNIQUE_ID_MAX);
        assertThat(report.getNotes()).isEqualTo("Reviewed\nby lab");
        assertThat(validate(report)).isEmpty();
    }

    @Test
    @DisplayName("should recognise common document signatures")
    void shouldRecogniseSignature_forCommonDocumentTypes() {
        assertThat(CdsEmbeddedLabDocument.extensionFor(PDF)).isEqualTo(".pdf");
        assertThat(CdsEmbeddedLabDocument.extensionFor(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D})).isEqualTo(".png");
        assertThat(CdsEmbeddedLabDocument.extensionFor(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).isEqualTo(".jpg");
        assertThat(CdsEmbeddedLabDocument.extensionFor("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEqualTo(".gif");
        assertThat(CdsEmbeddedLabDocument.extensionFor(new byte[] {'I', 'I', 0x2A, 0x00})).isEqualTo(".tif");
        assertThat(CdsEmbeddedLabDocument.extensionFor(new byte[] {'M', 'M', 0x00, 0x2A})).isEqualTo(".tif");
        assertThat(CdsEmbeddedLabDocument.extensionFor("{\\rtf1".getBytes(StandardCharsets.US_ASCII))).isEqualTo(".rtf");
        assertThat(CdsEmbeddedLabDocument.extensionFor(new byte[] {'%', 'P'})).isNull();
    }

    @Test
    @DisplayName("should decode only strict base64")
    void shouldRejectPayload_whenNotStrictBase64() {
        assertThat(CdsEmbeddedLabDocument.decodeBase64(null)).isNull();
        assertThat(CdsEmbeddedLabDocument.decodeBase64("   ")).isNull();
        assertThat(CdsEmbeddedLabDocument.decodeBase64("not*base64")).isNull();
        assertThat(CdsEmbeddedLabDocument.decodeBase64("QUJD")).containsExactly('A', 'B', 'C');
    }
}
