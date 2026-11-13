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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Storage;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The data-summary document: exact, encoded, distinct NULL/empty, whole long text, binaries. */
@Tag("unit")
@Tag("cds-export")
class CdsExportDataSummaryHtmlComposerUnitTest {
    private static final byte[] PNG =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static final CdsExportDataSummaryHtmlComposer.Patient PATIENT =
            CdsExportDataSummaryHtmlComposer.Patient.of(
                    Map.of(
                            "demographic_no", "27",
                            "last_name", "Fixture",
                            "first_name", "Synthetic <b>",
                            "year_of_birth", "1980",
                            "month_of_birth", "02",
                            "date_of_birth", "29",
                            "sex", "F",
                            "hin", "9876543217",
                            "ver", "AB",
                            "hc_type", "ON"));

    private static Value binary(byte[] bytes) throws Exception {
        return new Value(
                Storage.BINARY,
                null,
                bytes.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                bytes);
    }

    private static Group group(String table, Kind kind, Map<String, Value> values) {
        return new Group(kind, table, "5", List.of(new Row(table, "00000000000000000005", values)), 0, 0, null, false);
    }

    @Test
    void shouldEncodeEveryValue_andKeepSqlNullDistinctFromEmptyAndFromStoredMarkerText() {
        var values = new LinkedHashMap<String, Value>();
        values.put("message", Value.text("<script>alert('x')</script> & \"quoted\"\r\nline two"));
        values.put("priority", Value.sqlNull());
        values.put("status", Value.text(""));
        values.put("task_assigned_to", Value.text("no value (SQL NULL)"));
        values.put("tickler_no", Value.text("5"));
        String html = new CdsExportDataSummaryHtmlComposer().compose(group("tickler", Kind.TICKLER, values), PATIENT);
        assertThat(html)
                .contains("<title>CARLOS data summary: Tickler</title>")
                .contains("not an original document")
                .contains("&lt;script&gt;alert('x')&lt;/script&gt; &amp; \"quoted\"\r\nline two")
                .doesNotContain("<script>")
                .contains("Synthetic &lt;b&gt;")
                .contains("1980-02-29")
                .contains("9876543217 version AB (ON)")
                .contains("<span class=\"name\">priority</span> <span class=\"state\">no value (SQL NULL)</span>")
                .contains("<span class=\"name\">status</span> <span class=\"state\">empty</span>")
                // Stored text that reads like a marker is still a value with its own length.
                .contains("<span class=\"name\">task_assigned_to</span> <span class=\"state\">19 characters</span>");
        assertThat(new CdsExportDataSummaryHtmlComposer().compose(group("tickler", Kind.TICKLER, values), PATIENT))
                .isEqualTo(html);
    }

    @Test
    void shouldPrintLongTextWhole_andEmbedOnlyRealImages() throws Exception {
        String longText = "Whole clinical narrative 😀 ".repeat(20_000) + "END-OF-TEXT";
        var values = new LinkedHashMap<String, Value>();
        values.put("contents", binary(Base64.getEncoder().encode(PNG)));
        values.put("image_data", Value.text(longText));
        values.put("image_id", Value.text("5"));
        String html = new CdsExportDataSummaryHtmlComposer().compose(group("client_image", Kind.PATIENT_PHOTO, values), PATIENT);
        assertThat(html)
                .contains("END-OF-TEXT")
                .contains(longText.substring(0, 1000))
                .contains("Image (decoded from the stored Base64):")
                .contains("src=\"data:image/png;base64," + Base64.getEncoder().encodeToString(PNG) + "\"");
        var opaque = new LinkedHashMap<String, Value>();
        byte[] serialized = "not an image".getBytes(StandardCharsets.UTF_8);
        opaque.put("allergies", binary(serialized));
        opaque.put("id", Value.text("5"));
        String bpmh = new CdsExportDataSummaryHtmlComposer().compose(group("formBPMH", Kind.ENCOUNTER_FORM, opaque), PATIENT);
        assertThat(bpmh)
                .contains("binary, 12 bytes, SHA-256 " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized)))
                .doesNotContain("<img");
    }

    @Test
    void shouldDecodeAStoredBase64EmailBody_whenItIsText() throws Exception {
        var values = new LinkedHashMap<String, Value>();
        values.put("body", binary(Base64.getEncoder().encode("Café results <ok>".getBytes(StandardCharsets.UTF_8))));
        values.put("encryptedMessage", new Value(Storage.BINARY, null, 0, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0])), null));
        values.put("id", Value.text("5"));
        String html = new CdsExportDataSummaryHtmlComposer().compose(group("emailLog", Kind.EMAIL, values), PATIENT);
        assertThat(html)
                .contains("Text decoded from the stored Base64 (17 characters):")
                .contains("Café results &lt;ok&gt;")
                .contains("<span class=\"name\">encryptedMessage</span> <span class=\"state\">empty (binary, 0 bytes)</span>");
    }

    @Test
    void shouldWithholdEmailCredentials_whileStatingThatAValueIsHeld() {
        var values = new LinkedHashMap<String, Value>();
        values.put("subject", Value.text("Your results"));
        values.put("password", Value.text("S3cret-Pa55"));
        values.put("passwordClue", Value.text("Your clinic hint 4417"));

        String html =
                new CdsExportDataSummaryHtmlComposer()
                        .compose(group("emailLog", Kind.EMAIL, values), PATIENT);

        assertThat(html)
                .contains("Your results")
                .doesNotContain("S3cret-Pa55", "hint 4417", "11 characters")
                .containsSubsequence(
                        "password",
                        "value withheld: an e-mail access credential is not exported",
                        "passwordClue",
                        "value withheld: an e-mail access credential is not exported");
    }

    @Test
    void shouldStateAnEmptyCredential_asItDoesAnyEmptyField() {
        var values = new LinkedHashMap<String, Value>();
        values.put("password", Value.sqlNull());
        values.put("passwordClue", Value.text(""));

        String html =
                new CdsExportDataSummaryHtmlComposer()
                        .compose(group("emailLog", Kind.EMAIL, values), PATIENT);

        assertThat(html).contains("no value (SQL NULL)", "empty").doesNotContain("withheld");
    }

    @Test
    void shouldRenderAValidatedPdf_throughTheBundledFontRenderer() throws Exception {
        var values = new LinkedHashMap<String, Value>();
        values.put("contents", binary(PNG));
        values.put("image_id", Value.text("5"));
        values.put("image_type", Value.text("png"));
        values.put("update_date", Value.text("2024-01-02 03:04:05"));
        var service = new CdsExportDataSummaryPdfService(HtmlPdfTestServices.newService());
        byte[] pdf = service.render(group("client_image", Kind.PATIENT_PHOTO, values), PATIENT);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isPositive();
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("CARLOS data summary: Patient photo").contains("not an original");
        }
        assertThatThrownBy(() -> CdsExportDataSummaryPdfService.requirePdf("<html>".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_DATA_SUMMARY_RENDER_FAILED");
        assertThatThrownBy(() -> CdsExportDataSummaryPdfService.requirePdf("%PDF-1.7 truncated".getBytes(StandardCharsets.US_ASCII)))
                .hasMessage("CDS_EXPORT_DATA_SUMMARY_RENDER_FAILED");
    }
}
