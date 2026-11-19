/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import io.github.carlos_emr.carlos.commn.printing.PdfFonts;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the consultation request's patient, reason, specialist and letterhead tables through the
 * real {@link ConsultationPDFCreator} methods and checks every record value reaches the PDF text
 * (issue #5046).
 *
 * <p>These cells used to be built with {@code new Phrase(text, font)} in DejaVu, which has no
 * Chinese, Japanese or Korean characters, so {@code FAKE-陈小明 さくら 김민준 Nguyen} printed as
 * {@code FAKE-   Nguyen} in a valid PDF. The values below are synthetic.
 */
@Tag("unit")
@Tag("report")
class ConsultationPdfUnicodeUnitTest {
    private static final String PATIENT = "FAKE-陈小明 さくら 김민준 Nguyễn";
    private ConsultationPDFCreator creator;

    @BeforeEach
    void setUp() {
        // The drawing methods read only the resolved letter (#3973 made it request-free), so the
        // letter carries the same multi-script values the request path used to supply.
        ConsultationLetterDto letter = new ConsultationLetterDto(
                Locale.ENGLISH,
                new ConsultationLetterDto.Letterhead("FAKE-诊所 Łukasz", "", "", "", "FAKE-诊所 Łukasz",
                        "FAKE 9 대로 ", "Toronto", "ON", "M5V 1A1", "", ""),
                new ConsultationLetterDto.Request("1", "2026/10/22", "1", "FAKE-心脏科", "", true, false),
                new ConsultationLetterDto.Specialist("Dr. FAKE-김민준", "555-0101", "555-0102", "FAKE 3 東京都"),
                new ConsultationLetterDto.Patient(PATIENT, "FAKE 1 Rue Élodie<br>北京市朝阳区", "555-0100", "", "",
                        "", "2000/01/01", "F", "ON", "9876543210", "AB", "", "", "", "", "FAKE-カルテ7"),
                new ConsultationLetterDto.Clinical("FAKE-复诊 ≥5 mmol/L", "FAKE-さくら history", "",
                        "FAKE-阿莫西林 500 mg", "FAKE-페니실린", "", ""),
                new ConsultationLetterDto.Footer(false, null, "", false, null, ""),
                new ConsultationLetterDto.Images(false, null, false, null, null));
        creator = new ConsultationPDFCreator(letter, new ByteArrayOutputStream());

        Font font = PdfFonts.getFont(BaseFont.HELVETICA, 10);
        ReflectionTestUtils.setField(creator, "font", font);
        ReflectionTestUtils.setField(creator, "heading", PdfFonts.getFont(BaseFont.HELVETICA, 12));
        ReflectionTestUtils.setField(creator, "boldFontHeading", PdfFonts.getFont(BaseFont.HELVETICA, 12, Font.BOLD,
                java.awt.Color.BLACK));
    }

    private String render(String... tableMethods) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        for (String method : tableMethods) {
            document.add((PdfPTable) ReflectionTestUtils.invokeMethod(creator, method));
        }
        document.close();
        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            for (var name : pdf.getPage(0).getResources().getFontNames()) {
                assertThat(pdf.getPage(0).getResources().getFont(name).isEmbedded()).isTrue();
            }
            return new PDFTextStripper().getText(pdf);
        }
    }

    @Test
    @DisplayName("should print the patient's identifiers and address in any script")
    void shouldPrintPatientIdentifiers_inChineseJapaneseAndKorean() throws Exception {
        assertThat(render("createPatientTable"))
                .contains(PATIENT, "FAKE 1 Rue Élodie", "北京市朝阳区", "9876543210", "FAKE-カルテ7");
    }

    @Test
    @DisplayName("should print the reason, clinical information, medications and allergies in any script")
    void shouldPrintClinicalText_inChineseJapaneseAndKorean() throws Exception {
        assertThat(render("createConsultDetailTable"))
                .contains("FAKE-复诊 ≥5 mmol/L", "FAKE-さくら history", "FAKE-阿莫西林 500 mg", "FAKE-페니실린");
    }

    @Test
    @DisplayName("should print the specialist, service and letterhead in any script")
    void shouldPrintSpecialistAndLetterhead_inChineseJapaneseAndKorean() throws Exception {
        assertThat(render("createSpecialistTable", "createClinicInfoHeader"))
                .contains("Dr. FAKE-김민준", "FAKE-心脏科", "FAKE 3 東京都", "FAKE-诊所 Łukasz", "FAKE 9 대로");
    }
}
