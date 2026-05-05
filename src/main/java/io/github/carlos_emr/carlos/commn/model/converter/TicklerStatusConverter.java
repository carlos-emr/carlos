package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;

/**
 * TicklerStatusConverter provides functionality and data models for the TicklerStatusConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        super(STATUS.class, STATUS.A);
    }
}
