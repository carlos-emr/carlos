package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;

/**
 * Handles JPA attribute conversion for TicklerPriority types.
 *
 * @since 2026-06-10
 */
@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    public TicklerPriorityConverter() {
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
