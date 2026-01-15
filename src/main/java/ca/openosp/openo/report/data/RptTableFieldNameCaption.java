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
 * Created on 2005-7-25
 */
package ca.openosp.openo.report.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.EncounterFormDao;
import ca.openosp.openo.commn.dao.ReportTableFieldCaptionDao;
import ca.openosp.openo.commn.model.EncounterForm;
import ca.openosp.openo.commn.model.ReportTableFieldCaption;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.login.DBHelp;

/**
 * @author yilee18
 */
public class RptTableFieldNameCaption {
    private static final Logger logger = MiscUtils.getLogger();
    private static EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
    private ReportTableFieldCaptionDao dao = SpringUtils.getBean(ReportTableFieldCaptionDao.class);

    String table_name;
    String name;
    String caption;
    DBHelp dbObj = new DBHelp();

    public boolean insertOrUpdateRecord() {
        boolean ret = false;
        String sql = "select id from reportTableFieldCaption where table_name = ? and name = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbConnectionFilter.getThreadLocalDbConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, table_name);
            ps.setString(2, name);
            rs = ps.executeQuery();
            if (rs.next()) {
                ret = insertRecord();
            } else {
                ret = updateRecord();
            }
        } catch (SQLException e) {
            logger.error("insertOrUpdateRecord() error", e);
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (SQLException e) { logger.error("Error closing ResultSet", e); }
            }
            if (ps != null) {
                try { ps.close(); } catch (SQLException e) { logger.error("Error closing PreparedStatement", e); }
            }
        }
        return ret;
    }


    public boolean insertRecord() {
        ReportTableFieldCaption r = new ReportTableFieldCaption();
        r.setTableName(table_name);
        r.setName(name);
        r.setCaption(caption);
        dao.persist(r);
        return true;
    }

    public boolean updateRecord() {
        for (ReportTableFieldCaption r : dao.findByTableNameAndName(table_name, name)) {
            r.setCaption(caption);
            dao.merge(r);
        }
        return true;
    }

    // combine a table meta list and caption from table reportTableFieldCaption
    public Vector getTableNameCaption(String tableName) {
        Vector ret = new Vector();
        Vector vec = getMetaNameList(tableName);
        Properties prop = getNameCaptionProp(tableName);
        String temp = "";
        String tempName = "";
        for (int i = 0; i < vec.size(); i++) {
            tempName = (String) vec.get(i);
            if (tempName.matches(RptTableShadowFieldConst.fieldName)) {

                continue;
            }
            temp = prop.getProperty(tempName, "");
            temp += " |" + tempName;
            ret.add(temp);
        }
        return ret;
    }

    public Properties getNameCaptionProp(String tableName) {
        Properties ret = new Properties();
        String sql = "select name, caption from reportTableFieldCaption where table_name = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbConnectionFilter.getThreadLocalDbConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, tableName);
            rs = ps.executeQuery();
            while (rs.next()) {
                ret.setProperty(DBHelp.getString(rs, "name"), DBHelp.getString(rs, "caption"));
            }
        } catch (SQLException e) {
            logger.error("getNameCaptionProp() error", e);
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (SQLException e) { logger.error("Error closing ResultSet", e); }
            }
            if (ps != null) {
                try { ps.close(); } catch (SQLException e) { logger.error("Error closing PreparedStatement", e); }
            }
        }
        return ret;
    }

    public Vector getMetaNameList(String tableName) {
        Vector ret = new Vector();

        // Validate table name to prevent SQL injection
        // Table names should only contain alphanumeric characters, underscores, and hyphens
        if (tableName == null || !tableName.matches("^[a-zA-Z0-9_-]+$")) {
            logger.error("Invalid table name: " + tableName);
            return ret;
        }

        // Additional validation: check against known form tables
        List<EncounterForm> forms = encounterFormDao.findAll();
        boolean isValidTable = false;
        for (EncounterForm form : forms) {
            if (tableName.equals(form.getFormTable())) {
                isValidTable = true;
                break;
            }
        }

        if (!isValidTable) {
            logger.error("Table name not found in encounterForm list: " + tableName);
            return ret;
        }

        String sql = "select * from " + tableName + " limit 1";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbConnectionFilter.getThreadLocalDbConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                ret.add(md.getColumnName(i));
            }
        } catch (SQLException e) {
            logger.error("getMetaNameList() error for table: " + tableName, e);
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (SQLException e) { logger.error("Error closing ResultSet", e); }
            }
            if (ps != null) {
                try { ps.close(); } catch (SQLException e) { logger.error("Error closing PreparedStatement", e); }
            }
        }
        return ret;
    }

    public Vector getFormTableNameList() {

        List<EncounterForm> forms = encounterFormDao.findAll();

        Vector ret = new Vector();
        for (EncounterForm encounterForm : forms) {
            ret.add(encounterForm.getFormName());
            ret.add(encounterForm.getFormTable());
        }

        return ret;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTable_name() {
        return table_name;
    }

    public void setTable_name(String table_name) {
        this.table_name = table_name;
    }
}
