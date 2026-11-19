/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import java.time.Instant;
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2024-05-10 14:30:00.1234", "2024-05-10 14:30:00.123456789",
            "2024-03-10 02:30:00", "2024-05-10", "2024-05-10 14", "2024-05-10 14:30"})
    void shouldResolveLocalCollectionTimes_withFractionsAndDaylightSavingGaps(String source) throws Exception {
        var previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Toronto"));
            var method = MeasurementGraphAction22Action.class.getDeclaredMethod("collectionDay", Map.class, String.class);
            method.setAccessible(true);
            var actual = (MeasurementChart.Day) method.invoke(null, Map.of("collDate", source), "FAKE test");
            String date = source.substring(0, 10);
            assertThat(actual.getStart()).isEqualTo(new MeasurementChart.Day(java.time.LocalDate.parse(date)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()).getStart());
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "2024-03-10 02:30:00-05:00,2024-03-10T07:30:00Z",
            "2024-03-10 23:30:00-05:00,2024-03-11T04:30:00Z",
            "2024-03-10 00:30:00+05:30,2024-03-09T19:00:00Z",
            "2024-03-10 02:30-05:00,2024-03-10T07:30:00Z",
            "2024-03-10 02:30:00.123456789-05:00,2024-03-10T07:30:00.123456789Z"})
    void shouldPreserveTimestampInstant_whenCdsCollectionDateHasOffset(String source, String instant) throws Exception {
        var method = MeasurementGraphAction22Action.class.getDeclaredMethod("collectionDay", Map.class, String.class);
        method.setAccessible(true);
        var day = (MeasurementChart.Day) method.invoke(null, Map.of("collDate", source), "FAKE test");
        assertThat(day.getStart()).isEqualTo(new MeasurementChart.Day(Instant.parse(instant)).getStart());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2024-02-30 02:30:00-05:00", "2024-03-10 02:30:00+25:00", "2024-03-10 02:30:00-05:00 extra"})
    void shouldRejectInvalidCollectionTimestamp_withoutDiscardingOffsetOrTrailingText(String source) throws Exception {
        var method = MeasurementGraphAction22Action.class.getDeclaredMethod("collectionDay", Map.class, String.class);
        method.setAccessible(true);
        // #5853: an unreadable date names the test it belongs to, so the graph window can say which.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> method.invoke(null, Map.of("collDate", source), "FAKE test"))
                .cause().isInstanceOfSatisfying(GraphDataException.class, problem -> {
                    org.assertj.core.api.Assertions.assertThat(problem.problem())
                            .isEqualTo(GraphDataException.Problem.UNREADABLE_DATE);
                    org.assertj.core.api.Assertions.assertThat(problem.subject()).isEqualTo("FAKE test");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReplaceDailyReadings_andKeepSecondaryAxisUnits() {
        var series = new MeasurementChart.Series("Weight");
        var day = new MeasurementChart.Day(Instant.ofEpochMilli(1_700_000_000_000L));
        series.addOrUpdate(day, 61);
        series.addOrUpdate(day, 63);
        var collection = new MeasurementChart.SeriesCollection();
        collection.addSeries(series);
        var chart = MeasurementChart.lines("Synthetic", "Days", "kg", collection)
                .withSecondAxis("Secondary units", collection)
                .withReferenceRanges("Normal Reference Range", List.of(new MeasurementChart.ReferenceRange(day.getStart(), 55, 70)));
        var datasets = (List<Map<String, Object>>) chart.toConfig().get("datasets");
        var primary = (List<Map<String, Object>>) datasets.get(0).get("data");
        assertThat(primary).hasSize(1);
        assertThat(primary.getFirst()).containsEntry("y", 63.0);
        assertThat(datasets.get(1)).containsEntry("yAxisID", "y2");
        assertThat(chart.toConfig()).containsEntry("secondAxis", "Secondary units");
        var bounds = (List<Map<String, Object>>) datasets.get(2).get("data");
        assertThat(bounds).singleElement().satisfies(range -> assertThat(range).containsEntry("low", 55.0).containsEntry("high", 70.0));
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
            assertThat(construction.constructed()).hasSize(1);
            assertThat(intervals).hasSize(1);
            assertThat(intervals.getFirst()).containsEntry("name", "Medication").containsEntry("start", 1_700_000_000_000L);
            assertThat((Long) intervals.getFirst().get("end")).isBetween(before, System.currentTimeMillis());
        }
    }

    /**
     * Each requested DIN was its own TaskSeries named by its first prescription: a renamed brand stays on
     * its DIN's row, and two DINs sharing a brand keep separate rows.
     */
    @Test
    void shouldKeepOneRowPerDin_namedByItsFirstPrescription() {
        var first = mock(RxPrescriptionData.Prescription.class);
        when(first.getRxDate()).thenReturn(new Date(1_700_000_000_000L));
        when(first.getEndDate()).thenReturn(new Date(1_700_086_400_000L));
        when(first.getBrandName()).thenReturn("SYNTHETIC BRAND");
        var renamed = mock(RxPrescriptionData.Prescription.class);
        when(renamed.getRxDate()).thenReturn(new Date(1_700_172_800_000L));
        when(renamed.getEndDate()).thenReturn(new Date(1_700_259_200_000L));
        when(renamed.getBrandName()).thenReturn("SYNTHETIC BRAND RENAMED");
        var other = mock(RxPrescriptionData.Prescription.class);
        when(other.getRxDate()).thenReturn(new Date(1_700_000_000_000L));
        when(other.getEndDate()).thenReturn(new Date(1_700_086_400_000L));
        when(other.getBrandName()).thenReturn("SYNTHETIC BRAND");
        try (var _ = mockConstruction(RxPrescriptionData.class, (instance, context) -> {
            when(instance.getPrescriptionScriptsByPatientRegionalIdentifier(101, "din-a"))
                    .thenReturn(new RxPrescriptionData.Prescription[]{first, renamed});
            when(instance.getPrescriptionScriptsByPatientRegionalIdentifier(101, "din-b"))
                    .thenReturn(new RxPrescriptionData.Prescription[]{other});
        })) {
            var intervals = MeasurementGraphAction22Action.getDrugDataSet(101, new String[]{"din-a", "din-b"});
            assertThat(intervals).extracting(m -> m.get("row")).containsExactly("0", "0", "1");
            assertThat(intervals).extracting(m -> m.get("name"))
                    .containsExactly("SYNTHETIC BRAND", "SYNTHETIC BRAND", "SYNTHETIC BRAND");
        }
    }

    /** A medication chart stays a combined plot with an empty "Meds" panel when nothing matched. */
    @Test
    void shouldKeepTheMedicationPanel_whenNoPrescriptionMatched() {
        var chart = MeasurementChart.lines("Synthetic", "Days", "MEDS", new MeasurementChart.SeriesCollection())
                .withMedications(List.of());
        assertThat(chart.toConfig()).containsEntry("medicationPanel", true);
        assertThat(MeasurementChart.lines("Synthetic", "Days", "kg", collection("Weight", 61)).toConfig())
                .doesNotContainKey("medicationPanel");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseHalfEvenPointLabels_withoutRoundingTheObservation() {
        for (String pattern : List.of("0.00", "0.###")) {
            var chart = MeasurementChart.lines("Synthetic", "Days", "kg", collection("Weight", 0.125))
                    .withItemLabels("{2}", pattern, 12);
            var datasets = (List<Map<String, Object>>) chart.toConfig().get("datasets");
            var point = ((List<Map<String, Object>>) datasets.getFirst().get("data")).getFirst();
            assertThat(point.get("y")).isEqualTo(0.125);
            assertThat(point.get("labelValue")).isEqualTo(new java.text.DecimalFormat(pattern).format(0.125));
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

    /** ChartFactory's time series chart: gray plot, white dashed grid, 2% date margins, no forced zero. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDescribeTheTimeSeriesChartLook_byDefault() {
        var chart = MeasurementChart.lines("Synthetic", "Days", "kg", collection("Weight", 61));
        var style = (Map<String, Object>) chart.toConfig().get("style");
        assertThat(style).containsEntry("theme", "jfree").containsEntry("plotBackground", "#c0c0c0").containsEntry("gridlines", "#ffffff")
                .containsEntry("domainMargins", List.of(0.02, 0.02)).containsEntry("rangeMargins", List.of(0.05, 0.05))
                .containsEntry("rangeIncludesZero", false).containsEntry("domainIncludesZero", false);
        assertThat(style.get("rangeAxisPaint")).isNull();
        assertThat(style.get("itemLabels")).isNull();
        var dataset = ((List<Map<String, Object>>) chart.toConfig().get("datasets")).getFirst();
        assertThat(dataset).containsEntry("kind", "line").containsEntry("lines", true).containsEntry("shapes", false).containsEntry("yAxisID", "y");
        assertThat(dataset.get("paint")).as("the renderer applies JFreeChart's series palette by index").isNull();
        assertThat(chart.toConfig()).doesNotContainKeys("pointLabels", "fixedDecimals");
    }

    /** The reference range chart was a high-low chart: its range dataset came first, in red, with zero on the axis. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDescribeTheHighLowChartLook_forReferenceRangeCharts() {
        var day = new MeasurementChart.Day(Instant.ofEpochMilli(1_700_000_000_000L));
        var ranges = List.of(new MeasurementChart.ReferenceRange(day.getStart(), 7, 40));
        var chart = MeasurementChart.highLow("HighLowChartDemo2", "Time", "Value", collection("ALT", 23), "DREFERENCE RANGE", ranges);
        var style = (Map<String, Object>) chart.toConfig().get("style");
        assertThat(style).containsEntry("plotBackground", "#ffffff").containsEntry("gridlines", "#ffffff")
                .containsEntry("domainMargins", List.of(0.9, 0.9)).containsEntry("rangeMargins", List.of(0.05, 0.2)).containsEntry("rangeIncludesZero", true);
        var datasets = (List<Map<String, Object>>) chart.toConfig().get("datasets");
        assertThat(datasets.get(0)).containsEntry("label", "DREFERENCE RANGE").containsEntry("kind", "range").containsEntry("paint", "#ff5555").containsEntry("widensAxis", true);
        assertThat((List<Map<String, Object>>) datasets.get(0).get("data")).singleElement()
                .satisfies(point -> assertThat(point).containsEntry("x", day.getStart().toEpochMilli()).containsEntry("low", 7.0).containsEntry("high", 40.0));
        assertThat(datasets.get(1)).containsEntry("label", "ALT").containsEntry("kind", "line").containsEntry("lines", true).containsEntry("shapes", false)
                .as("drawn by the same renderer as the range dataset, so it shares its first series paint").containsEntry("paint", "#ff5555")
                .containsEntry("highLow", true);
    }

    /** labChartRef painted its ranges and, through the shared axis object, the value axis green; the actual-lab charts let the plot's paint sequence colour them. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldColourTheRangeAxisGreen_onlyForGreenReferenceRanges() {
        var day = new MeasurementChart.Day(Instant.ofEpochMilli(1_700_000_000_000L));
        var ranges = List.of(new MeasurementChart.ReferenceRange(day.getStart(), 120, 160));
        var green = MeasurementChart.lines("Synthetic", "Days", "g/L", collection("Hemoglobin", 132)).withGreenReferenceRanges("Reference Range", ranges);
        var datasets = (List<Map<String, Object>>) green.toConfig().get("datasets");
        assertThat(datasets.get(1)).containsEntry("label", "Reference Range").containsEntry("kind", "range").containsEntry("paint", "#00ff00").containsEntry("widensAxis", false);
        assertThat((Map<String, Object>) green.toConfig().get("style")).containsEntry("rangeAxisPaint", "#00ff00");

        var sequence = MeasurementChart.lines("Synthetic", "Days", "g/L", collection("Hemoglobin", 132)).withReferenceRanges("Normal Reference Range", ranges);
        var sequenceDatasets = (List<Map<String, Object>>) sequence.toConfig().get("datasets");
        assertThat(sequenceDatasets.get(1)).containsEntry("label", "Normal Reference Range").containsEntry("kind", "range").containsEntry("widensAxis", true);
        assertThat(sequenceDatasets.get(1).get("paint")).as("the next paint of the sequence, after the series").isNull();
        assertThat(((Map<String, Object>) sequence.toConfig().get("style")).get("rangeAxisPaint")).isNull();

        var none = MeasurementChart.lines("Synthetic", "Days", "g/L", collection("Hemoglobin", 132)).withGreenReferenceRanges("Reference Range", List.of());
        assertThat((List<?>) none.toConfig().get("datasets")).hasSize(1);
        assertThat(((Map<String, Object>) none.toConfig().get("style")).get("rangeAxisPaint")).isNull();
    }

    /** The lab charts chose their item labels by bands of points (tens, or sixes with medications): date and value, value, value in 8pt, none. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldChooseLabItemLabels_byBandsOfPoints() {
        assertThat(MeasurementChart.labItemLabels(5)).containsEntry("format", "{1} \n {2}").containsEntry("decimals", "0.###").containsEntry("fontSize", 12);
        assertThat(MeasurementChart.labItemLabels(15)).containsEntry("format", "{2}").containsEntry("decimals", "0.###").containsEntry("fontSize", 12);
        assertThat(MeasurementChart.labItemLabels(25)).containsEntry("format", "{2}").containsEntry("fontSize", 8);
        assertThat(MeasurementChart.labItemLabels(35)).isEmpty();
        // The lab-plus-medication chart counted the same bands in sixes.
        assertThat(MeasurementChart.labItemLabels(5, 6)).containsEntry("format", "{1} \n {2}").containsEntry("fontSize", 12);
        assertThat(MeasurementChart.labItemLabels(8, 6)).containsEntry("format", "{2}").containsEntry("fontSize", 12);
        assertThat(MeasurementChart.labItemLabels(14, 6)).containsEntry("format", "{2}").containsEntry("fontSize", 8);
        assertThat(MeasurementChart.labItemLabels(18, 6)).isEmpty();
        var chart = MeasurementChart.lines("Synthetic", "Days", "kg", collection("Weight", 61))
                .withItemLabels(MeasurementChart.labItemLabels(35));
        assertThat((Map<String, Object>) chart.toConfig().get("style")).containsEntry("itemLabels", null);
    }

    /** The other lab charts overrode the factory look: wide date margins, a taller range, white plot, shapes, labels. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldOverrideTheLook_forLabCharts() {
        var chart = MeasurementChart.lines("Synthetic", "Days", "kg", collection("Weight", 61))
                .withDomainMargins(0.9, 0.9).withRangeUpperMargin(0.34).withPlotBackground("#ffffff").withGridlines("#c0c0c0")
                .withShapes().withItemLabels("{1} \n {2}", "0.00", 8);
        var style = (Map<String, Object>) chart.toConfig().get("style");
        assertThat(style).containsEntry("domainMargins", List.of(0.9, 0.9)).containsEntry("rangeMargins", List.of(0.05, 0.34))
                .containsEntry("plotBackground", "#ffffff").containsEntry("gridlines", "#c0c0c0");
        assertThat((Map<String, Object>) style.get("itemLabels")).containsEntry("format", "{1} \n {2}").containsEntry("decimals", "0.00").containsEntry("fontSize", 8);
        assertThat(((List<Map<String, Object>>) chart.toConfig().get("datasets")).getFirst()).containsEntry("shapes", true);
    }

    /** The scatter servlet: themed scatter in pure red squares; blood pressure as red and blue lines with squares, zero on the axis. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDescribeTheScatterLook_withTheServletsSeriesColours() {
        var series = new MeasurementChart.Series("ALT-in U/L"); series.add(0, 23); series.add(549, 37);
        var scatter = MeasurementChart.scatter("ALT-in U/L", "Day", "Test Results", List.of(series), false);
        var style = (Map<String, Object>) scatter.toConfig().get("style");
        assertThat(style).containsEntry("plotBackground", "#c0c0c0").containsEntry("domainMargins", List.of(0.05, 0.05))
                .containsEntry("rangeMargins", List.of(0.05, 0.05)).containsEntry("rangeIncludesZero", false).containsEntry("domainIncludesZero", false);
        var dataset = ((List<Map<String, Object>>) scatter.toConfig().get("datasets")).getFirst();
        assertThat(dataset).containsEntry("paint", "#ff0000").containsEntry("lines", false).containsEntry("shapes", true);

        var systolic = new MeasurementChart.Series("Systolic"); systolic.add(1, 120);
        var diastolic = new MeasurementChart.Series("Diastolic"); diastolic.add(1, 80);
        var pressure = MeasurementChart.scatter("BP", "Tests", "Hgmm", List.of(systolic, diastolic), true);
        assertThat((Map<String, Object>) pressure.toConfig().get("style")).containsEntry("rangeIncludesZero", true);
        var datasets = (List<Map<String, Object>>) pressure.toConfig().get("datasets");
        assertThat(datasets.get(0)).containsEntry("paint", "#ff0000").containsEntry("lines", true).containsEntry("shapes", true);
        assertThat(datasets.get(1)).containsEntry("paint", "#0000ff");
    }

    /** A second measurement type was drawn by a plain renderer in black, without shapes, on its own axis. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDrawTheSecondType_inBlackWithoutShapes() {
        var chart = MeasurementChart.lines("Synthetic", "Days", "kg", collection("Weight", 61)).withSecondAxis("cm", collection("Height", 170));
        var second = ((List<Map<String, Object>>) chart.toConfig().get("datasets")).get(1);
        assertThat(second).containsEntry("yAxisID", "y2").containsEntry("paint", "#000000").containsEntry("lines", true).containsEntry("shapes", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveScatterObservations_whenCoordinatesShareTheSameDay() {
        var series = new MeasurementChart.Series("Synthetic observations");
        series.add(2, 41);
        series.add(0, 23);
        series.add(0, 37);
        var chart = MeasurementChart.scatter("Synthetic", "Day", "Results", List.of(series), false);
        var datasets = (List<Map<String, Object>>) chart.toConfig().get("datasets");
        assertThat((List<Map<String, Object>>) datasets.getFirst().get("data")).containsExactly(
                Map.of("x", 0L, "y", 23.0), Map.of("x", 0L, "y", 37.0), Map.of("x", 2L, "y", 41.0));
        assertThat(series.getItemCount()).isEqualTo(3);
    }

    private static MeasurementChart.SeriesCollection collection(String name, double value) {
        var series = new MeasurementChart.Series(name);
        series.addOrUpdate(new MeasurementChart.Day(Instant.ofEpochMilli(1_700_000_000_000L)), value);
        var collection = new MeasurementChart.SeriesCollection();
        collection.addSeries(series);
        return collection;
    }
}
