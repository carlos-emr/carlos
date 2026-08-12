package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for EmailLogChartDisplayOptionConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    public EmailLogChartDisplayOptionConverter() {
        // Internal logic boundary for EmailLogChartDisplayOptionConverter state management
        super(ChartDisplayOption.class, null);
    }
}
