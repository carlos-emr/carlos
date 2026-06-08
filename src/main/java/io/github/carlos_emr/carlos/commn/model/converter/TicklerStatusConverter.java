package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the TicklerStatus enum to its database column.
 * Ensures that TicklerStatus values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        // Initialize the converter mapping to the target enum class
        super(STATUS.class, STATUS.A);
    }
}
