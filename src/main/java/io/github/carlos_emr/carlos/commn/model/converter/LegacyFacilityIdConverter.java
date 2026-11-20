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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Reads legacy NULL message facility IDs as the existing local-facility sentinel (zero).
 * Explicit conversion preserves MessageList's primitive field and public API contracts,
 * including the migration tooling's primitive-column metadata.
 *
 * @since 2026-10-03
 */
@Converter(autoApply = false)
public class LegacyFacilityIdConverter implements AttributeConverter<Integer, Integer> {
    /**
     * Preserves the facility identifier when writing the database column.
     * @param facilityId facility identifier, possibly null
     * @return the unchanged identifier
     */
    @Override
    public Integer convertToDatabaseColumn(Integer facilityId) {
        return facilityId;
    }

    /**
     * Supplies the local-facility sentinel before Hibernate assigns the primitive entity field.
     * @param facilityId identifier read from the nullable legacy column
     * @return zero for null, otherwise the unchanged identifier
     */
    @Override
    public Integer convertToEntityAttribute(Integer facilityId) {
        return facilityId == null ? 0 : facilityId;
    }
}
