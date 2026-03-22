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


package io.github.carlos_emr.carlos.lab;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.LabRequestReportLinkDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Manages the linkage between lab requests and lab reports, allowing the system to
 * track which lab request originated a given lab report. Also synchronizes request
 * date/time values into the measurements extension table for flowsheet display.
 *
 * @since 2007-01-18
 */
public class LabRequestReportLink {
    private static LabRequestReportLinkDao dao = SpringUtils.getBean(LabRequestReportLinkDao.class);
    private static MeasurementsExtDao measurementsExtDao = SpringUtils.getBean(MeasurementsExtDao.class);

    /**
     * Retrieves the request-report link information for a given lab report.
     *
     * @param reportTable String the name of the report table (e.g., "hl7TextInfo")
     * @param reportId Long the report record identifier
     * @return HashMap&lt;String, Object&gt; containing link metadata (id, request_table, request_id,
     *         request_date, report_table, report_id); empty map if no link exists
     */
    public static HashMap<String, Object> getLinkByReport(String reportTable, Long reportId) {
        HashMap<String, Object> link = new HashMap<String, Object>();

        List<io.github.carlos_emr.carlos.commn.model.LabRequestReportLink> results = dao.findByReportTableAndReportId(reportTable, reportId.intValue());
        for (io.github.carlos_emr.carlos.commn.model.LabRequestReportLink l : results) {
            link.put("id", l.getId().longValue());
            link.put("request_table", l.getRequestTable());
            link.put("request_id", Long.valueOf(l.getRequestId()));
            link.put("request_date", l.getRequestDate());
            link.put("report_table", reportTable);
            link.put("report_id", reportId);
        }


        return link;
    }

    /**
     * Retrieves the request-report link information for a given lab request.
     *
     * @param requestTable String the name of the request table
     * @param reqId Long the request record identifier
     * @return HashMap&lt;String, Object&gt; containing link metadata (id, request_table, request_id,
     *         request_date, report_table, report_id); empty map if no link exists
     */
    public static HashMap<String, Object> getLinkByRequestId(String requestTable, Long reqId) {
        HashMap<String, Object> link = new HashMap<String, Object>();

        List<io.github.carlos_emr.carlos.commn.model.LabRequestReportLink> results = dao.findByRequestTableAndRequestId(requestTable, reqId.intValue());
        for (io.github.carlos_emr.carlos.commn.model.LabRequestReportLink l : results) {
            link.put("id", l.getId().longValue());
            link.put("request_table", l.getRequestTable());
            link.put("request_id", Long.valueOf(l.getRequestId()));
            link.put("request_date", l.getRequestDate());
            link.put("report_table", l.getReportTable());
            link.put("report_id", Long.valueOf(l.getReportId()));
        }

        return link;
    }

