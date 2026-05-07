package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;
/**
 * JPA Attribute Converter for Tickler Status Converter.
 *
 * @since 2026-05-05
 */


@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        super(STATUS.class, STATUS.A);
    }
}
