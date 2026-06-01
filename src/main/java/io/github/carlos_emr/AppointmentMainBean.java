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


package io.github.carlos_emr;

import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.db.DBPreparedHandlerParam;
import io.github.carlos_emr.carlos.util.UtilDict;

/**
 * Main bean for managing appointment-related database operations and configurations.
 * 
 * <p>This bean provides functionality for:</p>
 * <ul>
 *   <li>Configuring database operations with SQL statements</li>
 *   <li>Managing control-to-file mappings for form handling</li>
 *   <li>Processing HTTP request parameters for appointments</li>
 *   <li>Executing prepared database statements</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This class still exposes legacy {@link ResultSet}-based
 * query methods for JSP compatibility. Prefer typed DAOs/services for new code.</p>
 * 
 * @see LegacyJdbcQuery
 * @see UtilDict
 */
public class AppointmentMainBean {
    // Design consideration: Explicit field preservation ensures structural compatibility with downstream reporting systems.

    private static final DBPreparedHandlerParam[] EMPTY_DB_PARAMS = new DBPreparedHandlerParam[0];

    private UtilDict toFile = null;
    private UtilDict dbSQL = null;
    private UtilDict requestUtilDict = null;
    private String targetType = null;
    private boolean bDoConfigure = false;

    /**
     * Gets the target control-to-file mappings.
     * 
     * @return UtilDict containing the control-to-file mappings
     */
    public UtilDict getTargets() {
        return toFile;
    }

    /**
     * Gets the utility dictionary containing processed request parameters.
     * 
     * @return UtilDict with request parameter mappings
     */
    public UtilDict getRequestUtilDict() {
        return requestUtilDict;
    }

    /**
     * Checks if the bean has been configured.
     * 
     * @return true if configured, false otherwise
     */
    public boolean getBDoConfigure() {
        return bDoConfigure;
    }

    /**
     * Resets the configuration state to false.
     */
    public void setBDoConfigure() {
        bDoConfigure = false;
    }

    /**
     * Configures the bean with database operations only.
     * 
     * @param dbOperation 2D array of database operation definitions
     */
    public void doConfigure(String[][] dbOperation) {
        bDoConfigure = true;
        dbSQL = new UtilDict();
        dbSQL.setDef(dbOperation);
    }

    /**
     * Configures the bean with both database operations and control-to-file mappings.
     * 
     * @param dbOperation 2D array of database operation definitions
     * @param controlToFile 2D array of control-to-file mapping definitions
     */
    public void doConfigure(String[][] dbOperation, String[][] controlToFile) {
        bDoConfigure = true;
        toFile = new UtilDict();
        toFile.setDef(controlToFile);
        dbSQL = new UtilDict();
        dbSQL.setDef(dbOperation);

    }

    public void doCommand(HttpServletRequest request) {
        //wrap the request object into a Dict help object
        requestUtilDict = new UtilDict();
        requestUtilDict.setDef(request);
    }

    public String whereTo() {
        targetType = requestUtilDict.getDef("displaymode", "");
        return toFile.getDef(targetType, "");
    }

    public String whereTo(String displaymode) {
        targetType = requestUtilDict.getDef(displaymode, "");
        return toFile.getDef(targetType, "");
    }

    public ResultSet queryResults(String[] aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;

        ResultSet rs = null;
        if (aKeyword[0].equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            // SQL from UtilDict constants is server-owned; empty parameters are part of an established template path.
            rs = LegacyJdbcQuery.queryResults(sqlQuery, EMPTY_DB_PARAMS);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = LegacyJdbcQuery.queryResults(sqlQuery, aKeyword);
        }

        return rs;
    }

    public ResultSet queryResults_paged(String[] aKeyword, String dboperation, int iOffSet) throws Exception {
        String sqlQuery = null;

        ResultSet rs = null;
        if (aKeyword[0].equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = LegacyJdbcQuery.queryResultsPaged(sqlQuery, EMPTY_DB_PARAMS, iOffSet);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = LegacyJdbcQuery.queryResultsPaged(sqlQuery, aKeyword, iOffSet);
        }

        return rs;
    }

    public ResultSet queryResults_paged(DBPreparedHandlerParam[] aKeyword, String dboperation, int iOffSet) throws Exception {
        String sqlQuery = null;

        ResultSet rs = null;
        if (aKeyword[0].getParamType().equals(DBPreparedHandlerParam.PARAM_STRING) &&
                aKeyword[0].getStringValue().equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = LegacyJdbcQuery.queryResultsPaged(sqlQuery, EMPTY_DB_PARAMS, iOffSet);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = LegacyJdbcQuery.queryResultsPaged(sqlQuery, aKeyword, iOffSet);
        }
        return rs;
    }

    /**
     * Runs a CAISI query using string parameters.
     *
     * <p>When the first keyword is {@code "*"}, the {@code search*} query is used
     * and no keyword parameters are bound. Otherwise {@code dboperation} selects
     * the query definition and {@code aKeyword} supplies the bound parameters.</p>
     *
     * @param aKeyword keyword parameters, or {@code "*"} as the first entry for the wildcard query
     * @param dboperation query definition key for non-wildcard lookups
     * @return a closable result wrapper; callers own it and must close it
     * @throws Exception if the query definition cannot be resolved or JDBC execution fails
     */
    public LegacyJdbcQuery.CaisiResult queryResultsCaisi(String[] aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        LegacyJdbcQuery.CaisiResult rs = null;
        if (aKeyword[0].equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = LegacyJdbcQuery.queryResultsCaisi(sqlQuery, new String[0]);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = LegacyJdbcQuery.queryResultsCaisi(sqlQuery, aKeyword);
        }
        return rs;
    }

