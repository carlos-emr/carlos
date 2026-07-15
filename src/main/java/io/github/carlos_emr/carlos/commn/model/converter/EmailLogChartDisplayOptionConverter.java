package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import jakarta.persistence.Converter;
/**
 * JPA AttributeConverter determining how email records are presented in the patient chart.
 */

@Converter
public class EmailLogChartDisplayOptionConverter extends NullSafeEnumConverter<ChartDisplayOption> {
    public EmailLogChartDisplayOptionConverter() {
        super(ChartDisplayOption.class, null);
    }
}
