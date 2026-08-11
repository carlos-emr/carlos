package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating tickler priority enum types to database columns.
 */
@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    public TicklerPriorityConverter() {
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
