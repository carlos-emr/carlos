package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;
/**
 * JPA Attribute Converter for mapping TicklerPriority enum values.
 * <p>
 * Safely translates between the domain enumeration and its database column representation.
 *
 * @since 2026-05-05
 */

@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    public TicklerPriorityConverter() {
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
