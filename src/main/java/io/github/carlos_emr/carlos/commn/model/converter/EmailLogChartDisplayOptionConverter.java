package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;

/**
 * Handles JPA attribute conversion for EmailLogChartDisplayOption types.
 *
 * @since 2026-06-10
 */
@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    public EmailLogChartDisplayOptionConverter() {
        super(ChartDisplayOption.class, null);
    }
}
