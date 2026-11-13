/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Preserves the primitive dose-bound API's zero default for schema-legal legacy NULL values. */
@Converter(autoApply = false)
public class LegacyZeroFloatConverter implements AttributeConverter<Float, Float> {
    /** Retains the actual fractional value when persisting a dose bound. */
    @Override
    public Float convertToDatabaseColumn(Float value) {
        return value;
    }

    /** Returns the existing model default for NULL without changing non-null values. */
    @Override
    public Float convertToEntityAttribute(Float value) {
        return value == null ? 0f : value;
    }
}
