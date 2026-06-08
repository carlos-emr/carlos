package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the TicklerPriority enum to its database column.
 * Ensures that TicklerPriority values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    public TicklerPriorityConverter() {
        // Initialize the converter mapping to the target enum class
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
