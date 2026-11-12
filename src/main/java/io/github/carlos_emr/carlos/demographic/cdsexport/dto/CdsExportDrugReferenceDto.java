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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.List;

/** Frozen public DPD product returned by the configured DrugRef unique-DIN lookup. */
public record CdsExportDrugReferenceDto(
        int version, String authority, String din, String product, List<Component> components) {
    public static final String AUTHORITY = "HEALTH_CANADA_DIN_VIA_DRUGREF";

    public record Component(String name, String strength, String unit) {
        @Override
        public String toString() {
            return "CdsExportDrugReferenceDto.Component[protected]";
        }
    }

    public CdsExportDrugReferenceDto {
        components = List.copyOf(components);
        if (version != 1
                || !AUTHORITY.equals(authority)
                || din == null
                || !din.matches("[0-9]{8}")
                || product == null
                || product.isBlank()
                || product.length() > 1024
                || components.size() > 64)
            throw new IllegalArgumentException("CDS_EXPORT_DRUG_REFERENCE_INVALID");
        for (var component : components) {
            if (component == null
                    || component.name() == null
                    || component.name().length() > 1024
                    || component.strength() == null
                    || component.strength().length() > 128
                    || component.unit() == null
                    || component.unit().length() > 128)
                throw new IllegalArgumentException("CDS_EXPORT_DRUG_REFERENCE_INVALID");
        }
    }

    @Override
    public String toString() {
        return "CdsExportDrugReferenceDto[protected]";
    }
}
