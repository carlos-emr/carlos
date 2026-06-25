package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the ChartDisplayOption enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    // Initialize the null-safe converter with the specific ChartDisplayOption class.
    public EmailLogChartDisplayOptionConverter() {
        super(ChartDisplayOption.class, null);
    }
}
