package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for email chart display options.
 *
 * <p>Determines how and if a specific email transaction should be rendered
 * within the patient's clinical notes view.</p>
 */

@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    // Ensure sensitive emails are hidden from the default chart view if requested
    public EmailLogChartDisplayOptionConverter() {
        super(ChartDisplayOption.class, null);
    }
}
