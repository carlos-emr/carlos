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

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.AuditFormat;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNamePrefixCode;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSuffixCode;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportClass;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportFormat;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportMedia;
import jakarta.xml.bind.annotation.XmlEnumValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Parses the shipped demo HRM report fixture end to end through {@link HRMReportParser} and pins
 * the JAXB enum mappings the decompiled model lost.
 *
 * <p>The {@code hospitalReportManager.xsd} sources were recovered by decompiling the generated
 * {@code cds_hrm} jar, and the decompiler dropped every per-constant {@code @XmlEnumValue}. JAXB
 * then matched XML values such as {@code Diagnostic Imaging Report} against the constant
 * <em>names</em> ({@code DIAGNOSTIC_IMAGING_REPORT}), unmarshalled them to {@code null}, and
 * {@link HRMReport#getFirstReportClass()} threw a NullPointerException for the first report any
 * HRM list rendered: the attach popups showed "No HRM documents available" or a 500. The fixture
 * is the same file the deb validation runbook seeds so the Rich Text Letter attachment checks can
 * exercise the HRM family.
 *
 * @since 2026-09-02
 */
@Tag("unit")
@Tag("fast")
@DisplayName("HRMReportParser demo fixture and enum mappings")
class HRMReportParserFixtureUnitTest {

    private static final Path FIXTURE = Path.of(".devcontainer/db/db_data/hrm/demo-hrm-diagnostic-imaging.xml");

    @TempDir
    Path documentDir;

    @Test
    @DisplayName("should parse the demo HRM fixture into a report with its class, format, media and text")
    void shouldParseDemoFixture_withReportClassFormatMediaAndText() throws Exception {
        Files.copy(FIXTURE, documentDir.resolve("demo-hrm.xml"));
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());

            List<Throwable> errors = new ArrayList<>();
            HRMReport report = HRMReportParser.parseReport(null, "demo-hrm.xml", errors);

            assertThat(errors).isEmpty();
            assertThat(report).isNotNull();
            // Each of these read an enum the decompiled model could not unmarshal.
            assertThat(report.getFirstReportClass()).isEqualTo("Diagnostic Imaging Report");
            assertThat(report.getMediaType()).isEqualTo("Download");
            assertThat(report.getFirstReportTextContent()).contains("SEED-HRM-ATTACHMENT-MARKER");
            assertThat(report.getFirstReportSubClass()).isEqualTo("X-Ray");
            assertThat(report.getLegalName()).contains("FAKE-Jones");
            assertThat(report.getSendingFacilityId()).isEqualTo("DEMO");
            assertThat(report.getMessageUniqueId()).isEqualTo("demo-hrm-diagnostic-imaging-0001");
            assertThat(report.getResultStatus()).isEqualTo("S");
        }
    }

    @Test
    @DisplayName("should date an imaging report from its first observation, and fall back to the event time without one")
    void shouldDateImagingReport_fromObservationOrEventTime() throws Exception {
        String fixture = Files.readString(FIXTURE);
        Files.writeString(documentDir.resolve("with-obr.xml"), fixture);
        // A schema-valid imaging report may omit OBRContent entirely; that used to index an empty
        // list and fail every PDF packet the report was attached to.
        Files.writeString(documentDir.resolve("without-obr.xml"),
                fixture.replaceAll("(?s)\\s*<OBRContent>.*?</OBRContent>", ""));
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());

            HRMReport withObservation = HRMReportParser.parseReport(null, "with-obr.xml", new ArrayList<>());
            assertThat(withObservation.getAccompanyingSubclassList()).hasSize(1);
            assertThat(HRMReportParser.getAppropriateDateStringFromReport(withObservation))
                    .isEqualTo(withObservation.getFirstAccompanyingSubClassDateTime());
            assertThat(HRMReportParser.getAppropriateDateFromReport(withObservation)).isNotNull();

            HRMReport withoutObservation = HRMReportParser.parseReport(null, "without-obr.xml", new ArrayList<>());
            assertThat(withoutObservation.getFirstReportClass()).isEqualTo("Diagnostic Imaging Report");
            assertThat(withoutObservation.getAccompanyingSubclassList()).isEmpty();
            assertThat(HRMReportParser.getAppropriateDateStringFromReport(withoutObservation))
                    .isNotEmpty()
                    .contains("2024");
            assertThat(HRMReportParser.getAppropriateDateFromReport(withoutObservation))
                    .isEqualTo(withoutObservation.getFirstReportEventTime().getTime());
        }
    }

    static Stream<Class<? extends Enum<?>>> decompiledEnums() {
        return Stream.of(ReportClass.class, ReportFormat.class, ReportMedia.class, AuditFormat.class,
                PersonNamePrefixCode.class, PersonNameSuffixCode.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decompiledEnums")
    @DisplayName("should map every schema value onto its constant with @XmlEnumValue")
    void shouldCarryXmlEnumValue_forEveryConstantWhoseNameDiffersFromItsValue(Class<? extends Enum<?>> enumType)
            throws Exception {
        for (Enum<?> constant : enumType.getEnumConstants()) {
            String xmlValue = (String) enumType.getMethod("value").invoke(constant);
            Field field = enumType.getField(constant.name());
            XmlEnumValue mapping = field.getAnnotation(XmlEnumValue.class);
            if (constant.name().equals(xmlValue)) {
                continue;
            }
            assertThat(mapping)
                    .as("%s.%s must declare @XmlEnumValue(\"%s\") or JAXB unmarshals it to null",
                            enumType.getSimpleName(), constant.name(), xmlValue)
                    .isNotNull();
            assertThat(mapping.value()).isEqualTo(xmlValue);
        }
    }
}
