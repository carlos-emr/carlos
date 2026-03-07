/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 PATHL7HandlerTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.HL7Exception;

/**
 * Unit tests for {@link PATHL7Handler} (Excelleris BC/ON HL7 lab parser).
 *
 * <p>Parameterized tests that validate HL7 message parsing across all test data
 * from the Excelleris test archive. Each HL7 message from the ZIP is parsed and
 * validated for correct extraction of demographics, results, and metadata.
 * Migrated from legacy JUnit 4 parameterized PATHL7HandlerTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("lab")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("PATHL7Handler parameterized unit tests")
class PATHL7HandlerUnitTest {

    static Stream<String> hl7MessageProvider() {
        URL url = Thread.currentThread().getContextClassLoader().getResource("EXCELLERIS_test_data.zip");
        if (url == null) {
            return Stream.empty();
        }

        List<String> messages = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(url.getPath())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".txt")) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(is, writer, "UTF-8");
                        messages.add(writer.toString());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load EXCELLERIS test data", e);
        }
        return messages.stream();
    }

    private PATHL7Handler createHandler(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = new PATHL7Handler();
        handler.init(hl7Body);
        return handler;
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse sending application from MSH segment")
    void shouldParseSendingApplication_fromMshSegment(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.getSendingApplication()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse message date from MSH segment")
    void shouldParseMsgDate_fromMshSegment(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.getMsgDate()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse OBR count matching message structure")
    void shouldParseOBRCount_matchingMessageStructure(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.getOBRCount()).isPositive();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse OBX counts for all OBR segments")
    void shouldParseOBXCounts_forAllOBRSegments(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        int totalObx = 0;
        for (int i = 0; i < handler.getOBRCount(); i++) {
            totalObx += handler.getOBXCount(i);
        }
        assertThat(totalObx).isPositive();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse patient demographics from PID segment")
    void shouldParsePatientDemographics_fromPidSegment(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.getLastName()).isNotNull();
        assertThat(handler.getFirstName()).isNotNull();
        assertThat(handler.getSex()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse OBX result values for all segments")
    void shouldParseOBXResultValues_forAllSegments(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        for (int i = 0; i < handler.getOBRCount(); i++) {
            for (int j = 0; j < handler.getOBXCount(i); j++) {
                assertThat(handler.getOBXName(i, j)).isNotNull();
                assertThat(handler.getOBXResult(i, j)).isNotNull();
                assertThat(handler.getOBXResultStatus(i, j)).isNotNull();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse ordering provider information")
    void shouldParseOrderingProvider_information(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.getDocName()).isNotNull();
        assertThat(handler.getDocNums()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse service date from OBR segment")
    void shouldParseServiceDate_fromObrSegment(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.getServiceDate()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse order status")
    void shouldParseOrderStatus(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.getOrderStatus()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should parse filler order number")
    void shouldParseFillerOrderNumber(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.getFillerOrderNumber()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("hl7MessageProvider")
    @DisplayName("should pass audit check")
    void shouldPassAuditCheck(String hl7Body) throws HL7Exception {
        PATHL7Handler handler = createHandler(hl7Body);
        assertThat(handler.audit()).isEqualTo("success");
    }
}
