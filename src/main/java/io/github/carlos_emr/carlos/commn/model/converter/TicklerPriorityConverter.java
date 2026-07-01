package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for tickler priority.
 * <p>
 * Maps the tickler priority enum (e.g., high, normal, low) to its
 * respective database value in CARLOS EMR.
 * </p>
 */
@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    public TicklerPriorityConverter() {
        // Map tickler priority enum to integer format required by the underlying legacy schema.
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
