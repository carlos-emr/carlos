// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BCARRecordUnitTest extends CarlosUnitTestBase {
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    @BeforeEach void setUp() {
        DemographicManager manager = createAndRegisterMock(DemographicManager.class);
        DemographicExtDao extensions = createAndRegisterMock(DemographicExtDao.class);
        Demographic patient = new Demographic(); patient.setDemographicNo(770001);
        patient.setFirstName("Synthetic"); patient.setLastName("Coverage");
        patient.setYearOfBirth("1995"); patient.setMonthOfBirth("01"); patient.setDateOfBirth("23");
        patient.setAddress("1 Fixture Lane"); patient.setCity("Fixture City"); patient.setProvince("BC");
        patient.setPostal("V0V0V0"); patient.setHin("0000000000"); patient.setPhone("2505550101"); patient.setPhone2("2505550102");
        patient.setFamilyDoctor("");
        when(manager.getDemographic(info, 770001)).thenReturn(patient);
        when(extensions.getAllValuesForDemo(770001)).thenReturn(Map.of("demo_cell", "2505550103"));
    }
    private FrmRecord record(int year) { return year == 2007 ? new FrmBCAR2007Record() : new FrmBCAR2012Record(); }
    @ParameterizedTest @ValueSource(ints = {2007, 2012})
    void shouldPopulateNewForm_fromSelectedPatient(int year) throws Exception {
        Properties form = record(year).getFormRecord(info, 770001, 0);
        assertThat(form.getProperty("demographic_no")).isEqualTo("770001");
        assertThat(form.getProperty("c_surname")).isEqualTo("Coverage");
        assertThat(form.getProperty("c_givenName")).isEqualTo("Synthetic");
        assertThat(form.getProperty("c_address")).isEqualTo("1 Fixture Lane");
        assertThat(form.getProperty("c_phoneAlt2")).isEqualTo("2505550103");
        assertThat(form.getProperty("pg1_dateOfBirth")).isEqualTo("23/01/1995");
        assertThat(form.getProperty("pg1_famPhy")).isEmpty();
    }
    @ParameterizedTest @ValueSource(ints = {2007, 2012})
    void shouldLoadExistingForm_withPatientAndRecordScope(int year) throws Exception {
        Properties stored = new Properties(); stored.setProperty("fixture", "retained");
        try (MockedConstruction<FrmRecordHelp> helpers = mockConstruction(FrmRecordHelp.class,
                (mock, context) -> when(mock.getFormRecord("SELECT * FROM formBCAR" + year + " WHERE demographic_no = ? AND ID = ?", 770001, 77)).thenReturn(stored))) {
            assertThat(record(year).getFormRecord(info, 770001, 77).getProperty("fixture")).isEqualTo("retained");
            verify(helpers.constructed().get(0)).getFormRecord("SELECT * FROM formBCAR" + year + " WHERE demographic_no = ? AND ID = ?", 770001, 77);
        }
    }
    @ParameterizedTest @ValueSource(ints = {2007, 2012})
    void shouldSaveNewVersion_withBoundPatientIdentifier(int year) throws Exception {
        Properties form = new Properties(); form.setProperty("demographic_no", "770001");
        try (MockedConstruction<FrmRecordHelp> helpers = mockConstruction(FrmRecordHelp.class,
                (mock, context) -> when(mock.saveFormRecord(form, "SELECT * FROM formBCAR" + year + " WHERE demographic_no=? AND ID=0", "770001")).thenReturn(78))) {
            assertThat(record(year).saveFormRecord(form)).isEqualTo(78);
            verify(helpers.constructed().get(0)).saveFormRecord(form, "SELECT * FROM formBCAR" + year + " WHERE demographic_no=? AND ID=0", "770001");
        }
    }
}
