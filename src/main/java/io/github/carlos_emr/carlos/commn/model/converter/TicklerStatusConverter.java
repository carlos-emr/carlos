package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for the TicklerStatus enum.
 * Translates the lifecycle state of a tickler/task (e.g., Active, Completed, Deleted)
 * to the corresponding single-character flag in the legacy tickler table.
 */

@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        super(STATUS.class, STATUS.A);
    }
}
