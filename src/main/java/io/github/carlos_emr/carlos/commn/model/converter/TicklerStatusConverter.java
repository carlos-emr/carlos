package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for TicklerStatusConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        // Internal logic boundary for TicklerStatusConverter state management
        super(STATUS.class, STATUS.A);
    }
}
