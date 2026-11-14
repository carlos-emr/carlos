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
import java.util.TreeMap;

/** Raw scalar header for a bounded source page; a digest key is never converted to a number. */
public record CdsExportRawKeyRowDto(String id, Map<String, CdsExportRawCellDto> cells) {
    public CdsExportRawKeyRowDto {
        if (id == null || !id.matches("[0-9a-f]{64}|[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}|-?(0|[1-9][0-9]{0,18})") || cells == null || cells.isEmpty()
                || cells.size() > 4096 || cells.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || !entry.getKey().matches("[A-Za-z0-9_]{1,128}") || entry.getValue() == null)) {
            throw new IllegalArgumentException("CDS_EXPORT_RAW_ROW_INVALID");
        }
        cells = Collections.unmodifiableMap(new TreeMap<>(cells));
    }
    @Override public String toString() { return "CdsExportRawKeyRowDto[protected]"; }
}
