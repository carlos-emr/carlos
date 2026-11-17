/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.ResourceBundle;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.printing.PdfFonts;
import io.github.carlos_emr.CarlosProperties;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private EctConsultationFormRequestUtil form;

    @BeforeEach
    void setUp() {
        creator = mock(ConsultationPDFCreator.class, CALLS_REAL_METHODS);
        form = mock(EctConsultationFormRequestUtil.class);
        form.patientName = PATIENT;
        form.patientAddress = "FAKE 1 Rue Élodie<br>北京市朝阳区";
        form.patientPhone = "555-0100";
        form.patientDOB = "2000/01/01";
        form.patientSex = "F";
        form.patientHealthCardType = "ON";
        form.patientHealthNum = "9876543210";
        form.patientHealthCardVersionCode = "AB";
        form.patientChartNo = "FAKE-カルテ7";
        form.pwb = "1";
        form.referalDate = "2026/10/22";
        form.reasonForConsultation = "FAKE-复诊 ≥5 mmol/L";
        form.clinicalInformation = "FAKE-さくら history";
        form.currentMedications = "FAKE-阿莫西林 500 mg";
        form.allergies = "FAKE-페니실린";
        form.letterheadName = "-1";
        ProfessionalSpecialist specialist = mock(ProfessionalSpecialist.class);
        when(specialist.getFormattedTitle()).thenReturn("Dr. FAKE-김민준");
        when(specialist.getPhoneNumber()).thenReturn("555-0101");
        when(specialist.getFaxNumber()).thenReturn("555-0102");
        when(specialist.getStreetAddress()).thenReturn("FAKE 3 東京都");
        when(form.getProfessionalSpecialist()).thenReturn(specialist);
        when(form.getServiceName(any())).thenReturn("FAKE-心脏科");
        ClinicData clinic = mock(ClinicData.class);
        when(clinic.getClinicName()).thenReturn("FAKE-诊所 Łukasz");
        when(clinic.getClinicAddress()).thenReturn("FAKE 9 대로 ");
        when(clinic.getClinicCity()).thenReturn("Toronto");
        when(clinic.getClinicProvince()).thenReturn("ON");
        when(clinic.getClinicPostal()).thenReturn("M5V 1A1");

        Font font = PdfFonts.getFont(BaseFont.HELVETICA, 10);
        ReflectionTestUtils.setField(creator, "reqFrm", form);
        ReflectionTestUtils.setField(creator, "clinic", clinic);
        ReflectionTestUtils.setField(creator, "font", font);
        ReflectionTestUtils.setField(creator, "heading", PdfFonts.getFont(BaseFont.HELVETICA, 12));
        ReflectionTestUtils.setField(creator, "boldFontHeading", PdfFonts.getFont(BaseFont.HELVETICA, 12, Font.BOLD,
                java.awt.Color.BLACK));
        ReflectionTestUtils.setField(creator, "props", mock(CarlosProperties.class, invocation ->
                invocation.getArguments().length > 1 ? invocation.getArgument(1) : null));
        ReflectionTestUtils.setField(creator, "oscarR", ResourceBundle.getBundle("oscarResources", Locale.ENGLISH));
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
