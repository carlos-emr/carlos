package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the STATUS enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    // Initialize the null-safe converter with the specific STATUS class.
    public TicklerStatusConverter() {
        super(STATUS.class, STATUS.A);
    }
}
