package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for TicklerPriorityConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    public TicklerPriorityConverter() {
        // Internal logic boundary for TicklerPriorityConverter state management
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
