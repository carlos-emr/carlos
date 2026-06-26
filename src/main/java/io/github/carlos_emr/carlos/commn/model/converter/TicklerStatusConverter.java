package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for TicklerStatusConverter, mapping entity attributes to database columns.
 */
@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    // Handles the conversion logic for TicklerStatusConverter to maintain data persistence

    public TicklerStatusConverter() {
        super(STATUS.class, STATUS.A);
    }
}
