/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ResourceLock(org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES)
class FixedDaySheetReportServiceUnitTest {
    @TempDir Path directory;

    @Test
    void shouldReturnNoContent_forAnEmptyFixedDaySheet() throws Exception {
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", directory.toString());
            var reports = mock(FixedJasperReportDao.class);
            when(reports.daySheet(java.time.LocalDate.parse("2025-01-01"))).thenReturn(java.util.List.of());
            var response = new MockHttpServletResponse();
            new FixedDaySheetReportService(reports).writePdf(response, "2025-01-01", "labDaySheet.xml");
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.getContentType()).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
        } finally { System.setProperty("user.home", previous); }
    }

    @Test
    void shouldPropagateDatabaseFailure_beforeSettingSuccessHeaders() {
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", directory.toString());
            var reports = mock(FixedJasperReportDao.class);
            when(reports.daySheet(any())).thenThrow(new IllegalStateException("synthetic query failure"));
            var response = new MockHttpServletResponse();
            var service = new FixedDaySheetReportService(reports);
            assertThatThrownBy(() -> service.writePdf(response, "2025-01-01", "labDaySheet.xml"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(response.getContentType()).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
        } finally { System.setProperty("user.home", previous); }
    }
    @Test
    void shouldPreserveCustomSqlAndCloseItsConnection_withoutCallingTheClinicalDao() throws Exception {
        String previous = System.getProperty("user.home");
        var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:jasper5692_override");
        try (var jdbc = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.db.LegacyJdbcQuery.class)) {
            System.setProperty("user.home", directory.toString());
            java.nio.file.Files.writeString(directory.resolve("Addresslabel.xml"), """
                <jasperReport name="override" pageWidth="300" pageHeight="200" columnWidth="260"
                    leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20">
                    <query language="sql"><![CDATA[select 'Override5692' as custom_name]]></query>
                    <field name="custom_name" class="java.lang.String"/>
                    <detail><band height="20"><element kind="textField" x="0" y="0" width="200" height="20">
                        <expression><![CDATA[$F{custom_name}]]></expression>
                    </element></band></detail>
                </jasperReport>
                """);
            jdbc.when(io.github.carlos_emr.carlos.db.LegacyJdbcQuery::getConnection).thenReturn(connection);
            var reports = mock(FixedJasperReportDao.class);
            var response = new MockHttpServletResponse();
            new FixedDaySheetReportService(reports).writePdf(response, null, "labDaySheet.xml");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate");
            assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
            assertThat(connection.isClosed()).isTrue();
            verifyNoInteractions(reports);
            try (var pdf = org.apache.pdfbox.Loader.loadPDF(response.getContentAsByteArray())) {
                assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)).contains("Override5692");
            }
        } finally {
            connection.close();
            System.setProperty("user.home", previous);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2025-02-31", "2025-3-1", "garbage"})
    void shouldRejectMalformedBundledDates_beforeQuerying(String date) {
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", directory.toString());
            var reports = mock(FixedJasperReportDao.class);
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> new FixedDaySheetReportService(reports).writePdf(response, date, "labDaySheet.xml"))
                    .isInstanceOf(FixedDaySheetReportService.InvalidDateException.class);
            verifyNoInteractions(reports);
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(response.getContentType()).isNull();
        } finally { System.setProperty("user.home", previous); }
    }

    @Test
    void shouldRejectANonFileOverride_withoutFallingBackToBundledData() throws Exception {
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", directory.toString());
            java.nio.file.Files.createDirectory(directory.resolve("Addresslabel.xml"));
            var reports = mock(FixedJasperReportDao.class);
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> new FixedDaySheetReportService(reports).writePdf(response, "2025-01-01", "labDaySheet.xml"))
                    .isInstanceOf(SecurityException.class);
            verifyNoInteractions(reports);
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(response.getContentType()).isNull();
        } finally { System.setProperty("user.home", previous); }
    }
}
