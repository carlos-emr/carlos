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


/*
 * Created on 2005-5-19
 *
 */
package io.github.carlos_emr.carlos.login;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.carlos_emr.Misc;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.db.DBHandler;

/**
 * Legacy database helper that provides direct SQL query execution via {@link DBHandler}.
 *
 * @deprecated Use JPA/Hibernate DAOs instead. No new code should be written against this class.
 * @since 2026-03-17
 */
@Deprecated
public final class DBHelp {
    private static final Logger logger = MiscUtils.getLogger();


    /**
     * Executes a SQL query and returns the result set.
     *
     * @param sql String the SQL query to execute
     * @return ResultSet the query results, or null if a SQL error occurs
     * @deprecated Use JPA/Hibernate DAOs instead
     */
    public static ResultSet searchDBRecord(String sql) {
        ResultSet ret = null;
        try {

            ret = DBHandler.GetSQL(sql);
        } catch (SQLException e) {
            logger.error("Error", e);
        }

        return ret;
    }

    /**
     * Retrieves a String value from a ResultSet by column name.
     *
     * @param rs ResultSet the result set to read from
     * @param columnName String the name of the column
     * @return String the column value
     * @throws SQLException if a database access error occurs
     * @deprecated Use JPA/Hibernate entity mappings instead
     */
    public static String getString(ResultSet rs, String columnName) throws SQLException {
        return Misc.getString(rs, columnName);
    }

    /**
     * Retrieves a String value from a ResultSet by column index.
     *
     * @param rs ResultSet the result set to read from
     * @param columnIndex int the 1-based column index
     * @return String the column value
     * @throws SQLException if a database access error occurs
     * @deprecated Use JPA/Hibernate entity mappings instead
     */
    public static String getString(ResultSet rs, int columnIndex) throws SQLException {
        return Misc.getString(rs, columnIndex);
    }
}
