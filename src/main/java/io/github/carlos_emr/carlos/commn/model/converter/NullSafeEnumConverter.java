/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.model.converter;

import jakarta.persistence.AttributeConverter;

/**
 * Abstract base for enum converters that safely handle empty strings and null
 * values read from the database.
 *
 * <p><strong>Why this exists:</strong> Hibernate 7 strictly calls
 * {@code Enum.valueOf()} for {@code @Enumerated(EnumType.STRING)} columns.
 * If the database contains an empty string ({@code ''}) or an unrecognised
 * value, Hibernate throws {@link IllegalArgumentException}.
 * Earlier Hibernate versions silently mapped these to {@code null}.</p>
 *
 * <p>Subclasses only need to supply the enum class and an optional default
 * value via the two-arg constructor.</p>
 *
 * @param <E> the enum type
 * @since 2026-03-19
 */
public abstract class NullSafeEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumClass;
    private final E defaultValue;

    /**
     * @param enumClass    the enum type to convert
     * @param defaultValue value to return when the database column is null,
     *                     blank, or contains an unrecognised string.
     *                     May itself be {@code null} if the field is legitimately nullable.
     */
    protected NullSafeEnumConverter(Class<E> enumClass, E defaultValue) {
        this.enumClass = enumClass;
        this.defaultValue = defaultValue;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return (attribute != null) ? attribute.name() : null;
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, dbData);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
