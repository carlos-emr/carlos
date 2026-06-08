package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the EmailLogChartDisplayOption enum to its database column.
 * Ensures that EmailLogChartDisplayOption values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    public EmailLogChartDisplayOptionConverter() {
        // Initialize the converter mapping to the target enum class
        super(ChartDisplayOption.class, null);
    }
}
