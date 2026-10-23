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
package io.github.carlos_emr.carlos.form;

import java.util.List;
import java.util.Properties;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020Dao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020DataDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020TextDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BC-AR 2020 fills a new form from the chart, and refreshes the health number on a saved one, through
 * {@code java.util.Properties}, which refuses null. A chart whose health number, version or address fields are NULL
 * (an imported patient), or whose province is blank ({@code Demographic.setProvince} turns "" into null), made the
 * form answer HTTP 500 (issue #4920).
 *
 * @since 2026-10-18
 */
@DisplayName("BC-AR 2020 prefill with empty chart fields")
@Tag("unit")
@Tag("form")
class FrmBCAR2020RecordNullFieldsUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 42;

    private Demographic patient;

    @BeforeEach
    void registerBeans() {
        // Names and date of birth are set, as every chart has them; every other field the prefill reads is NULL.
        patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setLastName("FAKE-Surname");
        patient.setFirstName("FAKE-Given");
        patient.setYearOfBirth("1985");
        patient.setMonthOfBirth("03");
        patient.setDateOfBirth("04");
        DemographicManager demographicManager = mock(DemographicManager.class);
        when(demographicManager.getDemographic(any(), eq(PATIENT))).thenReturn(patient);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
        FormBCAR2020DataDao dataDao = mock(FormBCAR2020DataDao.class);
        when(dataDao.findFieldsForPage(anyInt(), anyInt())).thenReturn(List.of());
        registerMock(FormBCAR2020DataDao.class, dataDao);
        FormBCAR2020TextDao textDao = mock(FormBCAR2020TextDao.class);
        when(textDao.findFieldsForPage(anyInt(), anyInt())).thenReturn(List.of());
        registerMock(FormBCAR2020TextDao.class, textDao);
        // The saved form the reopen case loads (form 7) is the patient's own, as a saved form's row says.
        FormBCAR2020 saved = new FormBCAR2020();
        saved.setDemographicNo(PATIENT);
        FormBCAR2020Dao formDao = mock(FormBCAR2020Dao.class);
        when(formDao.find(7)).thenReturn(saved);
        registerMock(FormBCAR2020Dao.class, formDao);
    }

    @Test
    void shouldFillANewForm_whenTheChartFieldsAreNull() throws Exception {
        Properties props = new FrmBCAR2020Record().getFormRecord(null, PATIENT, 0, 1);

        assertThat(props.getProperty("t_patientSurname")).isEqualTo("FAKE-Surname");
        for (String field : new String[] {"t_patientHIN", "t_hinVer", "t_patientAddress", "t_patientCity", "t_patientProvince",
                "t_patientPostal", "t_patientPhone", "t_patientPhoneWork", "t_patientPreferredName"}) {
            assertThat(props.getProperty(field)).as(field).isEmpty();
        }
    }

    @Test
    void shouldFillANewForm_whenTheProvinceIsBlank() throws Exception {
        // Blank fields as the master record stores them; the province setter turns "" into null.
        patient.setHin("");
        patient.setVer("");
        patient.setAddress("");
        patient.setCity("");
        patient.setProvince("");
        patient.setPostal("");
        patient.setPrefName("");

        Properties props = new FrmBCAR2020Record().getFormRecord(null, PATIENT, 0, 1);

        assertThat(props.getProperty("t_patientProvince")).isEmpty();
        assertThat(props.getProperty("t_patientPreferredName")).as("no \" / \" when there is neither").isEmpty();
    }

    @Test
    void shouldReopenASavedForm_whenTheHealthNumberIsNull() throws Exception {
        Properties props = new FrmBCAR2020Record().getFormRecord(null, PATIENT, 7, 2);

        assertThat(props.getProperty("t_patientHIN")).isEmpty();
        assertThat(props.getProperty("t_patientGivenName")).isEqualTo("FAKE-Given");
    }

    @ParameterizedTest
    @CsvSource(value = {"Sam,they/them,Sam / they/them", "Sam,'',Sam", "'',they/them,they/them", "'','',''", "NULL,NULL,''", "'  ',NULL,''"},
            nullValues = "NULL")
    void shouldLeaveOutAMissingPart_ofThePreferredName(String prefName, String pronoun, String expected) {
        assertThat(FrmBCAR2020Record.preferredName(prefName, pronoun)).isEqualTo(expected);
    }
}
