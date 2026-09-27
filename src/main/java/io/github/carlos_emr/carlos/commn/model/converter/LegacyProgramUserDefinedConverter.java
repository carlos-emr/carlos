/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Preserves the schema and model's true default for a nullable legacy userDefined flag.
 *
 * @since 2026-09-26
 */
@Converter(autoApply = false)
public class LegacyProgramUserDefinedConverter implements AttributeConverter<Boolean, Boolean> {
    /** Returns the model value unchanged for persistence, including NULL. */
    @Override
    public Boolean convertToDatabaseColumn(Boolean value) {
        return value;
    }

    /** A legacy NULL uses the same true default as a newly created program. */
    @Override
    public Boolean convertToEntityAttribute(Boolean value) {
        return value == null || value;
    }
}
