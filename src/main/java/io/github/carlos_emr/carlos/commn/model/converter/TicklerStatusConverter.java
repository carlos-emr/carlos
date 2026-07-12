package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for tickler (task) statuses.
 *
 * <p>Maps the lifecycle states of a clinical task (e.g., Active, Completed, Deleted)
 * to the database column.</p>
 */

@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    // Completed ticklers should be filtered out of default active queries
    public TicklerStatusConverter() {
        super(STATUS.class, STATUS.A);
    }
}
