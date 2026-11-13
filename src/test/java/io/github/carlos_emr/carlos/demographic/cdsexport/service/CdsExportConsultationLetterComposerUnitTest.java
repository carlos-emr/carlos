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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPrintedFormReadDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import io.github.carlos_emr.CarlosProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The exported consultation letter is the creator's request-free letter filled from the captured
 * request, its extensions, the captured demographic row and the reference values, with the
 * patient's own stored signature; a missing signature is reported, never substituted.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportConsultationLetterComposerUnitTest {
    private CdsExportPrintedFormReadDao references;
    private CdsExportConsultationLetterComposer composer;
    private final Map<String, String> previous = new HashMap<>();

    @TempDir Path temp;

    /** Sets a CARLOS property for one test; the packaged file names a logo that is not installed here. */
    private void property(String key, String value) {
        previous.putIfAbsent(key, CarlosProperties.getInstance().getProperty(key));
        if (value == null) CarlosProperties.getInstance().remove(key);
        else CarlosProperties.getInstance().setProperty(key, value);
    }

    @AfterEach
    void restore() {
        previous.forEach((key, value) -> {
            if (value == null) CarlosProperties.getInstance().remove(key);
            else CarlosProperties.getInstance().setProperty(key, value);
        });
    }

    @BeforeEach
    void setUp() {
        property("faxLogoInConsultation", null);
        references = mock(CdsExportPrintedFormReadDao.class);
        when(references.clinic()).thenReturn(new CdsExportPrintedFormReadDao.Clinic(
                "Synthetic Clinic", "1 Test St", "Testville", "ON", "A1A 1A1", "555-0100", "555-0101"));
        when(references.service(3)).thenReturn("Synthetic Service");
        when(references.specialist(9001)).thenReturn(new CdsExportPrintedFormReadDao.Specialist(
                "Dr.", "Sam", "SPECIALIST", "MD", "7 Consult Way", "555-0400", "555-0401"));
        when(references.provider("101")).thenReturn(new CdsExportPrintedFormReadDao.Provider("Rhea", "REFERRING", "123456"));
        when(references.provider("999")).thenReturn(new CdsExportPrintedFormReadDao.Provider("Fay", "FAMILY", "654321"));
        when(references.appointmentInstruction("A1")).thenReturn("Please book within two weeks");
        when(references.cellPhone(27)).thenReturn("555-0301");
        composer = new CdsExportConsultationLetterComposer(references);
    }

    private static Row row(String table, long id, String... columns) {
        var values = new LinkedHashMap<String, Value>();
        for (int i = 0; i < columns.length; i += 2)
            values.put(columns[i], columns[i + 1] == null ? Value.sqlNull() : Value.text(columns[i + 1]));
        return new Row(table, CdsExportPatientTableSourceReader.key(id), values);
    }

    private static Group request(String signature, String contact) {
        return request(signature, contact, "9001");
    }

    private static Group request(String signature, String contact, String specId) {
        return new Group(
                Kind.CONSULTATION_REQUEST,
                "consultationRequests",
                "5",
                List.of(
                        row("consultationRequests", 5, "requestId", "5", "referalDate", "2024-02-03",
                                "patientWillBook", "0", "urgency", "1", "serviceId", "3", "specId", specId,
                                "demographicContactId", contact, "providerNo", "101", "appointmentDate", "2024-03-01",
                                "appointmentTime", "14:05:00", "appointmentInstructions", "A1",
                                "reason", "Synthetic reason", "clinicalInfo", "Clinical text",
                                "concurrentProblems", "", "currentMeds", "Medication", "allergies", "Allergy",
                                "letterheadName", "101", "letterheadAddress", "", "letterheadPhone", "",
                                "letterheadFax", "", "signature_img", signature, "site_name", null),
                        row("consultationRequestExt", 11, "id", "11", "requestId", "5", "name", "letterheadTitle",
                                "value", "Dr", "dateCreated", "2024-02-03")),
                0,
                0,
                null,
                false);
    }

    private static CdsExportDataSummaryOccurrenceService.Plan plan(Group request, byte[] signature) throws Exception {
        var values = new LinkedHashMap<String, Value>();
        values.put("id", Value.text("44"));
        values.put("signatureImage", new Value(CdsExportDataSummaryPlanner.Storage.BINARY, null, signature.length,
                "0".repeat(64), signature));
        var digital = new Group(Kind.DIGITAL_SIGNATURE, "DigitalSignature", "44",
                List.of(new Row("DigitalSignature", CdsExportPatientTableSourceReader.key(44), values)), 0, 0, null,
                false);
        var demographic = new HashMap<String, String>(Map.of("demographic_no", "27", "last_name", "FIXTURE",
                "first_name", "SYNTHETIC", "year_of_birth", "2001", "month_of_birth", "2", "date_of_birth", "3",
                "sex", "F", "hin", "0000000000", "provider_no", "999"));
        demographic.put("address", "5 Sample Lane");
        demographic.put("city", "Testville");
        demographic.put("province", "ON");
        demographic.put("postal", "A1A 1A1");
        return new CdsExportDataSummaryOccurrenceService.Plan(List.of(request, digital),
                CdsExportDataSummaryHtmlComposer.Patient.of(demographic), demographic);
    }

    private static byte[] png() throws Exception {
        var image = new BufferedImage(300, 90, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(10, 10, 100, 30);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    @Test
    void shouldPrintTheCapturedRequest_withTheStoredSignatureAndChartReferences() throws Exception {
        Path logo = temp.resolve("logo.png");
        Files.write(logo, png());
        property("faxLogoInConsultation", logo.toString());
        var request = request("44", null);
        var printed = composer.print(request, plan(request, png()));

        assertThat(printed.notes()).isEmpty();
        assertThat(printed.subClass()).isEqualTo("Consultation request letter");
        assertThat(printed.event().lexical()).isEqualTo("2024-02-03");
        assertThat(printed.author()).isEqualTo("REFERRING, Rhea");
        assertThat(printed.metadata()).containsOnly(
                Map.entry("referalDate", "Reports/EventDateTime"),
                Map.entry("providerNo", "Reports/SourceAuthorPhysician"));
        try (var pdf = Loader.loadPDF(printed.pdf())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Rhea REFERRING", "Date: 2024-02-03", "Please book within two weeks",
                    "Dr. SPECIALIST, Sam MD", "Urgent", "Synthetic Service", "FIXTURE, SYNTHETIC", "555-0301",
                    "2001-02-03 (y/m/d)", "2024-03-01", "2:05 PM", "Synthetic reason", "Clinical text", "Medication",
                    "Allergy", "Signature:", "MRP: FAMILY, Fay (654321)");
            int images = 0;
            for (var page : pdf.getPages())
                for (var name : page.getResources().getXObjectNames()) images++;
            assertThat(images).isEqualTo(2);
        }
    }

    @Test
    void shouldPrintWithoutALogoOrNote_whenTheConfiguredLogoFileIsNotInstalled() throws Exception {
        property("faxLogoInConsultation", temp.resolve("absent.png").toString());
        var request = request("44", null);
        var printed = composer.print(request, plan(request, png()));

        assertThat(printed.notes()).isEmpty();
        try (var pdf = Loader.loadPDF(printed.pdf())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Signature:", "Synthetic Service");
            int images = 0;
            for (var page : pdf.getPages())
                for (var name : page.getResources().getXObjectNames()) images++;
            assertThat(images).isEqualTo(1);
        }
    }

    @Test
    void shouldReportTheLogo_whenTheInstalledLogoIsNotAnImage() throws Exception {
        Path logo = temp.resolve("logo.png");
        Files.write(logo, "not an image".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        property("faxLogoInConsultation", logo.toString());
        var request = request("44", null);

        assertThat(composer.print(request, plan(request, png())).notes())
                .containsExactly(CdsExportConsultationLetterComposer.LOGO_NOTE);
    }

    @Test
    void shouldReportTheLogo_whenTheInstalledLogoIsLargerThanTheLimit() throws Exception {
        Path logo = temp.resolve("logo.png");
        Files.write(logo, new byte[(4 << 20) + 1]);
        property("faxLogoInConsultation", logo.toString());
        var request = request("44", null);

        assertThat(composer.print(request, plan(request, png())).notes())
                .containsExactly(CdsExportConsultationLetterComposer.LOGO_NOTE);
    }

    @Test
    void shouldReportTheSignature_whenTheStoredSignatureIsNotInTheChart() throws Exception {
        var request = request("45", null);
        var printed = composer.print(request, plan(request, png()));

        assertThat(printed.notes()).containsExactly(CdsExportConsultationLetterComposer.SIGNATURE_NOTE);
        try (var pdf = Loader.loadPDF(printed.pdf())) {
            assertThat(new PDFTextStripper().getText(pdf)).doesNotContain("Signature:");
        }
    }

    @Test
    void shouldReportTheSignature_whenTheStoredSignatureIsNotAnImage() throws Exception {
        var request = request("44", null);
        var printed = composer.print(request, plan(request, "text".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));

        assertThat(printed.notes()).containsExactly(CdsExportConsultationLetterComposer.SIGNATURE_NOTE);
    }

    /** A professional health-care-team contact (TYPE_CONTACT 2) with every value the letter shows. */
    private void teamContact(String category) {
        property("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true");
        when(references.teamLink(12)).thenReturn(new CdsExportPrintedFormReadDao.TeamLink(2, category, "40"));
        when(references.contact(40)).thenReturn(new CdsExportPrintedFormReadDao.Contact(
                "ProfessionalContact", "Nora", "NURSE", "8 Care Rd", "Suite 2", "Testville", "A1A 1A1", "ON",
                "Canada", "555-0500", "12", "555-0501", "12345"));
        // The chart's converter starts from the directory specialist with the contact's id.
        when(references.specialist(40)).thenReturn(new CdsExportPrintedFormReadDao.Specialist(
                "Dr.", "Other", "DIRECTORY", "NP", "1 Elsewhere", "555-0900", "555-0901"));
    }

    @Test
    void shouldPrintAHealthCareTeamContact_asTheChartsPrintShowsIt() throws Exception {
        teamContact("professional");
        var request = request("44", "12", null);
        var printed = composer.print(request, plan(request, png()));

        assertThat(printed.notes()).isEmpty();
        var consultant = composer.consultant(request.rows().getFirst());
        assertThat(consultant.name()).isEqualTo("Dr. NURSE, Nora NP");
        assertThat(consultant.phone()).isEqualTo("555-0500 ext 12");
        assertThat(consultant.fax()).isEqualTo("555-0501");
        assertThat(consultant.address()).isEqualTo("8 Care Rd Suite 2, Testville, A1A 1A1, ON, Canada,");
        try (var pdf = Loader.loadPDF(printed.pdf())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Dr. NURSE, Nora NP", "555-0500 ext 12", "555-0501", "8 Care Rd Suite 2")
                    .doesNotContain("DIRECTORY");
        }
    }

    @Test
    void shouldMatchTheCategoryInAnyCase_withAContactThatHasNoWorkPhoneOrDirectoryLetters() throws Exception {
        teamContact("Professional");
        when(references.contact(40)).thenReturn(new CdsExportPrintedFormReadDao.Contact(
                "ProfessionalContact", "Nora", "NURSE", null, null, "Testville", null, "ON", null, null, null,
                null, " 12345 "));
        when(references.specialist(40)).thenReturn(new CdsExportPrintedFormReadDao.Specialist(
                null, "Other", "DIRECTORY", null, null, null, null));

        var consultant = composer.consultant(request("44", "12", null).rows().getFirst());

        assertThat(consultant.name()).isEqualTo("NURSE, Nora");
        // The chart joins a missing work phone as the text "null"; the letter shows what it shows.
        assertThat(consultant.phone()).isEqualTo("null");
        assertThat(consultant.fax()).isNull();
        assertThat(consultant.address()).isEqualTo(", Testville, , ON, ,");
    }

    @Test
    void shouldPrintTheDirectorySpecialist_whenTheTeamLinkIsNotAProfessionalContact() throws Exception {
        property("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true");
        // A team link to a directory specialist (TYPE_PROFESSIONALSPECIALIST) prints the specialist
        // the request names, as does a personal contact or a contact row that is gone.
        for (var link : List.of(
                new CdsExportPrintedFormReadDao.TeamLink(3, "professional", "9001"),
                new CdsExportPrintedFormReadDao.TeamLink(2, "personal", "40"),
                new CdsExportPrintedFormReadDao.TeamLink(2, "professional", "41"))) {
            when(references.teamLink(12)).thenReturn(link);
            assertThat(composer.consultant(request("44", "12").rows().getFirst()).name())
                    .isEqualTo("Dr. SPECIALIST, Sam MD");
        }
        when(references.teamLink(12)).thenReturn(new CdsExportPrintedFormReadDao.TeamLink(2, "professional", "41"));
        assertThat(composer.consultant(request("44", "12", null).rows().getFirst())).isNull();
    }

    @Test
    void shouldIgnoreTheTeamLink_whenTheHealthCareTeamIsOff() throws Exception {
        property("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "false");
        var request = request("44", "12");

        assertThat(composer.consultant(request.rows().getFirst()).name()).isEqualTo("Dr. SPECIALIST, Sam MD");
        org.mockito.Mockito.verify(references, org.mockito.Mockito.never()).teamLink(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldRefuseTheLetter_whenTheChartCannotResolveTheTeamContact() throws Exception {
        var request = request("44", "12", null);
        var plan = plan(request, png());
        List<Runnable> unprintable = List.of(
                // The link row is gone, or holds no type: the chart cannot load the request.
                () -> when(references.teamLink(12)).thenReturn(null),
                () -> when(references.teamLink(12)).thenReturn(
                        new CdsExportPrintedFormReadDao.TeamLink(null, "professional", "40")),
                // The id it stores is not a number.
                () -> when(references.teamLink(12)).thenReturn(
                        new CdsExportPrintedFormReadDao.TeamLink(2, "professional", "forty")),
                // A personal Contact row under a professional link fails the chart's cast.
                () -> when(references.contact(40)).thenReturn(new CdsExportPrintedFormReadDao.Contact(
                        "Contact", "Nora", "NURSE", null, null, null, null, null, null, null, null, null, null)),
                // No directory specialist has the contact's id, or the contact has no CPSO number.
                () -> when(references.specialist(40)).thenReturn(null),
                () -> when(references.contact(40)).thenReturn(new CdsExportPrintedFormReadDao.Contact(
                        "ProfessionalContact", "Nora", "NURSE", null, null, null, null, null, null, null, null,
                        null, null)));
        for (var change : unprintable) {
            teamContact("professional");
            change.run();
            assertThatThrownBy(() -> composer.print(request, plan))
                    .isInstanceOf(CdsExportDataSummaryOccurrenceService.PrintRefusal.class)
                    .hasMessage("CONSULTANT_CONTACT_UNAVAILABLE");
        }
    }

    @Test
    void shouldPrintOnlyCurrentRequests_forTheConsultationReadRight() {
        var detached = new Group(Kind.CONSULTATION_REQUEST, "consultationRequestExt", "11",
                List.of(row("consultationRequestExt", 11, "id", "11")), 0, 0, null, true);
        var response = new Group(Kind.CONSULTATION_RESPONSE, "consultationResponse", "3",
                List.of(row("consultationResponse", 3, "responseId", "3")), 0, 0, null, false);

        assertThat(composer.prints(request("44", null))).isTrue();
        assertThat(composer.prints(detached)).isFalse();
        assertThat(composer.prints(response)).isFalse();
        assertThat(composer.readObject()).isEqualTo("_con");
        assertThat(CdsExportConsultationLetterComposer.appointmentTime("2024-03-01", "00:07:00"))
                .containsExactly("2024-03-01", "12", "07", "AM");
    }
}
