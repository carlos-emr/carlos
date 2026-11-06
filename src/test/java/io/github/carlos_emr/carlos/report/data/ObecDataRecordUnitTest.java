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
package io.github.carlos_emr.carlos.report.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The records of the Overnight Batch Eligibility Checking file (#4967): OBEC01, the health number padded to 10 and the
 * version code padded to 2, a missing version (NULL or empty) padded as blank.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("ObecData OBEC records")
class ObecDataRecordUnitTest extends CarlosUnitTestBase {

    @TempDir
    Path documentDir;

    private OscarAppointmentDao appointmentDao;

    @BeforeEach
    void registerAppointmentDao() {
        appointmentDao = createAndRegisterMock(OscarAppointmentDao.class);
    }

    private static Object[] booked(String hin, String ver) {
        Demographic patient = new Demographic();
        patient.setHin(hin);
        patient.setVer(ver);
        return new Object[] {new Appointment(), patient};
    }

    private String generate(List<Object[]> booked) throws Exception {
        when(appointmentDao.findAppointments(any(), any())).thenReturn(booked);
        Properties pp = new Properties();
        pp.setProperty("DOCUMENT_DIR", documentDir.toString());
        String name = new ObecData().generateOBEC("2070-06-18", "2070-06-21", pp);
        assertThat(name).startsWith("OBECE").endsWith(".TXT");
        return Files.readString(documentDir.resolve(name), StandardCharsets.ISO_8859_1);
    }

    @ParameterizedTest(name = "version {0}")
    @NullAndEmptySource
    @DisplayName("should list a patient without a version code with a blank version")
    void shouldListBlankVersion_whenVersionCodeMissing(String ver) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        rows.add(booked("9876543210", ver));

        assertThat(generate(rows)).isEqualTo("OBEC01" + "9876543210" + "  " + "\r");
    }

    @Test
    @DisplayName("should list every patient when one of them has no version code stored")
    void shouldListEveryPatient_whenOneVersionCodeIsNull() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        rows.add(booked("9876543210", "AB"));
        rows.add(booked("9123456789", null));

        assertThat(generate(rows)).isEqualTo("OBEC01" + "9876543210" + "AB" + "\r"
                + "\n" + "OBEC01" + "9123456789" + "  " + "\r");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @DisplayName("should pad a missing value as blank to the field width")
    void shouldPadBlank_whenValueMissing(String value) {
        assertThat(ObecData.space(value, 2)).isEqualTo("  ");
    }
}
