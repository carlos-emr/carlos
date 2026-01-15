//CHECKSTYLE:OFF
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
 */


package ca.openosp;

import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;

import ca.openosp.openo.db.DBPreparedHandler;
import ca.openosp.openo.db.DBPreparedHandlerParam;
import ca.openosp.openo.util.UtilDict;

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
 * <p><strong>Note:</strong> This class uses deprecated {@link DBPreparedHandler}.
 * Consider migrating to JPA or modern database access patterns.</p>
 * 
 * @see DBPreparedHandler
 * @see UtilDict
 */
public class AppointmentMainBean {

    private DBPreparedHandler dbPH = null;
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
        if (dbPH != null) dbPH = null;

        dbSQL = new UtilDict();
        dbSQL.setDef(dbOperation);
        dbPH = new DBPreparedHandler();
    }

    /**
     * Configures the bean with both database operations and control-to-file mappings.
     * 
     * @param dbOperation 2D array of database operation definitions
     * @param controlToFile 2D array of control-to-file mapping definitions
     */
    public void doConfigure(String[][] dbOperation, String[][] controlToFile) {
        bDoConfigure = true;
        if (dbPH != null) dbPH = null;

        toFile = new UtilDict();
        toFile.setDef(controlToFile);
        dbSQL = new UtilDict();
        dbSQL.setDef(dbOperation);

        dbPH = new DBPreparedHandler();
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
            rs = dbPH.queryResults(sqlQuery);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = dbPH.queryResults(sqlQuery, aKeyword);
        }

        return rs;
    }

    public ResultSet queryResults_paged(String[] aKeyword, String dboperation, int iOffSet) throws Exception {
        String sqlQuery = null;

        ResultSet rs = null;
        if (aKeyword[0].equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = dbPH.queryResults_paged(sqlQuery, iOffSet);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = dbPH.queryResults_paged(sqlQuery, aKeyword, iOffSet);
        }

        return rs;
    }

    public ResultSet queryResults_paged(DBPreparedHandlerParam[] aKeyword, String dboperation, int iOffSet) throws Exception {
        String sqlQuery = null;

        ResultSet rs = null;
        if (aKeyword[0].getParamType().equals(DBPreparedHandlerParam.PARAM_STRING) &&
                aKeyword[0].getStringValue().equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = dbPH.queryResults_paged(sqlQuery, iOffSet);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = dbPH.queryResults_paged(sqlQuery, aKeyword, iOffSet);
        }
        return rs;
    }

    public Object[] queryResultsCaisi(String[] aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        Object[] rs = null;
        if (aKeyword[0].equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = dbPH.queryResultsCaisi(sqlQuery);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = dbPH.queryResultsCaisi(sqlQuery, aKeyword);
        }
        return rs;
    }

    public Object[] queryResultsCaisi(String aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        Object[] rs = null;
        if (aKeyword.equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = dbPH.queryResultsCaisi(sqlQuery);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = dbPH.queryResultsCaisi(sqlQuery, aKeyword);
        }
        return rs;
    }

    public Object[] queryResultsCaisi(int aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        sqlQuery = dbSQL.getDef(dboperation, "");
        return dbPH.queryResultsCaisi(sqlQuery, aKeyword);
    }

    public Object[] queryResultsCaisi(String dboperation) throws Exception {
        String sqlQuery = dbSQL.getDef(dboperation);
        return dbPH.queryResultsCaisi(sqlQuery);
    }

    public ResultSet queryResults(String aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        ResultSet rs = null;
        if (aKeyword.equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = dbPH.queryResults(sqlQuery);
        } else {
            sqlQuery = dbSQL.getDef(dboperation, "");
            rs = dbPH.queryResults(sqlQuery, aKeyword);
        }
        return rs;
    }

    public ResultSet queryResults_paged(String aKeyword, String dboperation, int iOffSet) throws Exception {
        String sqlQuery = null;
        ResultSet rs = null;
        if (aKeyword.equals("*")) {
            sqlQuery = dbSQL.getDef("search*", "");
            rs = dbPH.queryResults_paged(sqlQuery, iOffSet);
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
                rs = dbPH.queryResults_paged(sqlQuery, iOffSet);
            } else {
                rs = dbPH.queryResults_paged(sqlQuery, aKeyword, iOffSet);
            }
        }
        return rs;
    }

    public ResultSet queryResults(int aKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        ResultSet rs = null;
        sqlQuery = dbSQL.getDef(dboperation, "");
        rs = dbPH.queryResults(sqlQuery, aKeyword);
        return rs;
    }

    public ResultSet queryResults(String[] aKeyword, int[] nKeyword, String dboperation) throws Exception {
        String sqlQuery = null;
        ResultSet rs = null;
        sqlQuery = dbSQL.getDef(dboperation, "");
        rs = dbPH.queryResults(sqlQuery, aKeyword, nKeyword);
        return rs;
    }

    public ResultSet queryResults(int[] parameters, String dboperation) throws Exception {
        String sqlQuery = dbSQL.getDef(dboperation);
        return dbPH.queryResults(sqlQuery, parameters);
    }

    /* This method is called by querys that dont need to set a PreparedStatement */
    public ResultSet queryResults(String dboperation) throws Exception {
        String sqlQuery = dbSQL.getDef(dboperation);
        return dbPH.queryResults(sqlQuery);
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
