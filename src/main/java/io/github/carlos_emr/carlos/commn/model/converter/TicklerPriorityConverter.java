package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for TicklerPriorityConverter, mapping entity attributes to database columns.
 */
@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    // Handles the conversion logic for TicklerPriorityConverter to maintain data persistence

    public TicklerPriorityConverter() {
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
