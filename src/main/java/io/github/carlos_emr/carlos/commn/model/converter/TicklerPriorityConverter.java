package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for tickler urgency levels.
 * Maps priority levels like HIGH, NORMAL, LOW to persistence values.
 */
@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    public TicklerPriorityConverter() {
        // Translate urgency enum values to ensure legacy sorting logic remains functional
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
