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
package io.github.carlos_emr.carlos.report.data;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Opens streaming text for report cells, including UTF-8 text stored in binary columns.
 * Consumers retain responsibility for their existing character and deadline budgets.
 *
 * @since 2026-11-16
 */
public final class ReportJdbcTextReader {
    private ReportJdbcTextReader() { }

    /**
     * Opens a cell without materializing the entire value with {@code getString}.
     * MariaDB's binary BLOB codec refuses character streams; its legacy string
     * conversion decodes the bytes as UTF-8. A binary stream preserves that text
     * conversion while allowing callers to stop at their existing budgets.
     *
     * @param rows positioned JDBC result set
     * @param column one-based column index
     * @param jdbcType column type from its JDBC metadata
     * @return caller-owned reader, or null for SQL NULL
     * @throws SQLException if the driver cannot open the cell
     */
    public static Reader open(ResultSet rows, int column, int jdbcType) throws SQLException {
        return switch (jdbcType) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                var stream = rows.getBinaryStream(column);
                yield stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
            }
            default -> rows.getCharacterStream(column);
        };
    }
}
