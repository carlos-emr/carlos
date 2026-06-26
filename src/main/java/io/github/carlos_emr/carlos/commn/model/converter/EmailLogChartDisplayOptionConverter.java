package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for EmailLogChartDisplayOptionConverter, mapping entity attributes to database columns.
 */
@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    // Handles the conversion logic for EmailLogChartDisplayOptionConverter to maintain data persistence

    public EmailLogChartDisplayOptionConverter() {
        super(ChartDisplayOption.class, null);
    }
}
