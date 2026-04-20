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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import io.github.carlos_emr.carlos.utility.DbConnectionFilter;

/**
 * @deprecated Use JPA ({@code entityManager().createNativeQuery(...)}) instead;
 * no new code should be written against this class. Scheduled for removal once
 * remaining callers migrate.
 */
@Deprecated(forRemoval = true)
public final class DBHandler {

    private DBHandler() {
        // not intented for instantiation
    }

    // GetSQL(String) removed — all callers migrated to GetPreSQL.
    // See git history for the deprecated raw SQL execution method.

	private static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
		for (int i = 0; i < params.length; i++) {
			Object p = params[i];
			if (p == null) {
				ps.setNull(i+1, Types.NULL);
			} else {
				ps.setObject(i+1, p);
			}
		}
	}

	public static ResultSet GetPreSQL(String sql, Object... params) throws SQLException {
		return GetPreSQL(sql, false, params);
	}

	public static ResultSet GetPreSQL(String sql, boolean updatable, Object... params) throws SQLException { // nosemgrep: formatted-sql-string -- this IS the parameterized query method; params are bound via PreparedStatement
		PreparedStatement ps = DbConnectionFilter
			.getThreadLocalDbConnection()
			.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, // codeql[java/sql-injection] — GetPreSQL IS the parameterized query method; params bound via PreparedStatement below
				updatable ? ResultSet.CONCUR_UPDATABLE : ResultSet.CONCUR_READ_ONLY);
		ResultSet rs;
		try {
			bindParams(ps, params);
			rs = ps.executeQuery(); // NOSONAR javasecurity:S3649 — this IS GetPreSQL, the safe parameterized method
		} catch (SQLException e) {
			ps.close();
			throw e;
		}
		return StatementClosingResultSet.wrap(rs, ps);
	}

}
