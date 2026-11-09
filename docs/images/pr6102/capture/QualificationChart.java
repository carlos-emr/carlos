/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.time.Instant;
/** Synthetic model using the installed chart API without changing its package visibility.
 * @since 2026-11-09
 */
public final class QualificationChart {
    private QualificationChart() { }
    public static MeasurementChart create() {
        var series = new MeasurementChart.Series("Synthetic readings");
        int day = 0;
        for (double value : new double[]{0.125, 0.0625, -0.125}) {
            series.addOrUpdate(new MeasurementChart.Day(Instant.parse("2024-01-15T12:00:00Z").plusSeconds(86400L * day++)), value);
        }
        var data = new MeasurementChart.SeriesCollection(); data.addSeries(series);
        return MeasurementChart.lines("SYNTHETIC locale/rounding", "Days", "Units", data).withShapes().withItemLabels("{2}", "0.00", 12);
    }
}
