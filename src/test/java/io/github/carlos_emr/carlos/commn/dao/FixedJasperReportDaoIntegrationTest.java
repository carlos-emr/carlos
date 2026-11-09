/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.report.FixedJasperDataSources;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.sql.Time;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperExportManager;
import static org.assertj.core.api.Assertions.assertThat;

/** Compares detached day-sheet projections with the JDBC templates in jasper-5692-baseline. */
@Tag("integration")
@Tag("dao")
@Tag("mariadb-native")
class FixedJasperReportDaoIntegrationTest extends CarlosTestBase {
    @Autowired private FixedJasperReportDao reports;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    void shouldPreserveDateOnlySelectionAndNativeTypes_forBothDaySheetTemplates() throws Exception {
        Appointment first = appointment("5692-one", "A", "2025-03-01");
        Appointment cancelled = appointment("5692-cancelled", "C", "2025-03-01");
        appointment("5692-other-day", "A", "2025-03-02");
        entityManager.flush();
        entityManager.clear();
        var rows = reports.daySheet(java.time.LocalDate.parse("2025-03-01"));
        assertThat(rows).extracting(FixedJasperReportDao.DaySheetRow::appointmentNo)
                .containsExactlyInAnyOrder(first.getId(), cancelled.getId());
        assertThat(rows.getFirst().startTime()).isInstanceOf(Time.class);
        assertThat(rows.getFirst().appointmentDate()).isEqualTo(Date.valueOf("2025-03-01"));
        assertThat(rows.getFirst().notes()).isNull();
        for (String name : new String[]{"labDaySheet.xml", "billDaySheet.xml"}) {
            try (var template = getClass().getResourceAsStream("/oscar/oscarReport/pageUtil/" + name);
                 var baseline = getClass().getResourceAsStream("/jasper-5692-baseline/" + name)) {
                var parameters = new java.util.HashMap<String, Object>(Map.of("input_date", "2025-03-01"));
                var actual = JasperFillManager.fillReport(JasperCompileManager.compileReport(template), parameters,
                        FixedJasperDataSources.daySheet(rows));
                var original = JasperCompileManager.compileReport(baseline);
                var expected = entityManager.unwrap(org.hibernate.Session.class).doReturningWork(connection -> {
                    try { return JasperFillManager.fillReport(original, new java.util.HashMap<>(Map.<String, Object>of("input_date", "2025-03-01")), connection); }
                    catch (net.sf.jasperreports.engine.JRException ex) { throw new java.sql.SQLException(ex); }
                });
                assertThat(actual.getPages()).hasSameSizeAs(expected.getPages());
                assertThat(text(actual)).isEqualTo(text(expected)).contains("5692-one", "5692-cancelled");
            }
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void shouldKeepNoPages_forAnEmptyDay() throws Exception {
        var rows = reports.daySheet(java.time.LocalDate.parse("1901-01-01"));
        assertThat(rows).isEmpty();
        try (var template = getClass().getResourceAsStream("/oscar/oscarReport/pageUtil/labDaySheet.xml")) {
            var print = JasperFillManager.fillReport(JasperCompileManager.compileReport(template),
                    new java.util.HashMap<>(Map.<String, Object>of("input_date", "1901-01-01")), FixedJasperDataSources.daySheet(rows));
            assertThat(print.getPages()).isEmpty();
        }
    }

    private Appointment appointment(String name, String status, String day) {
        var row = new Appointment();
        row.setProviderNo("956920");
        row.setAppointmentDate(Date.valueOf(day));
        row.setStartTime(Time.valueOf("09:12:00"));
        row.setEndTime(Time.valueOf("09:27:00"));
        row.setName(name);
        row.setStatus(status);
        row.setDemographicNo(0);
        entityManager.persist(row);
        return row;
    }

    private static String text(net.sf.jasperreports.engine.JasperPrint print) throws Exception {
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(JasperExportManager.exportReportToPdf(print))) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
        }
    }
}
