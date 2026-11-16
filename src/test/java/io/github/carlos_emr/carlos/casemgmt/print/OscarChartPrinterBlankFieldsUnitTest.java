/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.print;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.RelationshipsDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.Relationships;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The eChart print (Print, then the whole chart) printed the word "null" for empty patient fields: the page
 * title read "Documentation for null" for every patient, because its action sets no {@code demoName}, and the
 * patient information page printed "Health Insurance #: null", "HC Type: null" and so on for empty fields. An
 * empty field now prints as a blank after its label. All values are synthetic.
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("eChart print with empty patient fields")
class OscarChartPrinterBlankFieldsUnitTest extends CarlosUnitTestBase {
    private static final int PATIENT = 900301;
    private static final int RELATIVE = 900302;

    private DemographicCustDao custs;
    private DemographicDao demographics;
    private RelationshipsDao relationships;

    @BeforeEach
    void registerPrinterDependencies() {
        createAndRegisterMock(ProviderDao.class);
        custs = createAndRegisterMock(DemographicCustDao.class);
        demographics = createAndRegisterMock(DemographicDao.class);
        createAndRegisterMock(OscarAppointmentDao.class);
        createAndRegisterMock(PreventionDao.class);
        createAndRegisterMock(DemographicExtDao.class);
        createAndRegisterMock(TicklerManager.class);
        createAndRegisterMock(ClinicDAO.class);
        relationships = createAndRegisterMock(RelationshipsDao.class);
    }

    /** A patient with nothing on file but a number: every other field is empty. */
    private static Demographic emptyPatient() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        return patient;
    }

    private static String text(ByteArrayOutputStream output) throws Exception {
        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    @Test
    @DisplayName("should name the printed patient in the title when the action sets no name")
    void shouldTitleThePrintedPatient_whenNoNameIsSet() throws Exception {
        Demographic patient = emptyPatient();
        patient.setFirstName("FAKE-Ada");
        patient.setLastName("FAKE-Lovelace");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (OscarChartPrinter printer = new OscarChartPrinter(new MockHttpServletRequest(), output)) {
            printer.setDemographic(patient);
            printer.printDocHeaderFooter();
        }

        assertThat(text(output)).contains("Documentation for FAKE-Ada FAKE-Lovelace").doesNotContain("null");
    }

    @Test
    @DisplayName("should keep a name the caller set, and leave out a missing part of the patient's own")
    void shouldPreferTheSetName_andLeaveOutAMissingPart() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Demographic patient = emptyPatient();
        patient.setLastName("FAKE-Lovelace");

        try (OscarChartPrinter printer = new OscarChartPrinter(request, new ByteArrayOutputStream())) {
            printer.setDemographic(patient);
            assertThat(printer.headerName()).isEqualTo("FAKE-Lovelace");

            request.setAttribute("demoName", " ");
            assertThat(printer.headerName()).isEqualTo("FAKE-Lovelace");

            request.setAttribute("demoName", "FAKE-Ada Lovelace");
            assertThat(printer.headerName()).isEqualTo("FAKE-Ada Lovelace");
            printer.printBlankLine(); // OpenPDF cannot close a document with no content.
        }
    }

    @Test
    @DisplayName("should print blanks, not null, for every empty field of the patient information page")
    void shouldPrintBlanks_forEmptyPatientInformationFields() throws Exception {
        DemographicCust cust = new DemographicCust();
        cust.setId(PATIENT);
        // The printer looks the row up by the Integer patient number, so find(Object), not find(int).
        when(custs.find((Object) Integer.valueOf(PATIENT))).thenReturn(cust);
        Relationships relation = new Relationships();
        relation.setId(1);
        relation.setRelationDemographicNo(RELATIVE);
        when(relationships.findByDemographicNumber(PATIENT)).thenReturn(List.of(relation));
        Demographic relative = new Demographic();
        relative.setFirstName("FAKE-Bo");
        relative.setLastName("FAKE-Relative");
        relative.setAlias("FAKE-RelativeAlias");
        when(demographics.getClientByDemographicNo(RELATIVE)).thenReturn(relative);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (OscarChartPrinter printer = new OscarChartPrinter(new MockHttpServletRequest(), output)) {
            printer.setDemographic(emptyPatient());
            printer.printMasterRecord();
        }

        String printed = text(output);
        assertThat(printed).contains("FAKE-Relative, FAKE-Bo (FAKE-RelativeAlias)");
        assertThat(printed).doesNotContain("null").doesNotContain("Notes:");
        // Each empty field prints its bare label, so a placeholder after it ("N/A", "-") fails too.
        assertThat(printed.lines().map(String::strip)).contains("Title:", "Last Name:", "Health Insurance #:",
                "HC Type:", "Work Phone:", "Alerts:");
    }

    @ParameterizedTest(name = "first={0}, last={1}")
    @CsvSource(nullValues = "NULL", value = {
            "NULL, FAKE-Relative, FAKE-Relative",
            "FAKE-Bo, NULL, FAKE-Bo",
            "NULL, NULL, ''"})
    @DisplayName("should omit missing parts of a related patient's printed name")
    void shouldPrintRelatedName_withoutMissingParts(String first, String last, String expectedName) throws Exception {
        DemographicCust cust = new DemographicCust();
        cust.setId(PATIENT);
        when(custs.find((Object) Integer.valueOf(PATIENT))).thenReturn(cust);
        Relationships relation = new Relationships();
        relation.setId(1);
        relation.setRelationDemographicNo(RELATIVE);
        relation.setNotes("FAKE-relation-note");
        when(relationships.findByDemographicNumber(PATIENT)).thenReturn(List.of(relation));
        Demographic relative = new Demographic();
        relative.setFirstName(first);
        relative.setLastName(last);
        when(demographics.getClientByDemographicNo(RELATIVE)).thenReturn(relative);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (OscarChartPrinter printer = new OscarChartPrinter(new MockHttpServletRequest(), output)) {
            printer.setDemographic(emptyPatient());
            printer.printMasterRecord();
        }

        String printed = text(output);
        assertThat(printed).doesNotContain("null");
        assertThat(printed.lines().map(String::strip))
                .contains(("- " + expectedName + " -  -  - FAKE-relation-note").strip());
    }

    @Test
    @DisplayName("should print the patient's notes when there are some")
    void shouldPrintNotes_whenThePatientHasSome() throws Exception {
        DemographicCust cust = new DemographicCust();
        cust.setId(PATIENT);
        cust.setNotes("<unotes>FAKE-prefers mornings</unotes>");
        // The printer looks the row up by the Integer patient number, so find(Object), not find(int).
        when(custs.find((Object) Integer.valueOf(PATIENT))).thenReturn(cust);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (OscarChartPrinter printer = new OscarChartPrinter(new MockHttpServletRequest(), output)) {
            printer.setDemographic(emptyPatient());
            printer.printMasterRecord();
        }

        assertThat(text(output)).contains("Notes: FAKE-prefers mornings");
    }
}
