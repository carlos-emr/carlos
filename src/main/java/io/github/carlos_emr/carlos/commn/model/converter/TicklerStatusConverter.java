package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between TicklerStatus enumeration and its database column representation.
 */
@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        // Initialize execution context for TicklerStatusConverter

        super(STATUS.class, STATUS.A);
    }
}
