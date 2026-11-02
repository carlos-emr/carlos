/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.eform.util.BrowserChartRenderer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        var secondary = new MeasurementChart.Series("Synthetic temperature");
        secondary.addOrUpdate(new MeasurementChart.Day(new Date(1_700_000_000_000L)), 36.5);
        secondary.addOrUpdate(new MeasurementChart.Day(new Date(1_700_086_400_000L)), 37.2);
        var secondaryCollection = new MeasurementChart.SeriesCollection();
        secondaryCollection.addSeries(secondary);
        MeasurementChart chart = MeasurementChart.lines("Synthetic chart", "Days", "kg", collection)
                .withSecondAxis("C", secondaryCollection)
                .withReferenceRanges(List.of(new MeasurementChart.ReferenceRange(new Date(1_700_000_000_000L), 55, 70)))
                .withMedications(List.of(Map.of("name", "Synthetic medication", "start", 1_699_900_000_000L, "end", 1_700_100_000_000L)));
        Path project = projectRoot();
        var context = new MockServletContext(project.resolve("src/main/webapp").toUri().toString(), new FileSystemResourceLoader());
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            for (String format : List.of("png", "jpeg")) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                BrowserChartRenderer.write(context, output, chart, 1000, 600, format);
                var image = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));
                assertThat(image).isNotNull();
                assertThat(image.getWidth()).isEqualTo(1000);
                assertThat(image.getHeight()).isEqualTo(600);
                int seriesPixels = 0, referencePixels = 0, medicationPixels = 0;
                for (int x = 40; x < 980; x++) for (int y = 80; y < 580; y++) {
                    int rgb = image.getRGB(x, y);
                    int red = (rgb >>> 16) & 255, green = (rgb >>> 8) & 255, blue = rgb & 255;
                    if (y < 380 && red > 120 && red > green * 1.5 && red > blue * 1.5) seriesPixels++;
                    if (y < 380 && green > 60 && green > red * 1.3 && green > blue * 1.3) referencePixels++;
                    if (y > 450 && blue > red + 30 && blue > green + 15) medicationPixels++;
                }
                assertThat(seriesPixels).as("measurement series in %s", format).isGreaterThan(20);
                assertThat(referencePixels).as("reference range in %s", format).isGreaterThan(20);
                assertThat(medicationPixels).as("medication interval in %s", format).isGreaterThan(1000);
                Files.write(project.resolve("target/measurement-chart-smoke." + format), output.toByteArray());
            }
        }
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("src/main/webapp/library/chartjs/chart.umd.min.js"))) {
                return current;
            }
        }
        throw new IllegalStateException("Cannot locate the project chart resources");
    }
}
