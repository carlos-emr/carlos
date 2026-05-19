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

import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @deprecated Use JPA (for example, {@code EntityManager#createNativeQuery(...)}) instead;
 * no new code should be written against this class. Scheduled for removal once
 * remaining callers migrate.
 */
@Deprecated(forRemoval = true)
public final class DBPreparedHandler {

    ResultSet rs = null;
    synchronized public void procExecute(String procName, String[] param) throws SQLException {
        LegacyJdbcQuery.procExecute(procName, param);
    }

    synchronized public ResultSet queryResults(String preparedSQL, String[] param, int[] intparam) throws SQLException {
        rs = LegacyJdbcQuery.queryResults(preparedSQL, param, intparam);
        return rs;
    }

    synchronized public ResultSet queryResults(String preparedSQL, int param) throws SQLException {
        rs = LegacyJdbcQuery.queryResults(preparedSQL, param);
        return rs;
    }

    synchronized public ResultSet queryResults(String preparedSQL, int[] param) throws SQLException {
        return LegacyJdbcQuery.queryResults(preparedSQL, param);
    }

    synchronized public ResultSet queryResults(String preparedSQL, String param) throws SQLException {
        rs = LegacyJdbcQuery.queryResults(preparedSQL, param);
        return rs;
    }

    synchronized public ResultSet queryResults_paged(String preparedSQL, String param, int iOffSet) throws SQLException {
        rs = LegacyJdbcQuery.queryResultsPaged(preparedSQL, param, iOffSet);
        return rs;
    }

    synchronized public ResultSet queryResults(String preparedSQL, String[] param) throws SQLException {
        rs = LegacyJdbcQuery.queryResults(preparedSQL, param);
        return (rs);
    }

    synchronized public ResultSet queryResults_paged(String preparedSQL, String[] param, int iOffSet) throws SQLException {
        rs = LegacyJdbcQuery.queryResultsPaged(preparedSQL, param, iOffSet);
        return (rs);
    }

    synchronized public ResultSet queryResults(String preparedSQL, DBPreparedHandlerParam[] param) throws SQLException { // nosemgrep: formatted-sql-string -- parameterized query infrastructure; params are bound via PreparedStatement
        rs = LegacyJdbcQuery.queryResults(preparedSQL, param);
        return (rs);
    }

    synchronized public ResultSet queryResults_paged(String preparedSQL, DBPreparedHandlerParam[] param, int iOffSet) throws SQLException {
        rs = LegacyJdbcQuery.queryResultsPaged(preparedSQL, param, iOffSet);
        return (rs);
    }

    synchronized public Object[] queryResultsCaisi(String preparedSQL, int param) throws SQLException {
        return LegacyJdbcQuery.queryResultsCaisi(preparedSQL, param);
    }

    synchronized public Object[] queryResultsCaisi(String preparedSQL, String param) throws SQLException {
        return LegacyJdbcQuery.queryResultsCaisi(preparedSQL, param);
    }

    synchronized public Object[] queryResultsCaisi(String preparedSQL, String[] param) throws SQLException {
        return LegacyJdbcQuery.queryResultsCaisi(preparedSQL, param);
    }

    // queryResultsCaisi(String) removed — all callers migrated to parameterized overloads.

    /**
     * Executes a dynamic SQL SELECT query with denylist-based validation.
     * <p><strong>Sole authorized caller: {@code RptByExampleData}</strong> (admin report tool).
     * All other code must use the parameterized overloads. Do not add new callers.</p>
     */
    synchronized public ResultSet queryResults(String preparedSQL) throws SQLException { // nosemgrep: formatted-sql-string — sole caller is RptByExampleData (admin report); validated by validateSafeSelectQuery denylist
        rs = LegacyJdbcQuery.queryResults(LegacyJdbcQuery.trustedSelectSql(preparedSQL));
        return rs;
    }

    // queryResults_paged(String, int) removed — all callers migrated to parameterized overloads.

    public synchronized String getNewProviderNo() {
        try {
            String pno = Misc.getRandomNumber(6);
            String sql = "select count(*) from provider where provider_no= ?";
            ResultSet rs = queryResults(sql, pno);
            while (rs.next()) {
                if (rs.getInt(1) > 0) {
                    do {
                        pno = Misc.getRandomNumber(6);
                    } while (pno != null && pno.startsWith("0"));
                    sql = "select count(*) from provider where provider_no= ?";
                    rs = queryResults(sql, pno);
                }
            }
            return pno;
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Failed to generate new provider number", ex);
            return "";
        }
    }

}
