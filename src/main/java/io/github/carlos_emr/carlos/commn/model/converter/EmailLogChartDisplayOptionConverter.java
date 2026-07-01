package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for email log chart display options.
 * <p>
 * Handles the persistence mapping of visibility settings for email logs
 * within the patient chart of CARLOS EMR.
 * </p>
 */
@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    public EmailLogChartDisplayOptionConverter() {
        // Convert chart display option enum for persistence to ensure patient privacy rules are enforced.
        super(ChartDisplayOption.class, null);
    }
}
