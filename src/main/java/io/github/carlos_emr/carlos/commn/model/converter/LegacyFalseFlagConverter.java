/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps NULL legacy flags to the existing false default without changing
 * the model's primitive boolean API. The schema permits NULL for these flags.
 * Applied explicitly to those properties only; writes retain their Boolean value.
 *
 * @since 2026-09-27
 */
@Converter(autoApply = false)
public class LegacyFalseFlagConverter implements AttributeConverter<Boolean, Boolean> {
    /** Returns the model value unchanged for persistence. */
    @Override
    public Boolean convertToDatabaseColumn(Boolean value) {
        return value;
    }

    /** Uses the model's false default for a legacy NULL database value. */
    @Override
    public Boolean convertToEntityAttribute(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
