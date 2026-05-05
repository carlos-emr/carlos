package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;

/**
 * EmailLogChartDisplayOptionConverter provides functionality and data models for the EmailLogChartDisplayOptionConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    public EmailLogChartDisplayOptionConverter() {
        super(ChartDisplayOption.class, null);
    }
}
