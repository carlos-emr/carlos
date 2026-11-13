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
 * A reviewed LISTED table (a removed CARLOS feature) and the exact number of one patient's rows
 * in it. The rows are never read or exported; the encrypted package ReadMe names the table and
 * the count so the recipient knows what was not transferred.
 *
 * @since 2026-11-13
 */
public record CdsExportListedSourceDto(String table, long rows) {
    public CdsExportListedSourceDto {
        Objects.requireNonNull(table, "table");
        if (!table.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || rows < 1)
            throw new IllegalArgumentException("CDS_EXPORT_LISTED_SOURCE_INVALID");
    }
}
