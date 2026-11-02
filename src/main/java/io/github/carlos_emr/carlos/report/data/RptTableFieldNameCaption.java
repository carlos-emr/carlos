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
 * Created on 2005-7-25
 */
package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.carlos.commn.service.ReportConfigurationService;

import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.ReportTableFieldCaptionDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.commn.model.ReportTableFieldCaption;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * @author yilee18
 */
public class RptTableFieldNameCaption {
    private static final Logger logger = MiscUtils.getLogger();
    private static EncounterFormDao encounterFormDao;
    private ReportTableFieldCaptionDao dao;

    String table_name;
    String name;
    String caption;

    public boolean insertOrUpdateRecord() {
        io.github.carlos_emr.carlos.utility.SpringUtils.getBean(
                ReportConfigurationService.class)
                .saveCaption(table_name, name, caption);
        return true;
    }

    protected boolean recordExists() throws SQLException {
        return !dao().findByTableNameAndName(table_name, name).isEmpty();
    }


    public boolean insertRecord() {
        ReportTableFieldCaption r = new ReportTableFieldCaption();
        r.setTableName(table_name);
        r.setName(name);
        r.setCaption(caption);
        dao().persist(r);
        return true;
    }

    public boolean updateRecord() {
        io.github.carlos_emr.carlos.utility.SpringUtils.getBean(
                ReportConfigurationService.class)
                .updateCaption(table_name, name, caption);
        return true;
    }

    private static EncounterFormDao encounterFormDao() {
        if (encounterFormDao == null) {
            encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
        }
        return encounterFormDao;
    }

    private ReportTableFieldCaptionDao dao() {
        if (dao == null) {
            dao = SpringUtils.getBean(ReportTableFieldCaptionDao.class);
        }
        return dao;
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
        try {
            for (ReportTableFieldCaption r : dao().findByTableName(tableName)) {
                ret.setProperty(RptReportConfigData.text(r.getName()), RptReportConfigData.text(r.getCaption()));
            }
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException e) {
            logger.error("getNameCaptionProp() database read failed", e);
        }
        return ret;
    }

    public Vector getMetaNameList(String tableName) {
        Vector ret = new Vector();
        String trustedTableName = validateEncounterFormTableName(tableName);
        if (trustedTableName == null) {
            return ret;
        }

        String sql = metaNameListSql(trustedTableName);
        try {
            ret.addAll(ConfiguredReportQuery.read(sql).columnNames());
        } catch (SQLException e) {
            logger.error("getMetaNameList() error for table: {}", LogSafe.sanitize(tableName), e);
        }
        return ret;
    }

    private String validateEncounterFormTableName(String tableName) {
        // Validate table name to prevent SQL injection.
        // Table names are interpolated as identifiers, so only bare identifier characters are allowed.
        if (tableName == null || !tableName.matches("^\\w+$")) {
            if (logger.isErrorEnabled()) {
                logger.error("Invalid table name: {}", LogSafe.sanitize(tableName));
            }
            return null;
        }

        // Additional validation: check against known form tables
        List<EncounterForm> forms = encounterFormDao().findAll();
        boolean isValidTable = false;
        for (EncounterForm form : forms) {
            if (tableName.equals(form.getFormTable())) {
                isValidTable = true;
                break;
            }
        }

        if (!isValidTable) {
            if (logger.isErrorEnabled()) {
                logger.error("Table name not found in encounterForm list: {}", LogSafe.sanitize(tableName));
            }
            return null;
        }
        return tableName;
    }

    private String metaNameListSql(String trustedTableName) {
        // Table identifiers cannot be JDBC-bound. trustedTableName has passed
        // validateEncounterFormTableName(), including the bare-identifier check
        // and encounterForm allowlist.
        return "select * from " + trustedTableName + " limit 1";
    }

    public Vector getFormTableNameList() {

        List<EncounterForm> forms = encounterFormDao().findAll();

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
