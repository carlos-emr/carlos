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
package io.github.carlos_emr.carlos.commn.dto;

import java.util.Objects;

/**
 * A populated base table that exists in the database but belongs neither to the matched pinned
 * physical profile nor to the reviewed package-operational list. It has no reviewed structure or
 * patient ownership, so the exporter never reads its values; the export lists it by name and
 * MariaDB's row estimate in protected diagnostics and in the encrypted ReadMe instead.
 *
 * @since 2026-11-13
 */
public record CdsExportLocalSourceDto(String table, long estimatedRows) {
    public CdsExportLocalSourceDto {
        Objects.requireNonNull(table, "table");
        if (!table.matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || estimatedRows < 1)
            throw new IllegalArgumentException("CDS_EXPORT_LOCAL_SOURCE_INVALID");
    }
}
