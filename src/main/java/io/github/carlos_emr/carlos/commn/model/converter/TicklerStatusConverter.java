package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Tickler.STATUS;
import jakarta.persistence.Converter;

@Converter
public class TicklerStatusConverter extends NullSafeEnumConverter<STATUS> {
    public TicklerStatusConverter() {
        super(STATUS.class, STATUS.A);
    }
}
