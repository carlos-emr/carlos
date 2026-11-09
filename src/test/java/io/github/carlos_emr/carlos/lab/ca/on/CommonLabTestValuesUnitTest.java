/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Hashtable;
import java.io.Serializable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("lab")
class CommonLabTestValuesUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldDisplaySourceCaseAndExcludedLiteral_withoutChangingMeasurements() {
        Hashtable<String, Serializable> old = new Hashtable<>();
        old.put("lab_no", "100"); old.put("result", "negative"); old.put("date", "2026-01-02 09:00:00");
        List<Map<String, Serializable>> raw = List.of(
                Map.of("lab_no", "100", "result", "Negative", "collDate", "2026-01-02 09:00:00", "identifier", "3007"),
                Map.of("lab_no", "200", "result", "Pending", "collDate", "2026-01-03 09:00:00", "identifier", "3007"),
                Map.of("lab_no", "300", "result", "999", "collDate", "2026-01-04 09:00:00", "identifier", "unrelated"));
        var rows = CommonLabTestValues.overlaySourceValues(List.of(old), raw, Set.of("3007"));
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsEntry("lab_no", "200").containsEntry("result", "Pending")
                .containsEntry("sourceOnly", true);
        assertThat(rows.getLast()).containsEntry("result", "Negative").containsEntry("sourceOnly", false);
        assertThat(old.get("result")).isEqualTo("negative");
        assertThat(mockedBeans).doesNotContainKey(MeasurementDao.class);
    }

    @Test
    void shouldRetainEverySourceLiteral_whenMappedObservationRepeatsInOneReport() {
        List<Map<String, Serializable>> raw = List.of(
                Map.of("lab_no", "100", "result", "Negative", "collDate", "2026-01-02 09:00:00", "identifier", "3007", "abn", "N"),
                Map.of("lab_no", "100", "result", "Pending", "collDate", "2026-01-02 10:00:00", "identifier", "3007", "abn", "HH"));
        var rows = CommonLabTestValues.overlaySourceValues(List.of(), raw, Set.of("3007"));
        assertThat(rows).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("result", "Negative; Pending").containsEntry("multipleSourceResults", true).containsEntry("abn", "A"));
    }

    @Test
    void shouldRetainProjectedValuesAndAbnormalColours_whenSourceDisplayDiffers() {
        Hashtable<String, Serializable> old = new Hashtable<>();
        old.put("lab_no", "100"); old.put("result", "6.7"); old.put("date", "2026-01-02 09:00:00"); old.put("abn", "A");
        List<Map<String, Serializable>> raw = List.of(
                Map.of("lab_no", "100", "result", "5.1", "collDate", "2026-01-02 09:00:00", "identifier", "3007", "abn", "N"),
                Map.of("lab_no", "200", "result", "Pending", "collDate", "2026-01-03 09:00:00", "identifier", "3007", "abn", "HH"));
        var rows = CommonLabTestValues.overlaySourceValues(List.of(old), raw, Set.of("3007"));
        assertThat(rows.getFirst()).containsEntry("result", "Pending").containsEntry("abn", "A");
        assertThat(rows.getLast()).containsEntry("result", "6.7").containsEntry("abn", "A")
                .containsEntry("differentSourceResult", true);
    }

    @Test
    void shouldDiscoverHistoryWithoutMeasurementReferences_whenFinalSourceLiteralWasNotProjected() {
        try (var source = mockStatic(Hl7SourceResults.class)) {
            source.when(() -> Hl7SourceResults.read(17)).thenReturn(List.of(
                    Map.of("lab_no", "100", "result", "Pending", "collDate", "2026-01-02 09:00:00", "identifier", "3007"),
                    Map.of("lab_no", "101", "result", "Negative", "collDate", "2026-01-03 09:00:00", "identifier", "3007"),
                    Map.of("lab_no", "102", "result", "999", "collDate", "2026-01-04 09:00:00", "identifier", "other")));
            var rows = CommonLabTestValues.findValuesForTest("HL7", 17, "GLUCOSE", "3007");
            assertThat(rows).extracting(row -> row.get("result")).containsExactly("Negative", "Pending");
            assertThat(mockedBeans).doesNotContainKey(MeasurementDao.class);
        }
    }
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
