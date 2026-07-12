package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.PRIORITY;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for tickler priorities.
 *
 * <p>Maps the urgency level (e.g., Normal, High, Urgent) of a clinical task
 * to its persistent state.</p>
 */

@Converter
public class TicklerPriorityConverter extends NullSafeEnumConverter<PRIORITY> {
    // Urgent priorities should trigger UI highlights for the assigned provider
    public TicklerPriorityConverter() {
        super(PRIORITY.class, PRIORITY.Normal);
    }
}
