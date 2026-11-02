/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.eform.util.BrowserChartRenderer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@Tag("integration")
@Tag("measurement")
class MeasurementChartSmokeIntegrationTest {
    @Test
    void shouldRenderClinicalSeriesRangesAndMedicationIntervalsAsPngAndJpeg() throws Exception {
        String endpoint = System.getenv("CARLOS_SMOKE_DRIVER_URL");
        String binary = System.getenv("CARLOS_SMOKE_CHROMIUM");
        assumeTrue(endpoint != null && binary != null, "local browser service required");
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("eform_pdf_browser_service_url", "http://127.0.0.1:9515")).thenReturn(endpoint);
        when(properties.getProperty("eform_pdf_browser_chromium_path")).thenReturn(binary);
        var series = new MeasurementChart.Series("Synthetic measurement");
        series.addOrUpdate(new MeasurementChart.Day(new Date(1_700_000_000_000L)), 61);
        series.addOrUpdate(new MeasurementChart.Day(new Date(1_700_086_400_000L)), 63);
        var collection = new MeasurementChart.SeriesCollection();
        collection.addSeries(series);
        MeasurementChart chart = MeasurementChart.lines("Synthetic chart", "Days", "kg", collection)
                .withReferenceRanges(List.of(new MeasurementChart.ReferenceRange(new Date(1_700_000_000_000L), 55, 70)))
                .withMedications(List.of(Map.of("name", "Synthetic medication", "start", 1_699_900_000_000L, "end", 1_700_100_000_000L)));
        var context = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            for (String format : List.of("png", "jpeg")) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                BrowserChartRenderer.write(context, output, chart, 1000, 600, format);
                var image = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));
                assertThat(image).isNotNull();
                assertThat(image.getWidth()).isEqualTo(1000);
                assertThat(image.getHeight()).isEqualTo(600);
                int colored = 0;
                for (int x = 0; x < 1000; x += 5) for (int y = 0; y < 600; y += 5) {
                    if ((image.getRGB(x, y) & 0xffffff) != 0xffffff) colored++;
                }
                assertThat(colored).isGreaterThan(100);
            }
        }
    }
}
