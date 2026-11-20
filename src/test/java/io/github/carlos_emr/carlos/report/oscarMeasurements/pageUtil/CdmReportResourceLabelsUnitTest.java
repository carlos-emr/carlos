/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CdmReportResourceLabelsUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"en", "es", "fr", "pl", "pt_BR"})
    void shouldKeepLabelsSeparateFromFormattedValidationMessages_whenLoadingLocale(String locale) throws Exception {
        var properties = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream("oscarResources_" + locale + ".properties")) {
            assertNotNull(stream);
            properties.load(stream);
        }
        for (String bound : List.of("Upper", "Lower")) {
            String key = "oscarReport.CDMReport.msg" + bound + "Bound";
            String label = properties.getProperty(key);
            assertNotNull(label);
            assertFalse(label.isBlank());
            assertFalse(label.contains("{0}"), "A field label must not require a measurement argument");
            String message = properties.getProperty(key + "Value");
            assertNotNull(message);
            assertTrue(new MessageFormat(message).format(new Object[]{"HbA1c"}).contains("HbA1c"));
        }
    }
}
