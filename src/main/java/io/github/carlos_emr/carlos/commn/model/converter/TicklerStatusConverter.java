package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for tickler status.
 * <p>
 * Maps the status of a tickler (task/reminder) enum to its character or string
 * equivalent in the CARLOS EMR database.
 * </p>
 */
@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        // Convert tickler status enum to character representation defined by the legacy database schema.
        super(STATUS.class, STATUS.A);
    }
}
