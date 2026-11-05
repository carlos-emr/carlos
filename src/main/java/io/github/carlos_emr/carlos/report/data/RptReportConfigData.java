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
 * Created on 2005-7-22
 */
package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.carlos.commn.service.ReportConfigurationService;

import java.sql.SQLException;
import java.util.Properties;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.ReportConfigDao;
import io.github.carlos_emr.carlos.commn.model.ReportConfig;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * @author yilee18
 */
public class RptReportConfigData {

    private final ReportConfigDao dao = SpringUtils.getBean(ReportConfigDao.class);

    private final ReportConfigurationService service = SpringUtils.getBean(ReportConfigurationService.class);

    int report_id;
    String name;
    String caption;
    int order;
    String table_name;
    String save;

    // Retain the checked exception contract for legacy callers and subclasses compiled from source.
    @SuppressWarnings("java:S1130")
    public boolean insertRecordWithOrder() throws SQLException {
        ReportConfig r = configuration();
        service.insertWithOrder(r);
        order = r.getOrderNo();
        return false; // Preserve the legacy return contract (callers ignore it).
    }

    public boolean insertRecord() {
        dao.persist(configuration());
        return true;
    }

    private ReportConfig configuration() {
        ReportConfig r = new ReportConfig();
        r.setReportId(report_id);
        r.setName(name);
        r.setCaption(caption);
        r.setOrderNo(order);
        r.setTableName(table_name);
        r.setSave(save);
        return r;
    }

    public boolean deleteRecord() {
        service.deleteMatching(report_id, name, caption, table_name, save);
        return true;
    }

    public boolean updateRecordOrder(String saveAs, String reportId, String id, String newPos) {
        service.shiftAndRemove(Integer.parseInt(reportId), saveAs, Integer.parseInt(id), Integer.parseInt(newPos));
        return true;
    }

    public boolean deleteRecord(int recordId) {
        dao.remove(recordId);
        return true;
    }

    static int parseReportId(String reportId) throws SQLException {
        try {
            return Integer.parseInt(reportId);
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid report identifier", e);
        }
    }

    static String text(String value) {
        return value == null ? "" : value.trim();
    }

    // 0 - name; 1 - caption
    public Vector[] getConfigFieldName(String saveAs, String reportId) throws SQLException {
        Vector[] ret = {new Vector(), new Vector()};
        for (ReportConfig r : dao.findByReportIdAndSave(parseReportId(reportId), saveAs)) {
            String field = text(r.getName());
            if (field.matches(RptTableShadowFieldConst.fieldName)) continue;
            ret[0].add(text(r.getTableName()) + "." + field);
            ret[1].add(text(r.getCaption()).isEmpty() ? field : text(r.getCaption()));
        }
        return ret;
    }

    public Vector getConfigNameList(String saveAs, String reportId) throws SQLException {
        Vector ret = new Vector();
        for (ReportConfig r : dao.findByReportIdAndSave(parseReportId(reportId), saveAs)) {
            if (!text(r.getName()).matches(RptTableShadowFieldConst.fieldName)) {
                ret.add(text(r.getCaption()) + " |" + text(r.getName()));
            }
        }
        return ret;
    }

    public Vector getConfigObj(String saveAs, String reportId) throws SQLException {
        Vector ret = new Vector();
        for (ReportConfig r : dao.findByReportIdAndSave(parseReportId(reportId), saveAs)) {
            Properties prop = new Properties();
            prop.setProperty("name", text(r.getName()));
            prop.setProperty("caption", text(r.getCaption()));
            prop.setProperty("id", String.valueOf(r.getId()));
            prop.setProperty("order_no", String.valueOf(r.getOrderNo()));
            ret.add(prop);
        }
        return ret;
    }

    // get form..., demographic;
    public Vector getReportTableNameList(String reportId) throws SQLException {
        return new Vector(dao.findFormTableNames(parseReportId(reportId)));
    }

    // 0 - name; 1 - caption
    public Vector[] getAllFieldNameValue(String saveAs, String reportId) throws SQLException {
        Vector[] ret = new Vector[2];
        //Vector vecFieldCaption = new Vector();
        //Vector vecFieldName = new Vector();
        Vector[] vecField = getConfigFieldName(saveAs, reportId);
        //vecFieldName.addAll(vecField[0]);
        //vecFieldCaption.addAll(vecField[1]);
        ret[0] = vecField[0]; //vecFieldName;
        ret[1] = vecField[1]; //vecFieldCaption;
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

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getReport_id() {
        return report_id;
    }

    public void setReport_id(int report_id) {
        this.report_id = report_id;
    }

    public String getTable_name() {
        return table_name;
    }

    public void setTable_name(String table_name) {
        this.table_name = table_name;
    }

    public String getSave() {
        return save;
    }

    public void setSave(String save) {
        this.save = save;
    }
}
