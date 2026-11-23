/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Reads legacy nullable integers through an explicitly selected primitive zero default.
 * Writes retain the supplied value; other nullable properties keep their existing semantics.
 * @since 2026.08
 */
@Converter(autoApply = false)
public class LegacyZeroIntegerConverter implements AttributeConverter<Integer, Integer> {
    /**
     * Preserves the model value for persistence.
     * @param value Integer model value, possibly {@code null}
     * @return the unchanged value
     */
    @Override
    public Integer convertToDatabaseColumn(Integer value) {
        return value;
    }

    /**
     * Applies the primitive property's existing zero default to legacy NULLs.
     * @param value Integer database value, possibly {@code null}
     * @return zero for NULL, otherwise the unchanged value
     */
    @Override
    public Integer convertToEntityAttribute(Integer value) {
        return value == null ? 0 : value;
    }
}