    /**
     * Runs a CAISI query using a single string parameter.
     *
     * <p>A keyword of {@code "*"} uses the {@code search*} query and binds no
     * keyword parameter. Other values use {@code dboperation} and bind
     * {@code aKeyword} as the query parameter.</p>
     *
     * @param aKeyword keyword value, or {@code "*"} for the wildcard query
     * @param dboperation query definition key for non-wildcard lookups
     * @return a closable result wrapper; callers own it and must close it
     * @throws Exception if the query definition cannot be resolved or JDBC execution fails
     */
    public LegacyJdbcQuery.CaisiResult queryResultsCaisi(String aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        LegacyJdbcQuery.CaisiResult rs = null;
        if (aKeyword.equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = LegacyJdbcQuery.queryResultsCaisi(sqlQuery, new String[0]);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = LegacyJdbcQuery.queryResultsCaisi(sqlQuery, aKeyword);
        }
        return rs;
    }

    /**
     * Runs a CAISI query using a single integer parameter.
     *
     * @param aKeyword integer keyword value to bind to the query
     * @param dboperation query definition key
     * @return a closable result wrapper; callers own it and must close it
     * @throws Exception if the query definition cannot be resolved or JDBC execution fails
     */
    public LegacyJdbcQuery.CaisiResult queryResultsCaisi(int aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        sqlQuery = dbSQL.getDef(dboperation, "");
        return LegacyJdbcQuery.queryResultsCaisi(sqlQuery, aKeyword);
    }

    /**
     * Runs a CAISI query with no keyword parameters.
     *
     * @param dboperation query definition key
     * @return a closable result wrapper; callers own it and must close it
     * @throws Exception if the query definition cannot be resolved or JDBC execution fails
     */
    public LegacyJdbcQuery.CaisiResult queryResultsCaisi(String dboperation) throws Exception {
        String sqlQuery = dbSQL.getDef(dboperation);
        return LegacyJdbcQuery.queryResultsCaisi(sqlQuery, new String[0]);
    }

    public ResultSet queryResults(String aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        ResultSet rs = null;
        if (aKeyword.equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = LegacyJdbcQuery.queryResults(sqlQuery, EMPTY_DB_PARAMS);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = LegacyJdbcQuery.queryResults(sqlQuery, aKeyword);
        }
        return rs;
    }

    public ResultSet queryResults_paged(String aKeyword, String dboperation, int iOffSet) throws Exception {
        String sqlQuery = null;
        ResultSet rs = null;
        if (aKeyword.equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = LegacyJdbcQuery.queryResultsPaged(sqlQuery, EMPTY_DB_PARAMS, iOffSet);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            //works with only one " like ?"
            if (aKeyword.length() < 1) {
                int iIndex1 = sqlQuery.indexOf("like");
                if (iIndex1 > 0) {
                    String str1 = sqlQuery.substring(0, iIndex1 - 1).trim();
                    String str2 = str1.substring(0, str1.lastIndexOf(" "));
                    String str3 = sqlQuery.substring(iIndex1 + 5, sqlQuery.length());
                    int iIndex2 = str3.indexOf("?");
//            if(str3.indexOf("and")>iIndex2) iIndex2=str3.indexOf("and") + 3;
                    sqlQuery = str2 + " 1=1 " + str3.substring(iIndex2 + 1, str3.length());
                }
                rs = LegacyJdbcQuery.queryResultsPaged(sqlQuery, EMPTY_DB_PARAMS, iOffSet);
            } else {
                rs = LegacyJdbcQuery.queryResultsPaged(sqlQuery, aKeyword, iOffSet);
            }
        }
        return rs;
    }

    public ResultSet queryResults(int aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        ResultSet rs = null;
        sqlQuery = dbSQL.getDef(dboperation, "");
        rs = LegacyJdbcQuery.queryResults(sqlQuery, aKeyword);
        return rs;
    }

    public ResultSet queryResults(String[] aKeyword, int[] nKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        ResultSet rs = null;
        sqlQuery = dbSQL.getDef(dboperation, "");
        rs = LegacyJdbcQuery.queryResults(sqlQuery, aKeyword, nKeyword);
        return rs;
    }

    public ResultSet queryResults(int[] parameters, String dboperation) throws Exception {
        String sqlQuery = dbSQL.getDef(dboperation);
        return LegacyJdbcQuery.queryResults(sqlQuery, parameters);
    }

    /* This method is called by querys that dont need to set a PreparedStatement */
    public ResultSet queryResults(String dboperation) throws Exception {
        String sqlQuery = dbSQL.getDef(dboperation);
        return LegacyJdbcQuery.queryResults(sqlQuery, EMPTY_DB_PARAMS);
    }

    public String getString(ResultSet rs, java.lang.String columnName) throws SQLException {
        return Misc.getString(rs, columnName);
    }

    public String getString(ResultSet rs, int columnIndex) throws SQLException {
        return Misc.getString(rs, columnIndex);
    }

    public String getString(Object o) {
        if (o == null) return "";
        return (String) o;
    }
}
