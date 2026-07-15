package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA AttributeConverter mapping task/tickler lifecycle states (active, completed, deleted).
 */

@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        super(STATUS.class, STATUS.A);
    }
}
