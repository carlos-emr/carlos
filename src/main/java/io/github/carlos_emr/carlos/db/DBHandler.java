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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.LogSanitizer;

/**
 * @deprecated Use JPA instead, no new code should be written against this class.
 */
@Deprecated
public final class DBHandler {

    private static final Logger logger = LogManager.getLogger(DBHandler.class);

    private DBHandler() {
        // not intented for instantiation
    }

    /**
     * @deprecated This method is vulnerable to SQL injection. Use GetPreSQL with parameters or JPA instead.
     * This method now includes basic SQL injection detection as a safety measure for legacy code.
     */
    @Deprecated
    public static java.sql.ResultSet GetSQL(String SQLStatement) throws SQLException {
        return GetSQL(SQLStatement, false);
    }

    /**
     * @deprecated This method is vulnerable to SQL injection. Use GetPreSQL with parameters or JPA instead.
     * This method now includes basic SQL injection detection as a safety measure for legacy code.
     */
    @Deprecated
	public static ResultSet GetSQL(String SQLStatement, boolean updatable) throws SQLException {
		// Log warning about deprecated usage — sanitize and allow longer output for SQL migration diagnostics
		logger.warn("Deprecated GetSQL method called. SQL injection risk. Consider migrating to GetPreSQL or JPA. SQL: {}", LogSanitizer.sanitize(SQLStatement, 1000));
		
		Statement stmt;

		if (updatable) {
			stmt = DbConnectionFilter.getThreadLocalDbConnection().createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
		} else {
			stmt = DbConnectionFilter.getThreadLocalDbConnection().createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
		}

		ResultSet rs = stmt.executeQuery(SQLStatement);
		return rs;
	}

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

	public static ResultSet GetPreSQL(String sql, boolean updatable, Object... params) throws SQLException {
		PreparedStatement ps = DbConnectionFilter
			.getThreadLocalDbConnection()
			.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE,
				updatable ? ResultSet.CONCUR_UPDATABLE : ResultSet.CONCUR_READ_ONLY);
		bindParams(ps, params);
		return ps.executeQuery();
	}

}
