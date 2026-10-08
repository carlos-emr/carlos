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
package io.github.carlos_emr.carlos.hospitalReportManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HRMReportParser#parseReport} path-containment handling, and for what the
 * parser writes to the log.
 *
 * <p>parseReport resolves a DB-sourced report-file location against {@code DOCUMENT_DIR} via
 * {@code PathValidationUtils}. A misconfigured {@code DOCUMENT_DIR} or a stored location that escapes
 * it makes the validation throw {@code SecurityException}/{@code FileValidationException}; the parser
 * must honour its {@code null}-return + {@code errors}-collection contract (so one bad document does
 * not abort an HRM list render / batch import) instead of letting the unchecked exception escape.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("HRMReportParser.parseReport path containment and logging")
class HRMReportParserUnitTest {

    @TempDir
    Path documentDir;

    @Test
    @DisplayName("should return null and record the error when the report path escapes DOCUMENT_DIR")
    void shouldReturnNullAndRecordError_whenReportPathEscapesDocumentDir() {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());

            List<Throwable> errors = new ArrayList<>();

            // A traversal report location resolves outside DOCUMENT_DIR; validateExistingPath rejects it.
            // The fix must degrade to null + errors rather than throwing out of parseReport.
            assertThatCode(() -> {
                Object report = HRMReportParser.parseReport(null, "../escaped-report.xml", errors);
                assertThat(report).isNull();
            }).doesNotThrowAnyException();

            assertThat(errors).isNotEmpty();
        }
    }

    @Test
    @DisplayName("should return null and record the error when DOCUMENT_DIR is misconfigured (blank)")
    void shouldReturnNullAndRecordError_whenDocumentDirBlank() {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("DOCUMENT_DIR")).thenReturn("");

            List<Throwable> errors = new ArrayList<>();

            // A blank DOCUMENT_DIR makes resolveConfiguredDirectory throw; must not escape parseReport.
            assertThatCode(() -> {
                Object report = HRMReportParser.parseReport(null, "report.xml", errors);
                assertThat(report).isNull();
            }).doesNotThrowAnyException();

            assertThat(errors).isNotEmpty();
        }
    }

    @Test
    @DisplayName("should name a report by a short fingerprint of its location, never the location")
    void shouldReturnShortFingerprint_forReportLocation() {
        String reference = HRMReportParser.logReference("FAKE-PATIENT-NAME/report.xml");

        assertThat(reference).matches("ref:[0-9a-f]{12}").doesNotContain("FAKE");
        assertThat(HRMReportParser.logReference("FAKE-PATIENT-NAME/report.xml")).isEqualTo(reference);
        assertThat(HRMReportParser.logReference("FAKE-OTHER-NAME/report.xml")).isNotEqualTo(reference);
        assertThat(HRMReportParser.logReference(null)).isEqualTo("ref:none");
    }

    @Test
    @DisplayName("should log neither the location nor the parser's message when a report fails to parse")
    void shouldLogTypeAndReferenceOnly_whenReportFailsToParse() throws Exception {
        // The file name stands in for a name in the path; the element name for report content
        // that a schema-validation message repeats.
        String location = "FAKE-PATIENT-NAME-report.xml";
        Files.writeString(documentDir.resolve(location), "<FAKE-REPORT-VALUE/>", StandardCharsets.UTF_8);

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class);
             LogCapture logs = LogCapture.forLogger(HRMReportParser.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());

            List<Throwable> errors = new ArrayList<>();
            assertThat(HRMReportParser.parseReport(null, location, errors)).isNull();

            // The caller still gets the full exception; only the log is reduced.
            assertThat(errors).hasSize(1);
            assertThat(String.valueOf(errors.get(0).getCause())).contains("FAKE-REPORT-VALUE");
            assertThat(logs.messages()).isNotEmpty()
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain("FAKE-PATIENT-NAME")
                            .doesNotContain("FAKE-REPORT-VALUE")
                            .doesNotContain(documentDir.toString()))
                    .anySatisfy(message -> assertThat(message)
                            .contains(HRMReportParser.logReference(location))
                            .containsPattern("could not be unmarshalled \\(UnmarshalException <- SAXParseException\\w* at line \\d+, column \\d+\\)"));
            assertThat(logs.events()).extracting(LogEvent::getThrown).containsOnlyNulls();
        }
    }

    @Test
    @DisplayName("should log a rejected report path by its reference only, in the parser's own log")
    void shouldLogReferenceOnly_whenReportPathRejected() {
        String location = "../FAKE-PATIENT-NAME-report.xml";
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class);
             LogCapture logs = LogCapture.forLogger(HRMReportParser.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());

            assertThat(HRMReportParser.parseReport(null, location, new ArrayList<>())).isNull();

            assertThat(logs.messages())
                    .anySatisfy(message -> assertThat(message).contains(HRMReportParser.logReference(location)))
                    .allSatisfy(message -> assertThat(message).doesNotContain("FAKE-PATIENT-NAME"));
            assertThat(logs.events()).extracting(LogEvent::getThrown).containsOnlyNulls();
        }
    }

    @Test
    @DisplayName("should log a missing report file by its reference only, in one line")
    void shouldLogReferenceOnly_whenReportFileMissing() {
        String location = "FAKE-PATIENT-NAME-missing.xml";
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class);
             LogCapture logs = LogCapture.forLogger(HRMReportParser.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());

            List<Throwable> errors = new ArrayList<>();
            assertThat(HRMReportParser.parseReport(null, location, errors)).isNull();

            // A missing file's exception message is its path.
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("FAKE-PATIENT-NAME");
            assertThat(logs.messages())
                    .contains("HRM report " + HRMReportParser.logReference(location) + " could not be opened (FileNotFoundException)")
                    .allSatisfy(message -> assertThat(message).doesNotContain("FAKE-PATIENT-NAME"));
            // One line at the levels a server runs at; the trace is for debug only.
            assertThat(logs.events())
                    .filteredOn(event -> event.getLevel().isMoreSpecificThan(Level.INFO))
                    .isNotEmpty()
                    .allSatisfy(event -> assertThat(event.getMessage().getFormattedMessage()).doesNotContain("\n"));
            assertThat(logs.events()).extracting(LogEvent::getThrown).containsOnlyNulls();
        }
    }
}
