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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Holds a parameterized SQL template alongside its bind-parameter values.
 * Used to separate the SQL structure from user-supplied values, preventing
 * SQL injection by ensuring values are bound via {@code PreparedStatement}
 * placeholders ({@code ?}) rather than string concatenation.
 *
 * <p>Invariants enforced at construction:
 * <ul>
 *   <li>{@code sql} is non-null</li>
 *   <li>the count of {@code ?} characters in {@code sql} equals {@code params.size()}</li>
 * </ul>
 * The placeholder count is derived by a simple character scan of {@code ?},
 * which is correct for this codebase's SQL and intentionally stricter than
 * dialects that allow {@code ?} in string literals or operators.
 */
public final class ParameterizedSql {

    private final String sql;
    private final List<Object> params;

    /**
     * Creates a new parameterized SQL holder.
     *
     * @param sql    the SQL template with {@code ?} placeholders; must be non-null
     * @param params the parameter values to bind, in order; a null list is treated as empty
     * @throws NullPointerException     if {@code sql} is null
     * @throws IllegalArgumentException if the number of {@code ?} characters in {@code sql}
     *                                  does not match {@code params.size()}
     */
    public ParameterizedSql(String sql, List<Object> params) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.params = params != null ? new ArrayList<>(params) : new ArrayList<>();
        int placeholderCount = countPlaceholders(sql);
        if (placeholderCount != this.params.size()) {
            throw new IllegalArgumentException(
                    "Placeholder/param count mismatch: sql has " + placeholderCount
                            + " ? marker(s) but " + this.params.size() + " param(s) supplied");
        }
    }

    private static int countPlaceholders(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '?') count++;
        }
        return count;
    }

    /**
     * Returns the SQL template with {@code ?} placeholders.
     *
     * @return the SQL template string
     */
    public String getSql() {
        return sql;
    }

    /**
     * Returns an unmodifiable view of the bind-parameter values.
     *
     * @return the parameter values in placeholder order
     */
    public List<Object> getParams() {
        return Collections.unmodifiableList(params);
    }

    /**
     * Returns the parameters as an {@code Object[]} array suitable for
     * passing to {@code DBHelp.searchDBRecord(sql, params)}.
     *
     * @return the parameter values as an array
     */
    public Object[] getParamsArray() {
        return params.toArray();
    }
}
