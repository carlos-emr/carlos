/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dto;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Exact bounded public provider projection; identifiers remain text, including legacy nonnumeric keys. */
public record CdsExportProviderRowDto(String id, Map<String, CdsExportRawCellDto> cells) {
    public static final Set<String> COLUMNS = Set.of("provider_no", "first_name", "last_name", "ohip_no", "practitionerNo", "practitionerNoType");
    public CdsExportProviderRowDto {
        if (id == null || id.codePointCount(0, id.length()) > 6 || cells == null || !cells.keySet().equals(COLUMNS)
                || cells.values().stream().anyMatch(cell -> cell == null || cell.bytes() > 1024 || !cell.sqlNull() && cell.inlineBytes() == null)) {
            throw new IllegalArgumentException("CDS_EXPORT_PUBLIC_PROVIDER_ROW_INVALID");
        }
        cells = Collections.unmodifiableMap(new TreeMap<>(cells));
    }
    @Override public String toString() { return "CdsExportProviderRowDto[protected]"; }
}
