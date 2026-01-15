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


/*
 * Created on 2005-8-1
 */
package ca.openosp.openo.report.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Vector;

import org.apache.commons.lang3.time.DateFormatUtils;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.login.DBHelp;

/**
 * @author yilee18
 */
public final class RptReportCreator {
    DBHelp dbObj = new DBHelp();

    // select formBCAR.pg1_ethOrig as Ethnic Origin, ...
    public String getSelectField(String recordId) throws SQLException {
        StringBuilder ret = new StringBuilder();
        String sql = "select * from reportConfig where report_id = " + recordId + " order by order_no";
        ResultSet rs = DBHelp.searchDBRecord(sql);
        while (rs.next()) {
            String caption = DBHelp.getString(rs, "caption");
            ret.append((ret.length() < 8 ? " " : ", ") + DBHelp.getString(rs, "table_name") + "." + DBHelp.getString(rs, "name"));
            if (caption != null && caption.length() > 0) {
                ret.append(" as '" + DBHelp.getString(rs, "caption") + "'");
            }
        }
        rs.close();
        return ret.toString();
    }

    // from formBCAR
    public String getFromTableFirst(String recordId) throws SQLException {
        String ret = "  ";
        String sql = "select distinct table_name from reportConfig where report_id = " + recordId
                + " order by table_name desc";
        ResultSet rs = DBHelp.searchDBRecord(sql);
        if (rs.next()) {
            ret = DBHelp.getString(rs, "table_name");
        }
        rs.close();
        return ret;
    }

    // from formBCAR, demographic
    public String getFromTable(String recordId) throws SQLException {
        String ret = "  ";
        Vector vec = new Vector();
        String sql = "select distinct table_name from reportConfig where report_id = " + recordId
                + " order by table_name desc";
        ResultSet rs = DBHelp.searchDBRecord(sql);
        while (rs.next()) {
            vec.add(DBHelp.getString(rs, "table_name"));
        }
        rs.close();
        for (int i = 0; i < vec.size(); i++) {
            ret += (i == 0 ? "" : ",") + vec.get(i);
        }
        return ret;
    }

    // tableName: formBCAR,formBCNewBorn... how to handle??
    public String getWhereJoinClause(String tableName, boolean bDemo) {
        String ret = "";
        if (bDemo)
            ret = tableName + ".demographic_no=demographic.demographic_no";
        return ret;
    }

    // Replace the result one by one if not null
    public static String getWhereValueClause(String value, Vector vec) {
        String ret = "";
        for (int i = 0; i < 100; i++) {
            // Use indexOf to check for template variables to avoid potential ReDoS
            int startIdx = value.indexOf("${");
            if (startIdx >= 0) {
                int endIdx = value.indexOf("}", startIdx);
                if (endIdx > startIdx + 2) {
                    // Found a complete ${...} pattern
                    String replacement = (i < vec.size() && vec.get(i) != null) ? (String) vec.get(i) : "";
                    value = value.substring(0, startIdx) + replacement + value.substring(endIdx + 1);
                } else {
                    ret = value;
                    break;
                }
            } else {
                ret = value;
                break;
            }
        }
        return ret;
    }

    public static boolean isIncludeDemo(String value) {
        boolean ret = false;
        if (value.indexOf("demographic.") >= 0)
            ret = true;
        return ret;
    }

    // get ${var} vars inside the string
    public static Vector getVarVec(String value) {
        Vector ret = new Vector();
        
        // Quick exit - no templates possible
        if (!value.contains("${")) {
            return ret;
        }
        
        int pos = 0;
        while (pos < value.length()) {
            int startIdx = value.indexOf("${", pos);
            if (startIdx == -1) break;
            
            int endIdx = value.indexOf("}", startIdx + 2);
            if (endIdx == -1) break;
            
            String varName = value.substring(startIdx + 2, endIdx);
            if (!varName.isEmpty()) {
                ret.add(varName);
            }
            
            pos = endIdx + 1;
        }

        return ret;
    }

    // change date string
    public static String getDiffDateFormat(String strDate, String oDate, String nDate) throws Exception {
        String ret = strDate;
        if (strDate.length() >= oDate.length()) {
            Date a = (new SimpleDateFormat(oDate)).parse(strDate);
            ret = DateFormatUtils.format(a, nDate);
            //ret = DateFormatUtils.format(DateUtils.parseDate(strDate, new String[] { oDate }),
            // nDate);
        } else {
            MiscUtils.getLogger().debug(" getDate wrong!!!");
        }
        return ret;
    }

    // get result of a SubQuery in ,,,,,,
    public String getRltSubQuery(String sql) throws SQLException {
        String ret = "0";

        ResultSet rs = DBHelp.searchDBRecord(sql);
        MiscUtils.getLogger().debug(" tempVal: " + sql);
        while (rs.next()) {
            if ("0".equals(ret)) {
                ret = "";
            }
            ret += ("".equals(ret) ? "" : ",") + rs.getInt(1);

        }
        rs.close();
        return ret;
    }

    // from formBCAR, demographic
    public Vector query(String sql, Vector vecFieldName) throws SQLException {
        Vector ret = new Vector();
        Properties prop = null;

        ResultSet rs = DBHelp.searchDBRecord(sql);
        while (rs.next()) {
            prop = new Properties();
            for (int i = 0; i < vecFieldName.size(); i++) {
                try {
                    prop.setProperty((String) vecFieldName.get(i),
                            DBHelp.getString(rs, (String) vecFieldName.get(i)) == null ? "" : rs
                                    .getString((String) vecFieldName.get(i)));
                } catch (SQLException e) {
                    prop.setProperty((String) vecFieldName.get(i), "" + rs.getInt((String) vecFieldName.get(i)));
                }
            }
            ret.add(prop);
        }
        rs.close();
        return ret;
    }

}
