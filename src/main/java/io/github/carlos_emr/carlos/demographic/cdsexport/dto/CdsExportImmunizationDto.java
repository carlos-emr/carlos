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

/**
 * Typed historical or reconciled vaccine event; neither this value nor its writer establishes
 * current chart coverage. {@code sourceContext} is the token-safe JSON of the complete native
 * rows for the {@code CARLOS.Prevention.Source.v1} residual, or null when no residual is written
 * (a reconciled imported occurrence keeps its retained original elements).
 */
public record CdsExportImmunizationDto(String name, String type, String manufacturer, String lot,
        String route, String site, String dose, String code, String codeSystem, CdsExportDateDto date,
        boolean refused, String instructions, String notes, String sourceContext) {
    public CdsExportImmunizationDto {
        java.util.Objects.requireNonNull(date);
        if ((code == null) != (codeSystem == null)) throw new IllegalArgumentException("CDS_EXPORT_IMMUNIZATION_CODE_INCOMPLETE");
    }

    /** A value without the native residual (prepared and reconciled imported occurrences). */
    public CdsExportImmunizationDto(String name, String type, String manufacturer, String lot,
            String route, String site, String dose, String code, String codeSystem,
            CdsExportDateDto date, boolean refused, String instructions, String notes) {
        this(name, type, manufacturer, lot, route, site, dose, code, codeSystem, date, refused,
                instructions, notes, null);
    }
    @Override public String toString() { return "CdsExportImmunizationDto[protected]"; }
}
