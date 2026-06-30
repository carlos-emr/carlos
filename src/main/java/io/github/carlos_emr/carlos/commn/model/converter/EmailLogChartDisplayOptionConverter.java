package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter defining chart visibility options for an email log.
 * Dictates whether the communication history is visible in the patient chart.
 */
@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    public EmailLogChartDisplayOptionConverter() {
        // Process visibility flag dictating if communication appears within clinical chart views
        super(ChartDisplayOption.class, null);
    }
}
