package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for tickler (task) statuses.
 * Maps states like ACTIVE, COMPLETED, or DELETED into persistence codes.
 */
@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        // Convert task states seamlessly to prevent invalid statuses entering the workflow DB
        super(STATUS.class, STATUS.A);
    }
}
