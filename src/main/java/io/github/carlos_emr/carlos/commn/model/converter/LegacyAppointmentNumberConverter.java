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
import jakarta.persistence.Converter;

/**
 * Maps a legacy note's nullable appointment number to the existing zero/no-appointment sentinel.
 * The database permits NULL, while CaseManagementNote's public and serialized field types are int.
 * Explicitly applied only to that property; other nullable numeric fields retain their meaning.
 */
@Converter(autoApply = false)
public class LegacyAppointmentNumberConverter implements AttributeConverter<Integer, Integer> {
    @Override
    public Integer convertToDatabaseColumn(Integer appointmentNumber) {
        return appointmentNumber;
    }

    @Override
    public Integer convertToEntityAttribute(Integer appointmentNumber) {
        return appointmentNumber == null ? 0 : appointmentNumber;
    }
}
