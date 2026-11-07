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
import java.util.Map;
import java.util.TreeMap;

/** Bounded immutable candidates from the same authenticated source generation; names are never lookup keys. */
public record CdsExportAppointmentLookupsDto(String sourceManifestSha256,
        Map<String, CdsExportCapturedTextRowDto> providers, Map<String, List<CdsExportCapturedTextRowDto>> statuses) {
    public CdsExportAppointmentLookupsDto {
        if (sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}") || providers == null || statuses == null) {
            throw new IllegalArgumentException("CDS_EXPORT_APPOINTMENT_LOOKUPS_INVALID");
        }
        providers = Map.copyOf(providers); var copied = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        statuses.forEach((code, rows) -> copied.put(code, List.copyOf(rows))); statuses = Map.copyOf(copied);
    }
    @Override public String toString() { return "CdsExportAppointmentLookupsDto[protected]"; }
}
