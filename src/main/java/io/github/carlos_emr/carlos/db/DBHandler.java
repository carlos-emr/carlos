/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @deprecated Use JPA via {@link jakarta.persistence.EntityManager#createNativeQuery(String)}
 * instead. Inject or otherwise obtain an {@link jakarta.persistence.EntityManager}
 * and create a native query from it. No new code should be written against this
 * class. Scheduled for removal once remaining callers migrate.
 */
@Deprecated(forRemoval = true)
public final class DBHandler {

    private DBHandler() {
        // not intented for instantiation
    }

    // GetSQL(String) removed; all callers migrated to parameterized SQL.
    // See git history for the deprecated raw SQL execution method.

    public static ResultSet getPreSql(String sql, Object... params) throws SQLException {
        return getPreSql(sql, false, params);
    }

    public static ResultSet getPreSql(String sql, boolean updatable, Object... params) throws SQLException {
        return LegacyJdbcQuery.getPreparedResultSet(sql, updatable, params);
    }

    /**
     * @deprecated Use {@link #getPreSql(String, Object...)}. Kept only as a
     * migration compatibility wrapper while production callers move off this
     * deprecated class.
     */
    @Deprecated(forRemoval = true)
    public static ResultSet GetPreSQL(String sql, Object... params) throws SQLException {
        return getPreSql(sql, params);
    }

    /**
     * @deprecated Use {@link #getPreSql(String, boolean, Object...)}. Kept only
     * as a migration compatibility wrapper while production callers move off
     * this deprecated class.
     */
    @Deprecated(forRemoval = true)
    public static ResultSet GetPreSQL(String sql, boolean updatable, Object... params) throws SQLException {
        return getPreSql(sql, updatable, params);
    }

}
