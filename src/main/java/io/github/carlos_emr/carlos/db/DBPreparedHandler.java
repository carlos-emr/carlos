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

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @deprecated Use JPA (for example, {@code EntityManager#createNativeQuery(...)}) instead;
 * no new code should be written against this class. Scheduled for removal once
 * remaining callers migrate.
 */
@Deprecated(forRemoval = true)
public final class DBPreparedHandler {

    ResultSet rs = null;
    Statement stmt = null;
    PreparedStatement preparedStmt = null;

    /**
     * Validates that a stored-procedure name contains only safe identifier characters
     * (letters, digits, underscores, dots) to prevent SQL injection when the name is
     * interpolated into the JDBC escape syntax <code>{call procName(...)}</code>.
     * Stored-procedure names cannot be parameterized in JDBC, so an allowlist character
     * check is the appropriate defence.
     *
     * @param procName the caller-supplied procedure name
     * @throws SQLException if the name is null, empty, or contains characters outside
     *         the allowed set {@code [a-zA-Z0-9_.]}
     */
    private static void validateProcName(String procName) throws SQLException {
        if (procName == null || procName.isEmpty()) {
            throw new SQLException("Stored procedure name must not be null or empty");
        }
        if (!procName.matches("[a-zA-Z0-9_.]+")) {
            throw new SQLException(
                    "Stored procedure name contains invalid characters; only letters, digits, underscores and dots are allowed");
        }
    }

    synchronized public void procExecute(String procName, String[] param) throws SQLException {
        // Validate the procedure name to prevent injection via JDBC escape syntax.
        validateProcName(procName);
        String sql = "{call " + procName;
        if (param != null && param.length > 0) {
            String prms = "";
            for (int i = 0; i < param.length; i++) {
                prms += "?,";
            }
            if (!prms.equals("")) sql += "(" + prms.substring(0, prms.length() - 1) + ")";
        }

        sql += "}";
        CallableStatement stmt = DbConnectionFilter.getThreadLocalDbConnection().prepareCall(sql);
        if (param != null && param.length > 0) {
            for (int i = 0; i < param.length; i++) {
                stmt.setString((i + 1), param[i]);

            }
        }
        stmt.execute();
    }

    synchronized public ResultSet queryResults(String preparedSQL, String[] param, int[] intparam) throws SQLException {
        int i = 0;
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        for (i = 0; i < param.length; i++) {
            preparedStmt.setString((i + 1), param[i]);
        }
        for (i = 0; i < intparam.length; i++) {
            preparedStmt.setInt((param.length + i + 1), intparam[i]);
        }
        rs = preparedStmt.executeQuery();
        return rs;
    }

    synchronized public ResultSet queryResults(String preparedSQL, int param) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        preparedStmt.setInt(1, param);
        rs = preparedStmt.executeQuery();
        return rs;
    }

    synchronized public ResultSet queryResults(String preparedSQL, int[] param) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        for (int i = 0; i < param.length; i++) {
            preparedStmt.setInt((i + 1), param[i]);
        }
        return (preparedStmt.executeQuery());
    }

    synchronized public ResultSet queryResults(String preparedSQL, String param) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        preparedStmt.setString(1, param);
        rs = preparedStmt.executeQuery();
        return rs;
    }

    synchronized public ResultSet queryResults_paged(String preparedSQL, String param, int iOffSet) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        preparedStmt.setString(1, param);
        rs = preparedStmt.executeQuery();
        for (int i = 1; i <= iOffSet; i++) {
            if (rs.next() == false) break;
        }
        return rs;
    }

    synchronized public ResultSet queryResults(String preparedSQL, String[] param) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        for (int i = 0; i < param.length; i++) {
            preparedStmt.setString((i + 1), param[i]);
        }
        rs = preparedStmt.executeQuery();
        return (rs);
    }

    synchronized public ResultSet queryResults_paged(String preparedSQL, String[] param, int iOffSet) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        for (int i = 0; i < param.length; i++) {
            preparedStmt.setString((i + 1), param[i]);
        }
        rs = preparedStmt.executeQuery();
        for (int i = 1; i <= iOffSet; i++) {
            if (rs.next() == false) break;
        }
        return (rs);
    }

    synchronized public ResultSet queryResults(String preparedSQL, DBPreparedHandlerParam[] param) throws SQLException { // nosemgrep: formatted-sql-string -- parameterized query infrastructure; params are bound via PreparedStatement
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL); // codeql[java/sql-injection] — parameterized infrastructure; params bound via setString/setDate/setInt below
        for (int i = 0; i < param.length; i++) {
            if (param[i].getParamType().equals(DBPreparedHandlerParam.PARAM_STRING)) {
                preparedStmt.setString((i + 1), param[i].getStringValue());
            } else if (param[i].getParamType().equals(DBPreparedHandlerParam.PARAM_DATE)) {
                preparedStmt.setDate((i + 1), param[i].getDateValue());
            } else if (param[i].getParamType().equals(DBPreparedHandlerParam.PARAM_INT)) {
                preparedStmt.setInt((i + 1), param[i].getIntValue());
            }
        }
        rs = preparedStmt.executeQuery();
        return (rs);
    }

    synchronized public ResultSet queryResults_paged(String preparedSQL, DBPreparedHandlerParam[] param, int iOffSet) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        for (int i = 0; i < param.length; i++) {
            if (param[i].getParamType().equals(DBPreparedHandlerParam.PARAM_STRING)) {
                preparedStmt.setString((i + 1), param[i].getStringValue());
            } else if (param[i].getParamType().equals(DBPreparedHandlerParam.PARAM_DATE)) {
                preparedStmt.setDate((i + 1), param[i].getDateValue());
            } else if (param[i].getParamType().equals(DBPreparedHandlerParam.PARAM_INT)) {
                preparedStmt.setInt((i + 1), param[i].getIntValue());
            }
        }
        rs = preparedStmt.executeQuery();
        for (int i = 1; i <= iOffSet; i++) {
            if (rs.next() == false) break;
        }
        return (rs);
    }

    synchronized public Object[] queryResultsCaisi(String preparedSQL, int param) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        preparedStmt.setInt(1, param);
        rs = preparedStmt.executeQuery();
        return new Object[]{rs, preparedStmt};
    }

    synchronized public Object[] queryResultsCaisi(String preparedSQL, String param) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        preparedStmt.setString(1, param);
        rs = preparedStmt.executeQuery();
        return new Object[]{rs, preparedStmt};
    }

    synchronized public Object[] queryResultsCaisi(String preparedSQL, String[] param) throws SQLException {
        preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        for (int i = 0; i < param.length; i++) {
            preparedStmt.setString((i + 1), param[i]);
        }
        rs = preparedStmt.executeQuery();
        return new Object[]{rs, preparedStmt};
    }

    // queryResultsCaisi(String) removed — all callers migrated to parameterized overloads.

    /**
     * Defense-in-depth guard for the admin report SQL execution path.
     * Restricts to SELECT-only and blocks known injection patterns including
     * UNION-based injection, DDL statements, and file operations.
     *
     * <p>This method is retained solely for the admin "query-by-example" report
     * feature ({@code RptByExampleData}). All other callers should use the
     * parameterized overloads.</p>
     */
    private void validateSafeSelectQuery(String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("SQL query must not be empty");
        }

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("select")) {
            throw new SQLException("Only SELECT statements are allowed");
        }

        if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new SQLException("Unsafe SQL detected: comment or statement separator");
        }

        if (normalized.contains("' or '") || normalized.contains("\" or \"") || normalized.contains(" or 1=1")) {
            throw new SQLException("Potential SQL injection pattern detected");
        }

        // Block UNION-based injection (most common bypass of older denylist)
        if (normalized.matches(".*\\bunion\\b.*")) {
            throw new SQLException("Unsafe SQL detected: UNION not permitted");
        }

        // Block DDL and DML keywords that should never appear in a reporting SELECT.
        // Uses word boundary patterns to avoid false positives on column names like "last_update".
        String[] blockedPatterns = {"\\binsert\\b", "\\bupdate\\b", "\\bdelete\\b", "\\bdrop\\b",
                "\\balter\\b", "\\bcreate\\b", "\\btruncate\\b", "\\bgrant\\b", "\\brevoke\\b",
                "\\bexec\\b", "\\bexecute\\b",
                "into outfile", "into dumpfile", "load_file", "load data"};
        for (String pattern : blockedPatterns) {
            if (normalized.matches(".*" + pattern + ".*")) {
                throw new SQLException("Unsafe SQL detected: prohibited keyword");
            }
        }
    }

    /**
     * Executes a dynamic SQL SELECT query with denylist-based validation.
     * <p><strong>Sole authorized caller: {@code RptByExampleData}</strong> (admin report tool).
     * All other code must use the parameterized overloads. Do not add new callers.</p>
     */
    synchronized public ResultSet queryResults(String preparedSQL) throws SQLException { // nosemgrep: formatted-sql-string — sole caller is RptByExampleData (admin report); validated by validateSafeSelectQuery denylist
        validateSafeSelectQuery(preparedSQL);
        stmt = DbConnectionFilter.getThreadLocalDbConnection().createStatement();
        rs = stmt.executeQuery(preparedSQL); // codeql[java/sql-injection] — admin-only dynamic SQL; validated by validateSafeSelectQuery
        return rs;
    }

    // queryResults_paged(String, int) removed — all callers migrated to parameterized overloads.

    public synchronized String getNewProviderNo() {
        try {
            String pno = Misc.getRandomNumber(6);
            String sql = "select count(*) from provider where provider_no= ?";
            while (true) {
                try (ResultSet rs = queryResults(sql, pno)) {
                    if (!rs.next() || rs.getInt(1) == 0) {
                        return pno;
                    }
                } finally {
                    if (preparedStmt != null) {
                        try {
                            preparedStmt.close();
                        } catch (SQLException ex) {
                            MiscUtils.getLogger().error("Failed to close provider number check statement", ex);
                        } finally {
                            preparedStmt = null;
                        }
                    }
                }

                do {
                    pno = Misc.getRandomNumber(6);
                } while (pno != null && pno.startsWith("0"));
            }
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Failed to generate new provider number", ex);
            return "";
        }
    }

}
