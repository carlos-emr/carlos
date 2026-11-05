/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class DemographicLabelPdfUnitTest {
    private static ByteArrayInputStream template(String query) {
        return new ByteArrayInputStream(("""
            <jasperReport name="testLabel" pageWidth="300" pageHeight="75" columnWidth="300" leftMargin="0" rightMargin="0" topMargin="0" bottomMargin="0">
              <query><![CDATA[%s]]></query>
              <field name="NAME" class="java.lang.String"/>
              <title height="20"><element kind="textField" x="0" y="0" width="280" height="20"><expression>$F{NAME}</expression></element></title>
            </jasperReport>
            """).formatted(query).getBytes(StandardCharsets.UTF_8));
    }

    @Test void shouldWriteCompletePdfAndCloseConnection_whenTemplateAndDataArePresent() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:labelPdf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(connection);
            DemographicLabelPdf.write(response, Map.of(), template("SELECT 'TestGiven TestFamily' AS NAME"), null);
        }
        assertThat(connection.isClosed()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentLength()).isEqualTo(response.getContentAsByteArray().length);
        try (PdfReader pdf = new PdfReader(response.getContentAsByteArray())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(new PdfTextExtractor(pdf).getTextFromPage(1)).contains("TestGiven TestFamily");
        }
    }

    @Test void shouldReturnFailureBeforeOpeningDatabase_whenTemplateIsMalformed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            DemographicLabelPdf.write(response, Map.of(), new ByteArrayInputStream("invalid".getBytes(StandardCharsets.UTF_8)), null);
            jdbc.verifyNoInteractions();
        }
        assertFailure(response);
    }

    @Test void shouldEmbedAutoPrintScript_whenPrinterPreferenceIsPresent() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:labelAutoPrintPdf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String script = "this.print({bUI: true, bSilent: false, bShrinkToFit: true});";
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(connection);
            DemographicLabelPdf.write(response, Map.of(), template("SELECT 'FAKE Auto Print' AS NAME"), script);
        }
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(connection.isClosed()).isTrue();
        try (var pdf = Loader.loadPDF(response.getContentAsByteArray())) {
            assertThat(pdf.getDocumentCatalog().getNames().getJavaScript().getNames().values())
                    .extracting(PDActionJavaScript::getAction).containsExactly(script);
        }
    }

    @Test void shouldReturnFailure_whenTemplateIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (LogCapture logs = LogCapture.forLogger(DemographicLabelPdf.class)) {
            DemographicLabelPdf.write(response, Map.of(), null, null);
            assertDiagnostic(logs, "loading label template", "net.sf.jasperreports.engine.JRException");
        }
        assertFailure(response);
    }

    @Test void shouldReturnFailureWithoutDatabaseDetails_whenConnectionFails() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (LogCapture logs = LogCapture.forLogger(DemographicLabelPdf.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(LegacyJdbcQuery::getConnection).thenThrow(new SQLException("private database details"));
            DemographicLabelPdf.write(response, Map.of(), template("SELECT 'name' AS NAME"), null);
            assertDiagnostic(logs, "opening label database connection", "java.sql.SQLException");
            assertThat(logs.events().getFirst().getMessage().getFormattedMessage()).doesNotContain("private database details");
        }
        assertFailure(response);
        assertThat(response.getErrorMessage()).doesNotContain("private");
    }

    @Test void shouldReturnFailureAndCloseConnection_whenReportHasNoPages() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:emptyLabelPdf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (LogCapture logs = LogCapture.forLogger(DemographicLabelPdf.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(connection);
            DemographicLabelPdf.write(response, Map.of(), template("SELECT 'name' AS NAME WHERE 1=0"), null);
            assertDiagnostic(logs, "label contains no patient data", "net.sf.jasperreports.engine.JRException");
        }
        assertFailure(response);
        assertThat(connection.isClosed()).isTrue();
    }

    @Test
    @DisplayName("should log cause locations without clinical messages when Jasper filling fails")
    void shouldLogCauseLocationsWithoutClinicalMessages_whenJasperFillingFails() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:failedLabelFill");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SQLException cause = new SQLException("FAKE clinical SQL value");
        cause.addSuppressed(new IllegalStateException("FAKE suppressed patient details"));
        JRException failure = new JRException("FAKE report expression value", cause);
        try (LogCapture logs = LogCapture.forLogger(DemographicLabelPdf.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedStatic<JasperFillManager> fill = mockStatic(JasperFillManager.class)) {
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(connection);
            fill.when(() -> JasperFillManager.fillReport(any(net.sf.jasperreports.engine.JasperReport.class), anyMap(), eq(connection)))
                    .thenThrow(failure);
            DemographicLabelPdf.write(response, Map.of("demo", "FAKE request value"), template("SELECT 'name' AS NAME"), null);
            assertDiagnostic(logs, "filling label report", "net.sf.jasperreports.engine.JRException");
            assertThat(logs.events().getFirst().getMessage().getFormattedMessage())
                    .contains("Caused by: java.sql.SQLException", "Suppressed: java.lang.IllegalStateException")
                    .doesNotContain("FAKE", "clinical SQL value", "patient details", "expression value", "request value");
        }
        assertFailure(response);
        assertThat(connection.isClosed()).isTrue();
        assertThat(response.getErrorMessage()).isEqualTo("Unable to generate patient label");
    }

    private static void assertDiagnostic(LogCapture logs, String context, String exceptionType) {
        assertThat(logs.events()).hasSize(1);
        var event = logs.events().getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrown()).isNull();
        assertThat(event.getMessage().getFormattedMessage())
                .contains("Label PDF generation failed while ", context, exceptionType, "DemographicLabelPdf.java:");
    }

    private static void assertFailure(MockHttpServletResponse response) {
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
