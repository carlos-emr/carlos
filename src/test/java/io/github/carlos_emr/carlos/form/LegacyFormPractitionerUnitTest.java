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

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.sql.ResultSet;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Exercises the provider lookup in the legacy mental-health and lab requisition form records.
 *
 * <p>Each record builds its OHIP practitioner number through
 * {@code PractitionerNumber.ohipRequisition}. A provider without a billing number must leave the
 * field blank rather than producing the unsaveable {@code 0000--00} value, and a selected provider
 * without a number falls back to the patient's provider. JDBC results are mocked; the records'
 * real lookup logic runs unchanged.
 *
 * @since 2026-09-20
 */
@Tag("unit")
class LegacyFormPractitionerUnitTest extends CarlosUnitTestBase {
    private static final String SELECTED_PROVIDER = "100001";
    private static final String PATIENT_PROVIDER = "100002";
    private static final String SELECTED_NAME = "Selected, Provider";
    private static final String PATIENT_NAME = "Patient, Provider";

    @BeforeEach
    void registerBeans() {
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
        registerMock(ClinicDAO.class, mock(ClinicDAO.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 14, 42})
    void shouldLeavePractitionerNumberBlank_whenMentalHealthProviderHasNoBillingNumber(int formNumber) throws Exception {
        Properties props = mentalHealth(formNumber, SELECTED_PROVIDER, SELECTED_PROVIDER,
                provider(SELECTED_NAME, "", ""), null);

        assertThat(props.getProperty("practitionerNo")).isEmpty();
        assertThat(props.getProperty("reqProvName")).isEqualTo(SELECTED_NAME);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 14, 42})
    void shouldFormatPractitionerNumber_whenMentalHealthProviderHasBillingNumber(int formNumber) throws Exception {
        Properties props = mentalHealth(formNumber, SELECTED_PROVIDER, SELECTED_PROVIDER,
                provider(SELECTED_NAME, "123456", ""), null);

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-123456-00");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 14, 42})
    void shouldUsePatientProviderNumber_whenMentalHealthSelectedProviderHasNone(int formNumber) throws Exception {
        Properties props = mentalHealth(formNumber, PATIENT_PROVIDER, SELECTED_PROVIDER,
                provider(SELECTED_NAME, "", ""), provider(PATIENT_NAME, "654321", ""));

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-654321-00");
        assertThat(props.getProperty("reqProvName")).isEqualTo(SELECTED_NAME);
        assertThat(props.getProperty("provName")).isEqualTo(PATIENT_NAME);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 14, 42})
    void shouldKeepSelectedProviderNumber_whenMentalHealthSelectedProviderHasOne(int formNumber) throws Exception {
        Properties props = mentalHealth(formNumber, PATIENT_PROVIDER, SELECTED_PROVIDER,
                provider(SELECTED_NAME, "123456", ""), provider(PATIENT_NAME, "654321", ""));

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-123456-00");
    }

    @Test
    void shouldLeavePractitionerNumberBlank_whenLabRequisitionProviderHasNoBillingNumber() throws Exception {
        Properties props = labRequisition(SELECTED_PROVIDER, SELECTED_PROVIDER,
                provider(SELECTED_NAME, "", ""), null);

        assertThat(props.getProperty("practitionerNo")).isEmpty();
    }

    @Test
    void shouldKeepNumberAndSpecialty_whenLabRequisitionProviderHasBoth() throws Exception {
        Properties props = labRequisition(SELECTED_PROVIDER, SELECTED_PROVIDER,
                provider(SELECTED_NAME, "123456", specialty("07")), null);

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-123456-07");
    }

    @Test
    void shouldUsePatientProviderNumberAndSpecialty_whenLabRequisitionSelectedProviderHasNone() throws Exception {
        Properties props = labRequisition(PATIENT_PROVIDER, SELECTED_PROVIDER,
                provider(SELECTED_NAME, "", specialty("03")), provider(PATIENT_NAME, "654321", specialty("08")));

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-654321-08");
        assertThat(props.getProperty("reqProvName")).isEqualTo(SELECTED_NAME);
        assertThat(props.getProperty("provName")).isEqualTo(PATIENT_NAME);
    }

    @Test
    void shouldKeepSelectedProviderNumber_whenLabRequisitionSelectedProviderHasOne() throws Exception {
        Properties props = labRequisition(PATIENT_PROVIDER, SELECTED_PROVIDER,
                provider(SELECTED_NAME, "123456", specialty("03")), provider(PATIENT_NAME, "654321", specialty("08")));

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-123456-03");
    }

    private Properties mentalHealth(int formNumber, String patientProvider, String selectedProvider,
            ResultSet selected, ResultSet fallback) throws Exception {
        Properties props = new Properties();
        props.setProperty("demoProvider", patientProvider);
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(contains("FROM provider"), any(String.class)))
                    .thenAnswer(invocation -> patientProvider.equals(invocation.getArgument(1)) && fallback != null
                            ? fallback : selected);
            return switch (formNumber) {
                case 1 -> new FrmMentalHealthForm1Record().getFormCustRecord(props, selectedProvider);
                case 14 -> new FrmMentalHealthForm14Record().getFormCustRecord(props, selectedProvider);
                case 42 -> new FrmMentalHealthForm42Record().getFormCustRecord(props, selectedProvider);
                default -> throw new IllegalArgumentException("Unknown form " + formNumber);
            };
        }
    }

    private Properties labRequisition(String patientProvider, String selectedProvider,
            ResultSet selected, ResultSet fallback) throws Exception {
        Properties props = new Properties();
        props.setProperty("demoProvider", patientProvider);
        try (MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class)) {
            // FrmLabReqRecord binds the selected provider as an int and the patient's provider as a String.
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(contains("FROM provider"), any(Object.class)))
                    .thenAnswer(invocation -> patientProvider.equals(String.valueOf((Object) invocation.getArgument(1)))
                            && fallback != null ? fallback : selected);
            return new FrmLabReqRecord().getFormCustRecord(props, Integer.parseInt(selectedProvider));
        }
    }

    private static String specialty(String code) {
        return "<xml_p_specialty_code>" + code + "</xml_p_specialty_code>";
    }

    private static ResultSet provider(String name, String billingNumber, String comments) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true);
        when(result.getString("provName")).thenReturn(name);
        when(result.getString("ohip_no")).thenReturn(billingNumber);
        when(result.getString("comments")).thenReturn(comments);
        return result;
    }
}
