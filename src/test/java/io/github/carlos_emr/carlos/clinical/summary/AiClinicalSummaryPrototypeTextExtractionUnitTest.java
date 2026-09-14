/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class AiClinicalSummaryPrototypeTextExtractionUnitTest {
    @Test
    void preservesStoredFieldValuesAndLabelsWithoutExecutingOrFetchingHtml() {
        String html = "<p>Medication history</p><script>secretScript()</script><style>.secretStyle{}</style>"
                + "<label>Dose<input name='dose' value='5 mg'></label><textarea name='history'>No rash</textarea>"
                + "<label>Allergy<input type='checkbox' name='allergy' value='penicillin'></label>"
                + "<select name='status'><option>Active</option><option selected>Discontinued</option></select>"
                + "<input type='hidden' name='token' value='secretValue'><iframe src='https://invalid.example'></iframe>";
        var result = ClinicalSummaryTextExtractor.html(html);
        assertThat(result.text()).contains("Dose", "dose: 5 mg", "history: No rash", "allergy: unchecked", "Discontinued")
                .doesNotContain("secret", "invalid.example", "Active");
        assertThat(result.complete()).isFalse();
        assertThat(result.reason()).contains("calculated", "original");
    }

    @Test
    void extractsEveryPdfPageAndFlagsPagesWithoutText() throws Exception {
        byte[] bytes;
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (String text : new String[]{"First page: no penicillin allergy", "", "Last page: aspirin discontinued"}) {
                var page = new PDPage();
                pdf.addPage(page);
                if (!text.isEmpty()) {
                    try (var stream = new PDPageContentStream(pdf, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(40, 700);
                        stream.showText(text);
                        stream.endText();
                    }
                }
            }
            pdf.save(output);
            bytes = output.toByteArray();
        }
        var result = ClinicalSummaryTextExtractor.bytes(bytes, "application/pdf");
        assertThat(result.text()).contains("no penicillin allergy", "aspirin discontinued", "Page 3");
        assertThat(result.complete()).isFalse();
        assertThat(result.reason()).contains("pages have no readable text");
    }

    @Test
    void leavesUnsupportedOrInvalidContentExplicitlyUnreadable() throws Exception {
        var image = ClinicalSummaryTextExtractor.bytes(new byte[]{1, 2}, "image/png");
        assertThat(image.text()).isEmpty();
        assertThat(image.complete()).isFalse();
        assertThatThrownBy(() -> ClinicalSummaryTextExtractor.bytes(new byte[]{(byte) 0xff}, "text/plain"))
                .isInstanceOf(java.io.IOException.class);
        assertThat(ClinicalSummaryTextExtractor.bytes("Recorded result".getBytes(StandardCharsets.UTF_8), "text/plain").complete()).isTrue();
    }

    @Test
    void cachesExactBytesAndContentTypeAndInvalidatesChangedContents() throws Exception {
        byte[] original = "Recorded dose: 5 mg.".getBytes(StandardCharsets.UTF_8);
        var first = ClinicalSummaryTextExtractor.bytes(original, "text/plain");
        assertThat(ClinicalSummaryTextExtractor.bytes(original.clone(), "text/plain")).isSameAs(first);
        var changed = ClinicalSummaryTextExtractor.bytes("Recorded dose: 2.5 mg.".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertThat(changed.text()).contains("2.5 mg").isNotEqualTo(first.text());
        assertThatThrownBy(() -> ClinicalSummaryTextExtractor.bytes(original, "application/pdf")).isInstanceOf(java.io.IOException.class);
    }
}
