/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
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

    @Test void shouldReturnFailure_whenTemplateIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        DemographicLabelPdf.write(response, Map.of(), null, null);
        assertFailure(response);
    }

    @Test void shouldReturnFailureWithoutDatabaseDetails_whenConnectionFails() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(LegacyJdbcQuery::getConnection).thenThrow(new SQLException("private database details"));
            DemographicLabelPdf.write(response, Map.of(), template("SELECT 'name' AS NAME"), null);
        }
        assertFailure(response);
        assertThat(response.getErrorMessage()).doesNotContain("private");
    }

    @Test void shouldReturnFailureAndCloseConnection_whenReportHasNoPages() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:emptyLabelPdf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(connection);
            DemographicLabelPdf.write(response, Map.of(), template("SELECT 'name' AS NAME WHERE 1=0"), null);
        }
        assertFailure(response);
        assertThat(connection.isClosed()).isTrue();
    }

    private static void assertFailure(MockHttpServletResponse response) {
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
