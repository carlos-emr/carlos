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
 * Migrated from legacy JUnit 4 CLSHandlerTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.labs.alberta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.CLSHandler;

/**
 * Unit tests for {@link CLSHandler} basic functionality.
 *
 * <p>Tests HL7 message loading and basic parsing of Alberta CLS lab results,
 * including patient location, health number, and home phone extraction.
 * Migrated from legacy JUnit 4 CLSHandlerTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("lab")
@DisplayName("CLSHandler basic unit tests")
class CLSHandlerUnitTest {

    @Test
    @DisplayName("should parse CLS HL7 message and extract basic patient information")
    void shouldParseCLSMessage_andExtractBasicPatientInfo() throws Exception {
        CLSHandler h = new CLSHandler();

        try (InputStream is = getClass().getResourceAsStream(
                "/labs/HL7-CLS/MillenniumUpgrade2010_Clinic_Validation_Current.hl7")) {
            assertThat(is).as("CLS HL7 test file should exist on classpath").isNotNull();

            byte[] bytes = IOUtils.toByteArray(is);
            h.init(new String(bytes));

            assertThat(h.getPatientLocation()).isNotNull();
            assertThat(h.getHealthNum()).isNotNull();
            assertThat(h.getHomePhone()).isNotNull();
        }
    }
}
