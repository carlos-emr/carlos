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
}
