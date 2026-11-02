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
 * Created on 2005-7-30
 */
package io.github.carlos_emr.carlos.report.data;

import java.sql.SQLException;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.ReportFilterDao;
import io.github.carlos_emr.carlos.commn.model.ReportFilter;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * @author yilee18
 */
public class RptReportFilter {

    private ReportFilterDao dao = SpringUtils.getBean(ReportFilterDao.class);

    int report_id = 0;
    String description;
    String value;
    String position;
    int status = 1;
    int order_no = 1;
    String javascript;
    String date_format;

    public boolean insertRecord() {
        ReportFilter r = new ReportFilter();
        r.setReportId(report_id);
        r.setDescription(description);
        r.setValue(value);
        r.setPosition(position);
        r.setStatus(status);
        r.setOrderNo(order_no);
        r.setJavascript(javascript);
        r.setDateFormat(date_format);
        dao.persist(r);

        return true;
    }

    public boolean deleteRecord(int recordId) {
        ReportFilter r = dao.find(recordId);
        if (r != null) {
            r.setStatus(0);
            dao.merge(r);
        }
        return true;
    }

    public boolean unDeleteRecord(int recordId) {
        ReportFilter r = dao.find(recordId);
        if (r != null) {
            r.setStatus(1);
            dao.merge(r);
        }
        return true;
    }

    // Legacy JSP adapters retain their six-column array contract; persistence returns typed entities.
    public Vector getNameList(int n) throws SQLException {
        return rows(dao.findByReportIdAndStatus(null, n));
    }

    public Vector getNameList(String recordId, int n) throws SQLException {
        return rows(dao.findByReportIdAndStatus(RptReportConfigData.parseReportId(recordId), n));
    }

    private Vector rows(java.util.List<ReportFilter> filters) {
        Vector ret = new Vector();
        for (ReportFilter r : filters) {
            ret.add(new String[]{RptReportConfigData.text(r.getDescription()), RptReportConfigData.text(r.getValue()),
                    RptReportConfigData.text(r.getPosition()), String.valueOf(r.getOrderNo()),
                    RptReportConfigData.text(r.getJavascript()), RptReportConfigData.text(r.getDateFormat())});
        }
        return ret;
    }
}
