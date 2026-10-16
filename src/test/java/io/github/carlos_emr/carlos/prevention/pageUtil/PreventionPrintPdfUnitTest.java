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
package io.github.carlos_emr.carlos.prevention.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.DocumentException;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Entry;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Printout;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Section;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@Tag("prevention")
@DisplayName("PreventionPrintPdf draws the printout it is given")
class PreventionPrintPdfUnitTest extends CarlosUnitTestBase {

    private Demographic patient;

    @BeforeEach
    void setUp() {
        Clinic clinic = mock(Clinic.class);
        when(clinic.getId()).thenReturn(1);
        when(clinic.getClinicName()).thenReturn("CARLOS Test Clinic");
        when(clinic.getClinicAddress()).thenReturn("1 Main St");
        when(clinic.getClinicCity()).thenReturn("Hamilton");
        when(clinic.getClinicProvince()).thenReturn("ON");
        when(clinic.getClinicPostal()).thenReturn("L0R 4K3");
        when(clinic.getClinicPhone()).thenReturn("555-555-5555");
        when(clinic.getClinicFax()).thenReturn("555-555-5556");
        ClinicDAO clinicDao = createAndRegisterMock(ClinicDAO.class);
        when(clinicDao.getClinic()).thenReturn(clinic);

        patient = new Demographic();
        patient.setLastName("FAKE-PRINT");
        patient.setFirstName("Patient");
        patient.setSexDesc("Female");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("02");
        patient.setHcType("ON");
    }

    @Test
    @DisplayName("should print every entry, with immunizations before screenings")
    void shouldPrintEverySection_withImmunizationsBeforeScreenings() throws Exception {
        Entry flu = new Entry("2025-10-01", "45 years", "3", null, null, null, "left deltoid", "Sanofi Pasteur",
                "FLUZONE High-Dose", "L123", "0.5 mL", "Dr Carlos");
        Entry refused = new Entry("2015-03-03", "35 years", "1", null, null, null, null, null, null, null, null, null);
        Entry pap = new Entry("2024-05-05", "44 years", "0", "other", null, null, null, null, null, null, null, "Dr Pap");

        String text = print(new Printout(patient, null, false, List.of(
                new Section("Inf", false, false, List.of(flu)),
                new Section("HPV", false, false, List.of(refused)),
                new Section("PAP", true, false, List.of(pap)))));

        assertThat(text).contains("Immunizations and Screenings", "FAKE-PRINT, Patient", "CARLOS Test Clinic",
                "Prevention Inf", "2025-10-01", "Age: 45 years", "Status: Completed externally",
                "Location of shot: left deltoid", "Manufacturer: Sanofi Pasteur", "Name of Vaccine: FLUZONE High-Dose",
                "Lot#: L123", "Dose Administered: 0.5 mL", "Provider: Dr Carlos",
                "Prevention HPV", "Status: Refused", "Provider: Unknown",
                "Screenings:", "Prevention PAP", "Result: other (no reason submitted)", "Provider: Dr Pap");
        assertThat(text.indexOf("Immunizations:")).isLessThan(text.indexOf("Prevention Inf"));
        assertThat(text.indexOf("Prevention HPV")).isLessThan(text.indexOf("Screenings:"));
        assertThat(text.indexOf("Screenings:")).isLessThan(text.indexOf("Prevention PAP"));
    }

    @Test
    @DisplayName("should print an entry's comments once, whatever prevention_show_comments says")
    void shouldPrintCommentsOnce_whateverTheSetting() throws Exception {
        // The loader applies the setting; the PDF draws what the printout holds. It used to print
        // the comment under a "Comments:" label and again in its own block when the setting was on.
        Printout printout = new Printout(patient, null, false, List.of(new Section("Inf", false, false, List.of(
                new Entry("2025-10-01", "45 years", "0", null, null, "Tolerated well, no reaction", null, null, null,
                        null, null, "Dr Carlos")))));
        CarlosProperties properties = CarlosProperties.getInstance();
        Object previous = properties.setProperty("prevention_show_comments", "true");
        try {
            String text = print(printout).replaceAll("\\s+", " ");

            assertThat(text.split("Tolerated well, no reaction", -1)).hasSize(2);
        } finally {
            if (previous == null) {
                properties.remove("prevention_show_comments");
            } else {
                properties.setProperty("prevention_show_comments", previous.toString());
            }
        }
    }

