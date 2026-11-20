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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.HL7Exception;

/**
 * Behaviour the lab handlers kept through the HAPI 1.0.1 to 2.6.0 upgrade (issue #5414): a lookup
 * of a request, result or comment that is not in the message answers with an empty value or zero,
 * the checked {@link HL7Exception} contract of {@link CLSHandler}'s accessors survives, and the
 * one place HAPI 2.x changes the text of a value is pinned.
 */
@Tag("unit")
@Tag("lab")
@Tag("hl7")
@DisplayName("Lab handlers after the HAPI 2.x upgrade")
class HapiAccessFailureFallbackUnitTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = HapiAccessFailureFallbackUnitTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("fixture %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The first .txt message in a test archive. */
    private static String firstMessageIn(String zipName) throws IOException {
        URL url = HapiAccessFailureFallbackUnitTest.class.getClassLoader().getResource(zipName);
        assertThat(url).as("archive %s", zipName).isNotNull();
        try (ZipFile zip = new ZipFile(url.getPath())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".txt")) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        throw new IllegalStateException("no .txt message in " + zipName);
    }

    @Test
    @DisplayName("should answer empty or zero when MEDITECH is asked about a request or result it does not have")
    void shouldAnswerEmpty_whenMeditechIndexIsOutOfRange() throws Exception {
        MEDITECHHandler handler = new MEDITECHHandler();
        handler.init(firstMessageIn("MEDITECH_test_data.zip"));

        assertThat(handler.getSpecimenSource(99)).isEmpty();
        assertThat(handler.getSpecimenDescription(99)).isEmpty();
        assertThat(handler.getOBRCommentCount(99)).isZero();
        assertThat(handler.getOBXCommentCount(99, 99)).isZero();
        assertThat(handler.getOBXCommentCount(0, 99)).isZero();
    }

    @Test
    @DisplayName("should fail with a checked HL7Exception from CLSHandler accessors, as callers expect")
    void shouldThrowCheckedHl7Exception_whenClsIndexIsOutOfRange() throws Exception {
        CLSHandler handler = new CLSHandler();
        handler.init(resource("labs/HL7-CLS/MillenniumUpgrade2010_Clinic_Validation_Current.hl7"));

        assertThat(handler.getOBR(0)).isNotNull();
        assertThatThrownBy(() -> handler.getOBR(99)).isInstanceOf(HL7Exception.class);
        assertThatThrownBy(() -> handler.getORC(99)).isInstanceOf(HL7Exception.class);
        assertThatThrownBy(() -> handler.getOBX(99, 0)).isInstanceOf(HL7Exception.class);
        assertThatThrownBy(() -> handler.getOBX(0, 99)).isInstanceOf(HL7Exception.class);
        assertThatThrownBy(() -> handler.getNTE(0, 0, 99)).isInstanceOf(HL7Exception.class);
        assertThatThrownBy(() -> handler.getOBRNTE(0, 99)).isInstanceOf(HL7Exception.class);
    }

    @Test
    @DisplayName("should expose the embedded-document OBX segment through getOBXSegment, or fail with an Exception callers catch")
    void shouldFailWithException_whenOBXSegmentIsOutOfRange() throws Exception {
        MEDITECHHandler handler = new MEDITECHHandler();
        handler.init(firstMessageIn("MEDITECH_test_data.zip"));

        assertThat(handler.getOBXSegment(0, 0)).isNotNull();
        assertThatThrownBy(() -> handler.getOBXSegment(0, 99)).isInstanceOf(Exception.class);
    }

    /**
     * HL7 v2 says trailing blanks in a TX value mean nothing and leading blanks do. HAPI 1.0.1
     * kept the trailing blanks and dropped the leading ones; 2.x does the opposite. Epsilon sends
     * {@code Clinical History: } with a trailing blank, which is now shown without it.
     */
    @Test
    @DisplayName("should show a TX value without its trailing blank, as HAPI 2.x reads it")
    void shouldTrimTrailingBlank_ofTextObservation() throws Exception {
        EpsilonHandler handler = new EpsilonHandler();
        handler.init(resource("labs/HL7-Epsilon/medhealth-pap.hl7"));

        assertThat(handler.getOBXResult(0, 1)).isEqualTo("Clinical History:");
    }
}
