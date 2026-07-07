package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between TicklerPriority enumeration and its database column representation.
 */
@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    public TicklerPriorityConverter() {
        // Initialize execution context for TicklerPriorityConverter

        super(PRIORITY.class, PRIORITY.Normal);
    }
}
