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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.Properties;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.login.DBHelp;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * BC-AR 2007, Position Hazard and Health Passport fill their forms from the chart through
 * {@code java.util.Properties}, which refuses null. A chart with no address, city, postal code, health number or
 * version (or a blank province, which {@code Demographic.setProvince} stores as null) made these forms answer
 * HTTP 500 (issue #4953). Missing fields are now filled in as "".
 *
 * @since 2026-10-18
 */
@DisplayName("Form prefill with missing chart fields")
@Tag("unit")
@Tag("form")
class FormRecordMissingChartFieldsUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 42;
    private static final String PROVIDER = "999990";

    @BeforeEach
    void registerBeans() {
        // Names and date of birth are set, as every chart has them; every other field the forms read is NULL.
        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setLastName("FAKE-Surname");
        patient.setFirstName("FAKE-Given");
        patient.setYearOfBirth("1985");
        patient.setMonthOfBirth("03");
        patient.setDateOfBirth("04");
        patient.setProvince("");
        DemographicManager demographicManager = mock(DemographicManager.class);
        when(demographicManager.getDemographic(any(), eq(PATIENT))).thenReturn(patient);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
        ClinicDAO clinicDao = mock(ClinicDAO.class);
        when(clinicDao.getClinic()).thenReturn(new Clinic());
        registerMock(ClinicDAO.class, clinicDao);
        // The patient's provider has no OHIP/MSP number on file.
        ProviderData provider = new ProviderData();
        provider.setLastName("FAKE-Provider");
        ProviderDataDao providerDataDao = mock(ProviderDataDao.class);
        when(providerDataDao.findByProviderNo(PROVIDER)).thenReturn(provider);
        registerMock(ProviderDataDao.class, providerDataDao);
    }

    private static MockedConstruction<FrmRecordHelp> savedFormRows() {
        return mockConstruction(FrmRecordHelp.class,
                (help, context) -> when(help.getFormRecord(anyString(), any(Object[].class))).thenReturn(new Properties()));
    }

    /** A demographic row whose health number and phones are NULL. */
    private static ResultSet chartRow() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.next()).thenReturn(true, false);
        when(row.getString("demographic_no")).thenReturn(String.valueOf(PATIENT));
        when(row.getString("last_name")).thenReturn("FAKE-Surname");
        when(row.getString("first_name")).thenReturn("FAKE-Given");
        return row;
    }

    @Test
    void shouldFillANewBcAr2007Form_whenTheChartFieldsAreMissing() throws Exception {
        Properties props = new FrmBCAR2007Record().getFormRecord(null, PATIENT, 0);

        assertThat(props.getProperty("c_surname")).isEqualTo("FAKE-Surname");
        for (String field : new String[] {"c_address", "c_city", "c_province", "c_postal", "c_phn"}) {
            assertThat(props.getProperty(field)).as(field).isEmpty();
        }
    }

    @Test
    void shouldReopenASavedBcAr2007Form_whenTheChartFieldsAreMissing() throws Exception {
        try (MockedConstruction<FrmRecordHelp> rows = savedFormRows()) {
            Properties props = new FrmBCAR2007Record().getFormRecord(null, PATIENT, 7);

            assertThat(props.getProperty("c_givenName_cur")).isEqualTo("FAKE-Given");
            for (String field : new String[] {"c_address_cur", "c_city_cur", "c_province_cur", "c_postal_cur", "c_phn_cur"}) {
                assertThat(props.getProperty(field)).as(field).isEmpty();
            }
        }
    }

    @Test
    void shouldFillANewPositionHazardForm_whenTheChartAndClinicFieldsAreMissing() throws Exception {
        Properties props = new FrmPositionHazardRecord().getFormRecord(null, PATIENT, 0);

        for (String field : new String[] {"healthNumber", "version", "hcType", "patientAddress", "patientCity", "patientPC",
                "province", "sex", "demoProvider", "clinicName", "clinicProvince", "clinicAddress", "clinicCity", "clinicPC"}) {
            assertThat(props.getProperty(field)).as(field).isEmpty();
        }
    }

    @Test
    void shouldFillANewHealthPassport_whenTheHealthNumberAndPhonesAreMissing() throws Exception {
        ResultSet row = chartRow();
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(anyString(), any(Object[].class))).thenReturn(row);

            Properties props = new FrmBCHPRecord().getFormRecord(null, PATIENT, 0);

            assertThat(props.getProperty("pg1_phn")).isEmpty();
            assertThat(props.getProperty("pg1_phone")).as("no \"null\" text").doesNotContain("null");
        }
    }

    @Test
    void shouldFillANewHealthPassport_whenThePatientsProviderHasNoMspNumber() throws Exception {
        ResultSet row = chartRow();
        when(row.getString("provider_no")).thenReturn(PROVIDER);
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(anyString(), any(Object[].class))).thenReturn(row);

            Properties props = new FrmBCHPRecord().getFormRecord(null, PATIENT, 0);

            assertThat(props.getProperty("pg1_md")).isEqualTo("Dr. FAKE-Provider");
            assertThat(props.getProperty("pg1_msp")).isEmpty();
        }
    }

    @Test
    void shouldReopenASavedHealthPassport_whenTheHealthNumberAndPhonesAreMissing() throws Exception {
        ResultSet row = chartRow();
        try (MockedConstruction<FrmRecordHelp> rows = savedFormRows();
             MockedStatic<DBHelp> db = mockStatic(DBHelp.class)) {
            db.when(() -> DBHelp.searchDBRecord(anyString(), any(Object[].class))).thenReturn(row);

            Properties props = new FrmBCHPRecord().getFormRecord(null, PATIENT, 7);

            assertThat(props.getProperty("pg1_phn_cur")).isEmpty();
            assertThat(props.getProperty("pg1_phone_cur")).as("no \"null\" text").doesNotContain("null");
        }
    }

    @Test
    void shouldCopyAMissingSexAsEmpty_inTheMeasurementFormSave() throws Exception {
        // The save copies the chart's sex into the form's properties; the action needs a full request to run.
        String action = Files.readString(Path.of("src/main/java/io/github/carlos_emr/carlos/form/pageUtil/FrmForm2Action.java"),
                StandardCharsets.UTF_8);
        assertThat(action).contains("props.setProperty(\"gender\", StringUtils.defaultString(demo.getSex()));");
    }
}
