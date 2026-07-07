package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between EmailLogChartDisplayOption enumeration and its database column representation.
 */
@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    public EmailLogChartDisplayOptionConverter() {
        // Initialize execution context for EmailLogChartDisplayOptionConverter

        super(ChartDisplayOption.class, null);
    }
}
