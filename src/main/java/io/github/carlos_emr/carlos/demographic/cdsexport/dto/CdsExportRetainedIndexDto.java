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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded structurally checked retained-input index. Historical row receipt digests remain
 * assertions requiring actual row verification. This does not qualify clinical mappings,
 * resolve replay/merge aliases or establish that native projections are unchanged.
 * nativeParents maps individual retained field IDs to native unit addresses. The field's
 * separate preservation-unit address comes from its captured run_id and unit_key columns.
 */
public record CdsExportRetainedIndexDto(
        String sourceManifestSha256,
        long metadataRows,
        long metadataBytes,
        Map<String, Map<String, CdsExportCapturedTextRowDto>> tables,
        Set<String> appliedUnits,
        Map<String, List<Reference>> references,
        Map<String, String> nativeParents,
        Map<String, String> effectiveOptionsByUnit) {
    public CdsExportRetainedIndexDto(
            String sourceManifestSha256,
            long metadataRows,
            long metadataBytes,
            Map<String, Map<String, CdsExportCapturedTextRowDto>> tables,
            Set<String> appliedUnits,
            Map<String, List<Reference>> references,
            Map<String, String> nativeParents) {
        this(
                sourceManifestSha256,
                metadataRows,
                metadataBytes,
                tables,
                appliedUnits,
                references,
                nativeParents,
                Map.of());
    }

    public record Reference(String table, String id, String digest) {
        @Override
        public String toString() {
            return "CdsExportRetainedIndexDto.Reference[protected]";
        }
    }

    public CdsExportRetainedIndexDto {
        if (sourceManifestSha256 == null
                || !sourceManifestSha256.matches("[0-9a-f]{64}")
                || metadataRows < 0
                || metadataBytes < 0) {
            throw new IllegalArgumentException("CDS_EXPORT_RETAINED_INDEX_INVALID");
        }
        var copied = new TreeMap<String, Map<String, CdsExportCapturedTextRowDto>>();
        tables.forEach(
                (table, rows) ->
                        copied.put(table, Collections.unmodifiableMap(new TreeMap<>(rows))));
        tables = Collections.unmodifiableMap(copied);
        appliedUnits = Set.copyOf(appliedUnits);
        nativeParents = Map.copyOf(nativeParents);
        effectiveOptionsByUnit = Map.copyOf(effectiveOptionsByUnit);
        if (!appliedUnits.containsAll(effectiveOptionsByUnit.keySet())
                || effectiveOptionsByUnit.values().stream()
                        .anyMatch(value -> !value.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("CDS_EXPORT_RETAINED_INDEX_INVALID");
        var refs = new TreeMap<String, List<Reference>>();
        references.forEach((unit, rows) -> refs.put(unit, List.copyOf(rows)));
        references = Collections.unmodifiableMap(refs);
    }

    @Override
    public String toString() {
        return "CdsExportRetainedIndexDto[protected]";
    }
}
