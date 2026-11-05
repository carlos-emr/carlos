/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("measurement")
class MeasurementChartDataUnitTest {
    @Test
    @SuppressWarnings("unchecked")
    void shouldReplaceDailyReadings_andKeepSecondaryAxisUnits() {
        var series = new MeasurementChart.Series("Weight");
        var day = new MeasurementChart.Day(new Date(1_700_000_000_000L));
        series.addOrUpdate(day, 61);
        series.addOrUpdate(day, 63);
        var collection = new MeasurementChart.SeriesCollection();
        collection.addSeries(series);
        var chart = MeasurementChart.lines("Synthetic", "Days", "kg", collection)
                .withSecondAxis("Secondary units", collection)
                .withReferenceRanges(List.of(new MeasurementChart.ReferenceRange(day.getStart(), 55, 70)));
        var datasets = (List<Map<String, Object>>) chart.toConfig().get("datasets");
        var primary = (List<Map<String, Object>>) datasets.get(0).get("data");
        assertThat(primary).hasSize(1);
        assertThat(primary.getFirst().get("y")).isEqualTo(63.0);
        assertThat(datasets.get(1).get("yAxisID")).isEqualTo("y2");
        assertThat(chart.toConfig().get("secondAxis")).isEqualTo("Secondary units");
        var bounds = (List<Map<String, Object>>) datasets.get(2).get("data");
        assertThat(bounds).extracting(value -> value.get("y")).containsExactly(55.0, 70.0);
    }

    @Test
    void shouldKeepAnOpenMedicationInterval_whenBrandAndEndDateAreMissing() {
        var prescription = mock(RxPrescriptionData.Prescription.class);
        when(prescription.getRxDate()).thenReturn(new Date(1_700_000_000_000L));
        try (var construction = mockConstruction(RxPrescriptionData.class, (instance, context) ->
                when(instance.getPrescriptionScriptsByPatientRegionalIdentifier(101, "synthetic"))
                        .thenReturn(new RxPrescriptionData.Prescription[]{prescription}))) {
            long before = System.currentTimeMillis();
            var intervals = MeasurementGraphAction22Action.getDrugDataSet(101, new String[]{"synthetic"});
            assertThat(intervals).hasSize(1);
            assertThat(intervals.getFirst()).containsEntry("name", "Medication").containsEntry("start", 1_700_000_000_000L);
            assertThat((Long) intervals.getFirst().get("end")).isBetween(before, System.currentTimeMillis());
        }
    }

    /** V8's Intl accepts IANA regions and "+HH:MM" offsets, not Java's "GMT+03:00" or "Z". */
    @Test
    void shouldPassBrowserTimeZone_asIanaRegionOrOffset() {
        assertThat(MeasurementChart.browserTimeZone(java.time.ZoneId.of("America/Toronto"))).isEqualTo("America/Toronto");
        assertThat(MeasurementChart.browserTimeZone(java.time.ZoneId.of("GMT+03:00"))).isEqualTo("+03:00");
        assertThat(MeasurementChart.browserTimeZone(java.time.ZoneId.of("GMT-5"))).isEqualTo("-05:00");
        assertThat(MeasurementChart.browserTimeZone(java.time.ZoneId.of("GMT"))).isEqualTo("UTC");
        assertThat(MeasurementChart.browserTimeZone(java.time.ZoneId.of("Etc/UTC"))).isEqualTo("UTC");
    }
}