    @Test
    @DisplayName("should print Chinese, Japanese, Korean and emoji text read from the chart")
    void shouldPrintCjkAndEmoji_fromChart() throws Exception {
        patient.setLastName("FAKE-陈");
        patient.setFirstName("小明");
        Printout printout = new Printout(patient, "김민준", false, List.of(new Section("Inf", false, false, List.of(
                new Entry("2025-10-01", "45 years", "0", null, null, "Tolerated well 👍", "左上腕", null,
                        "インフルエンザ", null, null, "Dr 王")))));

        String text = print(printout).replaceAll("\\s+", " ");

        assertThat(text).contains("FAKE-陈, 小明", "MRP: 김민준", "Tolerated well 👍", "Location of shot: 左上腕",
                "Name of Vaccine: インフルエンザ", "Provider: Dr 王");
    }

    @Test
    @DisplayName("should not print null for a patient without a health card number or version")
    void shouldNotPrintNull_whenHinMissing() throws Exception {
        String text = print(printout(null, "Inf"));

        assertThat(text).contains("HIN: (ON)").doesNotContain("null");
    }

    @Test
    @DisplayName("should print the health card number and version when the patient has them")
    void shouldPrintHin_whenPresent() throws Exception {
        patient.setHin("1234567890");
        patient.setVer("AB");

        assertThat(print(printout(null, "Inf"))).contains("HIN: (ON) 1234567890 AB");
    }

    @Test
    @DisplayName("should show the MRP line inside the page header when one is named")
    void shouldPrintMrpLine_whenMrpNamed() throws Exception {
        String text = print(printout("Jane Carlos", "Inf"));

        assertThat(text).contains("MRP: Jane Carlos", "FAKE-PRINT, Patient", "Prevention Inf");
    }

    @Test
    @DisplayName("should carry a long section across pages and print every entry once")
    void shouldContinueSection_acrossPages() throws Exception {
        List<Entry> doses = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            doses.add(new Entry(String.format("2021-%02d-%02d", 1 + i % 12, 1 + i % 28), "41 years", "0", null, null,
                    "dose " + i, null, null, null, "LOT" + i, null, "Dr Carlos"));
        }

        String text = print(new Printout(patient, null, false, List.of(new Section("COVID-19", false, false, doses))));

        for (int i = 0; i < 60; i++) {
            assertThat(text).contains("LOT" + i + "\n");
        }
        assertThat(text).contains("Prevention COVID-19 (cont'd)", "-2-");
    }

    @Test
    @DisplayName("should read history-layout status codes as Yes, Never and Previous")
    void shouldPrintHistoryStatuses_forHistoryLayout() throws Exception {
        String text = print(new Printout(patient, null, false, List.of(new Section("Smoking", true, true, List.of(
                new Entry("2023-01-01", "43 years", "0", null, null, null, null, null, null, null, null, null),
                new Entry("2022-01-01", "42 years", "2", null, null, null, null, null, null, null, null, null))))));

        assertThat(text).contains("Status: Yes", "Status: Previous", "No immunization entries for this patient");
    }

    @Test
    @DisplayName("should title an immunizations-only printout without a screenings part")
    void shouldOmitScreenings_whenImmunizationsOnly() throws Exception {
        String text = print(new Printout(patient, null, true, List.of(new Section("Inf", false, false, List.of(
                new Entry("2025-10-01", "45 years", "0", null, null, null, null, null, null, null, null, null))))));

        assertThat(text).contains("Immunizations", "Prevention Inf")
                .doesNotContain("Immunizations and Screenings", "Screenings:", "No screening entries");
    }

    @Test
    @DisplayName("should say there are no screening entries when the full printout holds immunizations only")
    void shouldSayNoScreenings_whenFullPrintoutHasNone() throws Exception {
        assertThat(print(printout(null, "Inf"))).contains("No screening entries for this patient");
    }

    @Test
    @DisplayName("should refuse to draw an empty printout")
    void shouldThrow_whenNothingToPrint() {
        PreventionPrintPdf pdf = new PreventionPrintPdf();
        Printout empty = new Printout(patient, null, false, List.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThatThrownBy(() -> pdf.printPdf(empty, out)).isInstanceOf(DocumentException.class);
    }

    private Printout printout(String mrpName, String type) {
        return new Printout(patient, mrpName, false, List.of(new Section(type, false, false, List.of(
                new Entry("2025-10-01", "45 years", "0", null, null, null, null, null, null, null, null, "Dr Carlos")))));
    }

    private static String print(Printout printout) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PreventionPrintPdf().printPdf(printout, out);
        byte[] bytes = out.toByteArray();
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document).replace("\r\n", "\n");
        }
    }
}
