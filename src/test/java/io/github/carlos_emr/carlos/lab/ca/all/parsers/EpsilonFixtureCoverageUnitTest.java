/**
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 * <p>
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;

/**
 * Additional Epsilon fixture regressions adapted from Lakshmidevi Thirupathi's PR #4125.
 *
 * <p>The broader production fix and upload/display validation landed through PR #4147.
 * These assertions preserve the contributor's per-patient fixture checks, header consistency,
 * chemistry ordering and literal OBR names while using the production upload splitter.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("Epsilon fixture coverage")
class EpsilonFixtureCoverageUnitTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "labs", "HL7-Epsilon");

    private static List<String> separateMessages(String fixtureName, Path documentDir) throws Exception {
        Path feed = documentDir.resolve(fixtureName);
        Files.copy(FIXTURES.resolve(fixtureName), feed);
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        try (MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            return Utilities.separateMessages(feed.toString());
        }
    }

    private static EpsilonHandler handler(String message) throws Exception {
        EpsilonHandler handler = new EpsilonHandler();
        handler.init(message);
        return handler;
    }

    private static long countSegments(String message, String name) {
        return message.lines().filter(line -> line.startsWith(name + "|")).count();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"medhealth-general.hl7", "medhealth-pap.hl7"})
    @DisplayName("should expose every stored patient's OBR and OBX rows and matching headers")
    void shouldExposeRowsAndHeaders_forEveryStoredPatient(String fixtureName,
                                                         @TempDir Path documentDir) throws Exception {
        List<String> messages = separateMessages(fixtureName, documentDir);
        assertThat(messages).as("separated patients in %s", fixtureName).isNotEmpty();
        for (String message : messages) {
            EpsilonHandler handler = handler(message);
            assertThat(handler.getOBRCount()).isEqualTo((int) countSegments(message, "OBR"));
            assertThat(handler.getOBRCount()).isPositive();
            List<String> headers = handler.getHeaders();
            assertThat(headers).isNotEmpty();

            int obxTotal = 0;
            for (int i = 0; i < handler.getOBRCount(); i++) {
                obxTotal += handler.getOBXCount(i);
                for (int j = 0; j < handler.getOBXCount(i); j++) {
                    assertThat(handler.getOBXValueType(i, j)).isNotBlank();
                    assertThat(handler.getOBXIdentifier(i, j)).isNotBlank();
                    assertThat(headers).contains(handler.getOBXIdentifier(i, j));
                    assertThat(handler.getOBXResult(i, j)).isNotNull();
                    assertThat(handler.getOBXReferenceRange(i, j)).isNotNull();
                    assertThat(handler.getOBXUnits(i, j)).isNotNull();
                    assertThat(handler.getOBXAbnormalFlag(i, j)).isNotNull();
                    assertThat(handler.getOBXResultStatus(i, j)).isNotNull();
                }
            }
            assertThat(obxTotal).isEqualTo((int) countSegments(message, "OBX"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"medhealth-general.hl7", "medhealth-pap.hl7"})
    @DisplayName("should return every OBR name and identifier as written in each patient's message")
    void shouldReturnLiteralObrFields_forEveryStoredPatient(String fixtureName,
                                                          @TempDir Path documentDir) throws Exception {
        for (String message : separateMessages(fixtureName, documentDir)) {
            EpsilonHandler handler = handler(message);
            List<String> orders = message.lines().filter(line -> line.startsWith("OBR|")).toList();
            assertThat(handler.getOBRCount()).isEqualTo(orders.size());
            for (int i = 0; i < orders.size(); i++) {
                String[] fields = orders.get(i).split("\\|", -1);
                String[] obr4 = fields[4].split("\\^", -1);
                String expectedId = obr4[0];
                String expectedName = obr4.length > 1 && !obr4[1].isEmpty() ? obr4[1] : expectedId;
                assertThat(handler.getOBRIdentifier(i)).isEqualTo(expectedId);
                assertThat(handler.getOBRName(i)).isEqualTo(expectedName);
            }
        }
    }

    @Test
    @DisplayName("should preserve the chemistry patient's result ordering and LDL value")
    void shouldKeepChemistryResultOrder_forGeneralFixture(@TempDir Path documentDir) throws Exception {
        EpsilonHandler handler = handler(separateMessages("medhealth-general.hl7", documentDir).get(1));

        assertThat(handler.getOBRCount()).isEqualTo(1);
        assertThat(handler.getOBXCount(0)).isEqualTo(15);
        assertThat(handler.getOBXValueType(0, 0)).isEqualTo("NM");
        assertThat(handler.getOBXResult(0, 0)).isEqualTo("4.2");
        assertThat(handler.getOBXIdentifier(0, 9)).isEqualTo("GENERAL CHEMISTRY");
        assertThat(handler.getOBXName(0, 9)).isEqualTo("LDL");
        assertThat(handler.getOBXResult(0, 9)).isEqualTo("2.59");
    }

    @Test
    @DisplayName("should preserve blank OBR fields when the sender leaves them empty")
    void shouldKeepBlankObrFields_forGeneralFixture(@TempDir Path documentDir) throws Exception {
        EpsilonHandler handler = handler(separateMessages("medhealth-general.hl7", documentDir).get(0));

        assertThat(handler.getOBRName(0)).isEmpty();
        assertThat(handler.getOBRIdentifier(0)).isEmpty();
    }
}
