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
 * Created on 2005-7-20
 */
package io.github.carlos_emr.carlos.report.data;

import java.sql.SQLException;
import java.util.Properties;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.ReportItemDao;
import io.github.carlos_emr.carlos.commn.model.ReportItem;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * @author yilee18
 */
public class RptReportItem {
    String report_name;
    int status = 1;
    private final ReportItemDao dao = SpringUtils.getBean(ReportItemDao.class);


    public boolean insertRecord() {
        ReportItem r = new ReportItem();
        r.setReportName(report_name);
        r.setStatus(status);
        dao.persist(r);
        return true;
    }

    public boolean deleteRecord(int recordId) {
        ReportItem r = dao.find(recordId);
        if (r != null) {
            r.setStatus(0);
            dao.merge(r);
        }
        return true;
    }

    public boolean unDeleteRecord(int recordId) {
        ReportItem r = dao.find(recordId);
        if (r != null) {
            r.setStatus(1);
            dao.merge(r);
        }
        return true;
    }

    // id
    public String getReportName(String recordId) throws SQLException {
        ReportItem r = dao.find(RptReportConfigData.parseReportId(recordId));
        return r == null ? null : RptReportConfigData.text(r.getReportName());
    }

    // 1 - name list, 0 - deleted name list
    // Retain the checked exception contract for legacy callers and subclasses compiled from source.
    @SuppressWarnings("java:S1130")
    public Vector getNameList(int n) throws SQLException {
        Vector ret = new Vector();
        for (ReportItem r : dao.findByStatus(n)) {
            Properties prop = new Properties();
            prop.setProperty("id", String.valueOf(r.getId()));
            prop.setProperty(String.valueOf(r.getId()), RptReportConfigData.text(r.getReportName()));
            ret.add(prop);
        }
        return ret;
    }

    public String getReport_name() {
        return report_name;
    }

    public void setReport_name(String report_name) {
        this.report_name = report_name;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
