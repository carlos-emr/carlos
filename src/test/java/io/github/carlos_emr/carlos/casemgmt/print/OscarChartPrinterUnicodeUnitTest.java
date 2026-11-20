/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.print;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prints chart sections through the real {@link OscarChartPrinter} and checks record text in any
 * script reaches the PDF (issue #5046).
 *
 * <p>The printer built every section from {@code Phrase.add(String)} and font-less chunks in
 * DejaVu, which has no Chinese, Japanese or Korean characters, so they were left out of the
 * printed chart. The sections below cover each way the class adds text: plain strings, font-less
 * chunks, underlined heading chunks and table-cell paragraphs. All values are synthetic.
 */
@Tag("unit")
@Tag("report")
class OscarChartPrinterUnicodeUnitTest extends CarlosUnitTestBase {
    private OscarAppointmentDao appointmentDao;
    private ProviderDao providerDao;

    @BeforeEach
    void registerPrinterDependencies() {
        providerDao = createAndRegisterMock(ProviderDao.class);
        createAndRegisterMock(DemographicCustDao.class);
        createAndRegisterMock(DemographicDao.class);
        appointmentDao = createAndRegisterMock(OscarAppointmentDao.class);
        createAndRegisterMock(PreventionDao.class);
        createAndRegisterMock(DemographicExtDao.class);
        createAndRegisterMock(TicklerManager.class);
    }

    private static CaseManagementNote note(String text) {
        CaseManagementNote note = new CaseManagementNote();
        note.setNote(text);
        note.setObservation_date(new Date(1_790_000_000_000L));
        return note;
    }

    private static String text(ByteArrayOutputStream output) throws Exception {
        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            for (int page = 0; page < pdf.getNumberOfPages(); page++) {
                var resources = pdf.getPage(page).getResources();
                for (var name : resources.getFontNames()) {
                    assertThat(resources.getFont(name).isEmbedded()).as("%s embedded", resources.getFont(name).getName())
                            .isTrue();
                }
            }
            return new PDFTextStripper().getText(pdf);
        }
    }

    @Test
    void shouldPrintNativePrecision_whenPreventionTimingIsPartialOrUnknown() throws Exception {
        var dates = mock(io.github.carlos_emr.carlos.commn.dao.PartialDateDao.class);
        when(dates.getFormat(4, 41, 7)).thenReturn("YYYY-MM");
        var partial = new io.github.carlos_emr.carlos.commn.model.Prevention();
        org.springframework.test.util.ReflectionTestUtils.setField(partial, "id", 41);
        partial.setPreventionType("Td");
        partial.setPreventionDate(java.sql.Date.valueOf("2020-02-01"));
        var unknown = new io.github.carlos_emr.carlos.commn.model.Prevention();
        unknown.setPreventionType("MMR");
        var dao = createAndRegisterMock(PreventionDao.class);
        when(dao.findNotDeletedByDemographicId(7)).thenReturn(List.of(partial, unknown));
        var patient = new Demographic();
        patient.setDemographicNo(7);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (OscarChartPrinter printer = new OscarChartPrinter(mock(HttpServletRequest.class), output,
                () -> new io.github.carlos_emr.carlos.prevention.PreventionDateService(dates))) {
            printer.setDemographic(patient);
            printer.printPreventions();
        }
        assertThat(text(output)).contains("Td 2020-02", "MMR Unknown").doesNotContain("2020-02-01");
    }

    @Test
    @DisplayName("should print notes, CPP items and the allergies after them in Chinese, Japanese and Korean")
    void shouldPrintClinicalSections_inChineseJapaneseAndKorean() throws Exception {
        Allergy allergy = new Allergy();
        allergy.setDescription("FAKE-페니실린 hives");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (OscarChartPrinter printer = new OscarChartPrinter(mock(HttpServletRequest.class), output)) {
            printer.printNotes(List.of(note("FAKE-复诊 ≥5 mmol/L")), true);
            printer.printNotes(List.of(note("FAKE-さくら follow-up")), false);
            printer.printCPPItem("Social History", List.of(note("FAKE-김민준 lives alone")));
            printer.printAllergies(List.of(allergy));
        }

        assertThat(text(output)).contains("FAKE-复诊 ≥5 mmol/L", "FAKE-さくら follow-up", "Impression/Plan:",
                "FAKE-김민준 lives alone", "FAKE-페니실린 hives");
    }

    @Test
    @DisplayName("should print appointment history cells in Chinese, Japanese and Korean")
    void shouldPrintAppointmentHistory_inChineseJapaneseAndKorean() throws Exception {
        Demographic patient = new Demographic();
        patient.setDemographicNo(5046);
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(new Date(1_790_000_000_000L));
        appointment.setStartTime(new Date(1_790_000_000_000L));
        appointment.setEndTime(new Date(1_790_000_900_000L));
        appointment.setReason("FAKE-头痛");
        appointment.setNotes("FAKE-さくら");
        appointment.setProviderNo("999998");
        when(appointmentDao.getAppointmentHistory(anyInt())).thenReturn(List.of(appointment));
        Provider provider = new Provider();
        provider.setLastName("FAKE-김");
        provider.setFirstName("민준");
        when(providerDao.getProvider("999998")).thenReturn(provider);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (OscarChartPrinter printer = new OscarChartPrinter(mock(HttpServletRequest.class), output)) {
            printer.setDemographic(patient);
            printer.printAppointmentHistory();
        }

        assertThat(text(output)).contains("FAKE-头痛", "FAKE-さくら", provider.getFormattedName());
    }
}