    /**
     * Retrieves the request date for a given link record, formatted as "yyyy-MM-dd HH:mm:ss".
     *
     * @param id String the primary key of the link record
     * @return String the formatted request date, or {@code null} representation if not found
     */
    public static String getRequestDate(String id) {
        Date requestDate = null;
        io.github.carlos_emr.carlos.commn.model.LabRequestReportLink l = dao.find(Integer.parseInt(id));
        if (l != null) {
            requestDate = l.getRequestDate();
        }

        return UtilDateUtilities.DateToString(requestDate, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * Retrieves the link record ID for a given report.
     *
     * @param reportTable String the name of the report table
     * @param reportId Long the report record identifier
     * @return Long the link record ID, or {@code null} if no link exists
     */
    public static Long getIdByReport(String reportTable, Long reportId) {
        HashMap<String, Object> link = getLinkByReport(reportTable, reportId);
        return (Long) link.get("id");
    }

    /**
     * Retrieves the request table ID associated with a given report.
     *
     * @param reportTable String the name of the report table
     * @param reportId Long the report record identifier
     * @return Long the request ID, or {@code null} if no link exists or the request ID is zero
     */
    public static Long getRequestTableIdByReport(String reportTable, Long reportId) {
        HashMap<String, Object> link = getLinkByReport(reportTable, reportId);
        Long requestId = (Long) link.get("request_id");
        if (requestId == null || requestId == 0) requestId = null;
        return requestId;
    }

    /**
     * Creates a new request-report link and persists the request date into the
     * measurements extension table if a corresponding measurement exists.
     *
     * @param requestTable String the name of the request table
     * @param requestId Long the request record identifier
     * @param requestDate String the request date in "yyyy-MM-dd HH:mm:ss" format
     * @param reportTable String the name of the report table
     * @param reportId Long the report record identifier
     */
    public static void save(String requestTable, Long requestId, String requestDate, String reportTable, Long reportId) {
        if (StringUtils.empty(reportTable) || reportId == null) return;
        if (StringUtils.empty(requestDate)) requestDate = null;

        io.github.carlos_emr.carlos.commn.model.LabRequestReportLink l = new io.github.carlos_emr.carlos.commn.model.LabRequestReportLink();
        l.setRequestTable(requestTable);
        l.setRequestId(requestId == null ? null : requestId.intValue());
        l.setRequestDate(ConversionUtils.fromDateString(requestDate));
        l.setReportTable(reportTable);
        l.setReportId(reportId.intValue());

        dao.persist(l);

        Integer measurementId = getMeasurementIdFromExt(reportTable, reportId.toString());
        MeasurementsExt mExt = getRequestDate_MeasurementsExt(measurementId);
        if (mExt == null && measurementId != null) {
            saveRequestDate_MeasurementsExt(requestDate, measurementId);
        }
    }

    /**
     * Deletes all request-report links for the given report.
     *
     * @param reportTable String the name of the report table
     * @param reportId Long the report record identifier
     */
    public static void delete(String reportTable, Long reportId) {
        for (io.github.carlos_emr.carlos.commn.model.LabRequestReportLink link : dao.findByReportTableAndReportId(reportTable, reportId.intValue())) {
            dao.remove(link.getId());
        }
    }

    /**
     * Updates an existing request-report link with new request details and synchronizes
     * the request date in the measurements extension table if applicable.
     *
     * @param id Long the link record identifier
     * @param requestTable String the name of the request table
     * @param requestId Long the request record identifier
     * @param requestDate String the request date in "yyyy-MM-dd HH:mm:ss" format
     */
    public static void update(Long id, String requestTable, Long requestId, String requestDate) {
        if (id == null) return;

        io.github.carlos_emr.carlos.commn.model.LabRequestReportLink l = dao.find(id.intValue());
        if (l != null) {
            l.setRequestTable(requestTable);
            l.setRequestId(requestId.intValue());
            l.setRequestDate(ConversionUtils.fromDateString(requestDate));
            dao.merge(l);
        }


        //update request_datetime in measurementsExt
        HashMap<String, Object> link = getLinkByRequestId(requestTable, requestId);
        String reportTbl = (String) link.get("report_table");
        String reportId = String.valueOf(link.get("report_id"));

        Integer measurementId = getMeasurementIdFromExt(reportTbl, reportId);
        MeasurementsExt mExt = getRequestDate_MeasurementsExt(measurementId);

        if (mExt != null && getRequestDate(id.toString()).equals(mExt.getVal())) {
            saveRequestDate_MeasurementsExt(requestDate, measurementId);
        }
    }

    private static void saveRequestDate_MeasurementsExt(String requestDate, Integer measurementId) {
        if (requestDate == null) return;

        Date dRequestDate = UtilDateUtilities.StringToDate(requestDate, "yyyy-MM-dd HH:mm:ss");
        if (dRequestDate == null) requestDate += " 00:00:00";

        MeasurementsExt mExt = getRequestDate_MeasurementsExt(measurementId);
        if (mExt == null) {
            mExt = new MeasurementsExt(measurementId);
            mExt.setKeyVal("request_datetime");
            mExt.setVal(requestDate);
            measurementsExtDao.persist(mExt);
        } else {
            mExt.setVal(requestDate);
            measurementsExtDao.merge(mExt);
        }
    }

    private static MeasurementsExt getRequestDate_MeasurementsExt(Integer measurementId) {
        List<MeasurementsExt> l_mExt = measurementsExtDao.getMeasurementsExtByMeasurementId(measurementId);
        for (MeasurementsExt mExt : l_mExt) {
            if (mExt.getKeyVal().equals("request_datetime")) {
                return mExt;
            }
        }
        return null;
    }

    private static Integer getMeasurementIdFromExt(String reportTable, String reportId) {
        String key = "lab_no";
        if ("labPatientPhysicianInfo".equalsIgnoreCase(reportTable)) key = "lab_ppid";

        return measurementsExtDao.getMeasurementIdByKeyValue(key, reportId);
    }
}
