package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the PRIORITY enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    // Initialize the null-safe converter with the specific PRIORITY class.
    public TicklerPriorityConverter() {
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
