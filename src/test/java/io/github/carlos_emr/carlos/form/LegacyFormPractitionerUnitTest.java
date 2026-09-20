// SPDX-License-Identifier: GPL-2.0-or-later
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

/** Exercises each form's provider lookup, including the fallback to the patient's provider. */
@Tag("unit")
class LegacyFormPractitionerUnitTest extends CarlosUnitTestBase {
    private static final String SELECTED_PROVIDER = "100001";
    private static final String PATIENT_PROVIDER = "100002";

    @BeforeEach
    void registerBeans() {
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
        registerMock(ClinicDAO.class, mock(ClinicDAO.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 14, 42})
    void mentalHealthFormLeavesMissingBillingNumberBlank(int formNumber) throws Exception {
        Properties props = mentalHealth(formNumber, SELECTED_PROVIDER, SELECTED_PROVIDER,
                provider("", "", true), null);

        assertThat(props.getProperty("practitionerNo")).isEmpty();
        assertThat(props.getProperty("reqProvName")).isEqualTo("Selected Provider");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 14, 42})
    void mentalHealthFormFormatsValidBillingNumber(int formNumber) throws Exception {
        Properties props = mentalHealth(formNumber, SELECTED_PROVIDER, SELECTED_PROVIDER,
                provider("123456", "", true), null);

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-123456-00");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 14, 42})
    void mentalHealthFormFallsBackWhenSelectedProviderHasNoBillingNumber(int formNumber) throws Exception {
        Properties props = mentalHealth(formNumber, PATIENT_PROVIDER, SELECTED_PROVIDER,
                provider("", "", true), provider("654321", "", true));

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-654321-00");
        assertThat(props.getProperty("reqProvName")).isEqualTo("Selected Provider");
    }

    @Test
    void labRequisitionLeavesMissingBillingNumberBlank() throws Exception {
        Properties props = labRequisition(SELECTED_PROVIDER, SELECTED_PROVIDER,
                provider("", "", true), null);

        assertThat(props.getProperty("practitionerNo")).isEmpty();
    }

    @Test
    void labRequisitionKeepsValidNumberAndSpecialty() throws Exception {
        Properties props = labRequisition(SELECTED_PROVIDER, SELECTED_PROVIDER,
                provider("123456", "<xml_p_specialty_code>07</xml_p_specialty_code>", true), null);

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-123456-07");
    }

    @Test
    void labRequisitionFallsBackToPatientsProviderNumber() throws Exception {
        Properties props = labRequisition(PATIENT_PROVIDER, SELECTED_PROVIDER,
                provider("", "", true), provider("654321", "", true));

        assertThat(props.getProperty("practitionerNo")).isEqualTo("0000-654321-00");
        assertThat(props.getProperty("reqProvName")).isEqualTo("Selected Provider");
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
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(contains("FROM provider"), any(Object.class)))
                    .thenAnswer(invocation -> patientProvider.equals(String.valueOf((Object) invocation.getArgument(1)))
                            && fallback != null ? fallback : selected);
            return new FrmLabReqRecord().getFormCustRecord(props, Integer.parseInt(selectedProvider));
        }
    }

    private static ResultSet provider(String billingNumber, String comments, boolean present) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(present);
        when(result.getString("provName")).thenReturn("Selected Provider");
        when(result.getString("ohip_no")).thenReturn(billingNumber);
        when(result.getString("comments")).thenReturn(comments);
        return result;
    }
}
