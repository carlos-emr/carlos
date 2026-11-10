/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("lab")
class CommonLabTestValuesUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldPreserveSourceIdentityOrderAndUnits_whenMeasurementsHaveRepeatedValues() {
        var dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(17, "FAKE-code"))
                .thenReturn(List.of(new Object[]{"1.2 café", "2026-01-02", 100, "N", "mmol/L"},
                                   new Object[]{"1.2 café", "2026-01-02", 200, "N", "mmol/L"}));
        var rows = CommonLabTestValues.findValuesByLoinc2("17", "FAKE-code");
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsEntry("lab_no", "100").containsEntry("result", "1.2 café")
                .containsEntry("units", "mmol/L").containsEntry("date", "2026-01-02");
        assertThat(rows.getLast()).containsEntry("lab_no", "200");
        verify(dao, times(1)).findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(17, "FAKE-code");
    }

    @Test
    void shouldReturnNoInventedResult_whenNoMeasurementMatches() {
        var dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(17, "FAKE-missing")).thenReturn(List.of());
        assertThat(CommonLabTestValues.findValuesByLoinc2("17", "FAKE-missing")).isEmpty();
    }
    @Test
    void shouldKeepMissingUnitsAndUndatedCmlResults_visibleWithoutFabricatingDates() {
        var dao = createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao.class);
        var routing = new io.github.carlos_emr.carlos.commn.model.PatientLabRouting(); routing.setLabNo(123);
        var test = new io.github.carlos_emr.carlos.commn.model.LabTestResults();
        test.setTestName("CREATININE"); test.setResult("80"); test.setUnits(null);
        var dated = new io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo();
        dated.setAccessionNum("same-accession"); dated.setCollectionDate("01-Jan-26");
        var undated = new io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo();
        undated.setAccessionNum("same-accession"); undated.setCollectionDate(null);
        when(dao.findRoutingsAndTests(17, "CML", "CREATININE")).thenReturn(List.of(
                new Object[]{routing, test, undated}, new Object[]{routing, test, dated}));
        var rows = CommonLabTestValues.findValuesForTest("CML", 17, "CREATININE");
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().get("collDateDate")).isNull();
        assertThat(rows.getFirst().get("units")).isNull();
        assertThat(rows.getFirst().get("result")).isEqualTo("80");
        assertThat(rows.getLast().get("collDateDate")).isInstanceOf(java.util.Date.class);
    }

}
