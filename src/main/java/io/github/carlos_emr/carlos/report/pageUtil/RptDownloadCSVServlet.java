/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.report.pageUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.login.DBHelp;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.data.RptReportConfigData;
import io.github.carlos_emr.carlos.report.data.RptReportCreator;
import io.github.carlos_emr.carlos.report.data.RptReportFilter;
import io.github.carlos_emr.carlos.report.data.RptReportItem;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class RptDownloadCSVServlet extends HttpServlet {

    private static final Logger _logger = MiscUtils.getLogger();
    String reportName = "";
    String DELIMETER = "\t";

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null)
            return;
        if (!hasReportDownloadPrivilege(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String in = "";
        try {
            in = request.getParameter("demoReport") != null ? demoReport(request) : formReport(request);
        } catch (ServletException e1) {
            _logger.error("RptDownloadCSVServlet service() - form report failed", e1);
        } catch (Exception e1) {
            _logger.error("RptDownloadCSVServlet service() - report generation failed", e1);
        }


        String filename = attachmentFilename(reportName);
        OutputStream out = null;
        try {
            if (in != null) {
                out = new BufferedOutputStream(response.getOutputStream());
                byte[] b = in.getBytes();
                int len = b.length;
                int n = 0;
                int FIXED_LEN = 2048;
                String contentType = "application/unknow";
                MiscUtils.getLogger().debug("contentType: " + contentType);
                response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                while (n <= len - FIXED_LEN) {
                    out.write(b, n, FIXED_LEN); // b.flush();
                    n += FIXED_LEN;
                }
                if (n > len - FIXED_LEN) {
                    out.write(b, n, len - n);
                }
            }
        } finally {
            if (out != null)
                try {
                    out.close();
                } catch (Exception e) {
                }
        }

    }

    boolean hasReportDownloadPrivilege(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        return securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.reporting", SecurityInfoManager.READ, null);
    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    private static String attachmentFilename(String reportName) {
        String safeName = reportName == null ? "" : reportName;
        safeName = safeName.replaceAll("[\\p{Cntrl}\"\\\\/;]", "_");
        safeName = safeName.replaceAll("[^A-Za-z0-9._ -]", "_");
        safeName = safeName.trim().replaceAll(" +", " ");
        if (safeName.isEmpty() || ".".equals(safeName) || "..".equals(safeName)) {
            safeName = "report";
        }
        if (safeName.length() > 120) {
            safeName = safeName.substring(0, 120).trim();
        }
        return safeName + ".csv";
    }

    private String formReport(HttpServletRequest request) {


        String SAVE_AS = "default";
        String reportId = request.getParameter("id") != null ? request.getParameter("id") : "0";
        // get form name
        //String reportName = "";
        String in = "";
        try {
            reportName = (new RptReportItem()).getReportName(reportId);
            RptFormQuery formQuery = new RptFormQuery();
            ParameterizedSql psql = formQuery.getQueryStr(reportId, request);

            RptReportConfigData formConfig = new RptReportConfigData();
            Vector[] vecField = formConfig.getAllFieldNameValue(SAVE_AS, reportId);
            Vector vecFieldCaption = vecField[1];


            Vector vecFieldValue = (new RptReportCreator()).query(psql, vecFieldCaption);

            StringWriter swr = new StringWriter();
            CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter('\t')
                .build();
            CSVPrinter csvp = new CSVPrinter(swr, format);

            for (int i = 0; i < vecFieldCaption.size(); i++) {
                csvp.print((String) vecFieldCaption.get(i));
            }

            for (int i = 0; i < vecFieldValue.size(); i++) {
                Properties prop = (Properties) vecFieldValue.get(i);
                csvp.println();
                for (int j = 0; j < vecFieldCaption.size(); j++) {
                    csvp.print(prop.getProperty((String) vecFieldCaption.get(j), ""));
                }
            }
            csvp.flush();
            in = swr.toString();


        } catch (Exception e1) {
            _logger.error("service() - form report");
        }
        return in;
    }

    private String demoReport(HttpServletRequest request) throws Exception {
        reportName = "clientDatabaseReport";
        String in = "";
        String reportId = request.getParameter("id") != null ? request.getParameter("id") : "0";


        String ARTYPE = "formBCAR";
        if (request.getParameter("bcartype") != null && request.getParameter("bcartype").equals("BCAR2007")) {
            ARTYPE = "formBCAR2007";
        }

        MiscUtils.getLogger().debug("AR TYPE " + ARTYPE);


        Properties propDemoSelect = new Properties();
        Properties propSpecSelect = new Properties();
        Properties propARSelect = new Properties();
        propDemoSelect.setProperty("last_name", "Last Name");
        propDemoSelect.setProperty("first_name", "First Name");
        propDemoSelect.setProperty("date_joined", "Date Joined");
        propDemoSelect.setProperty("hin", "Health Ins.");
        propDemoSelect.setProperty("hc_type", "HC Type");
        propDemoSelect.setProperty("address", "Address");
        propDemoSelect.setProperty("city", "City");
        propDemoSelect.setProperty("postal", "Postal Code");
        propDemoSelect.setProperty("phone", "Phone (H)");
        propDemoSelect.setProperty("phone2", "Phone (W)");
        propDemoSelect.setProperty("email", "Email");
        Vector vecSeqDemoSelect = new Vector();
        vecSeqDemoSelect.add("last_name");
        vecSeqDemoSelect.add("first_name");
        vecSeqDemoSelect.add("date_joined");
        vecSeqDemoSelect.add("hin");
        vecSeqDemoSelect.add("hc_type");
        vecSeqDemoSelect.add("address");
        vecSeqDemoSelect.add("city");
        vecSeqDemoSelect.add("postal");
        vecSeqDemoSelect.add("phone");
        vecSeqDemoSelect.add("phone2");
        vecSeqDemoSelect.add("email");

        Vector vecSeqSpecSelect = new Vector();
        propSpecSelect.setProperty("prefer_language", "Preferred Language");
        CarlosProperties oscarProps = CarlosProperties.getInstance();
        if (oscarProps.getProperty("demographicExt") != null) {
            String[] propDemoExt = oscarProps.getProperty("demographicExt", "").split("\\|");
            for (int i = 0; i < propDemoExt.length; i++) {
                propSpecSelect.setProperty(propDemoExt[i].replace(' ', '_'), propDemoExt[i]);
                vecSeqSpecSelect.add(propDemoExt[i].replace(' ', '_'));
            }
        }

        propARSelect.setProperty("c_EDD", "EDD");
        propARSelect.setProperty("pg1_famPhy", "Family Physician");
        propARSelect.setProperty("pg1_partnerName", "Partner Name");
        Vector vecSeqARSelect = new Vector();
        vecSeqARSelect.add("c_EDD");
        vecSeqARSelect.add("ga");
        vecSeqARSelect.add("pg1_famPhy");
        vecSeqARSelect.add("pg1_partnerName");

        propARSelect.setProperty("ga", "GA Today");
        propARSelect.setProperty("b_primiparous", "Primiparous");

//        get selection
        boolean bDemoSelect = false;
        boolean bARSelect = false;
        boolean bSpecSelect = false;
        String sDemoSelect = "";
        String sSpecSelect = "";
        String sARSelect = "";


        String VARNAME_FORMAT = "startDate\\d|endDate\\d";
        Vector[] valueParams = getConfiguredFilterValues(reportId, request);
        Vector vecValue = valueParams[0];
        Vector vecDateFormat = valueParams[1];
        Properties propTempDemoSelect = new Properties();
        Properties propTempSpecSelect = new Properties();

        // Build SQL column selections from server-owned allowlists. The request
        // only supplies "selected/not selected"; column names never come from
        // request parameter names or hidden fields.
        for (Object field : vecSeqDemoSelect) {
            String name = (String) field;
            if (request.getParameter(name) != null) {
                bDemoSelect = true;
                propTempDemoSelect.setProperty(name, "");
            }
        }
        for (Object field : vecSeqARSelect) {
            String name = (String) field;
            if (request.getParameter(name) != null) {
                bARSelect = true;
                if (!name.equals("ga") && !name.equals("b_primiparous"))
                    sARSelect += (sARSelect.length() < 1 ? "" : ",") + ARTYPE + "." + name;
            }
        }
        for (Object field : vecSeqSpecSelect) {
            String name = (String) field;
            if (request.getParameter(name) != null) {
                bSpecSelect = true;
                propTempSpecSelect.setProperty(name, "");
            }
        }
//         get seq. select string
        for (int i = 0; i < vecSeqDemoSelect.size(); i++) {
            if (propTempDemoSelect.getProperty((String) vecSeqDemoSelect.get(i)) != null) {
                sDemoSelect += (sDemoSelect.length() < 1 ? "" : ",") + "demographic." + vecSeqDemoSelect.get(i);
            }
        }
        for (int i = 0; i < vecSeqSpecSelect.size(); i++) {
            if (propTempSpecSelect.getProperty((String) vecSeqSpecSelect.get(i)) != null) {
                sSpecSelect += (sSpecSelect.length() < 1 ? "" : ",") + "demographicExt." + vecSeqSpecSelect.get(i);
            }
        }

        MiscUtils.getLogger().debug(":" + bDemoSelect + bSpecSelect + bARSelect);
        MiscUtils.getLogger().debug(":" + sDemoSelect + sSpecSelect + sARSelect);

//        get replaced filter
//         filling the var with the real date value — now parameterized
        Vector vecFilter = new Vector();
        boolean bDemoFilter = false;
        boolean bARFilter = false;
        boolean bSpecFilter = false;
        String sDemoFilter = "";
        String sSpecFilter = "";
        String sARFilter = "";
        List<Object> demoFilterParams = new ArrayList<>();
        List<Object> specFilterParams = new ArrayList<>();
        List<Object> arFilterParams = new ArrayList<>();
        String sTempEle = "";
        for (int i = 0; i < vecValue.size(); i++) {
            String tempVal = (String) vecValue.get(i);
            Vector vecVar = RptReportCreator.getVarVec(tempVal);
            Vector vecVarValue = new Vector();
            for (int j = 0; j < vecVar.size(); j++) {
                // conver date format if needed
                if (((String) vecVar.get(j)).matches(VARNAME_FORMAT) && ((String) vecDateFormat.get(i)).length() > 1) {
                    vecVarValue.add(RptReportCreator.getDiffDateFormat(request.getParameter((String) vecVar.get(j)),
                            (String) vecDateFormat.get(i), "yyyy-MM-dd"));
                } else {
                    vecVarValue.add(request.getParameter((String) vecVar.get(j)));
                }
            }
            ParameterizedSql psFilter = RptReportCreator.getWhereValueClauseParameterized(tempVal, vecVarValue);
            String strFilter = psFilter.getSql();
            List<Object> filterParams = psFilter.getParams();
            if (strFilter.indexOf("demographic.") >= 0) {
                bDemoFilter = true;
                sDemoFilter += (sDemoFilter.length() < 1 ? "" : " and ") + strFilter;
                demoFilterParams.addAll(filterParams);
            }
            if (strFilter.indexOf("demographicExt.") >= 0) {
                bSpecFilter = true;
                sSpecFilter += (sSpecFilter.length() < 1 ? "" : " and ") + strFilter;
                specFilterParams.addAll(filterParams);
            }
            if (strFilter.indexOf(ARTYPE + ".") >= 0) {
                bARFilter = true;
                //"formBCAR.demographic_no in (select distinct demographic_no from formBCBirthSumMo)"
                if (strFilter.indexOf("formBCBirthSumMo") > 0) {
                    String sBirthSumNo = "";
                    try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(
                            "select distinct demographic_no from formBCBirthSumMo", List.of()))) {
                        if (rs != null) {
                            while (rs.next()) {
                                sBirthSumNo += (sBirthSumNo.length() > 0 ? "," : "") + rs.getInt("demographic_no");
                            }
                        }
                    }
                    sBirthSumNo = sBirthSumNo.length() > 0 ? sBirthSumNo : "0";
                    strFilter = " " + ARTYPE + ".demographic_no in (" + sBirthSumNo + ")";
                    filterParams = new ArrayList<>(); // no bind params for the integer-only IN list
                }

                sARFilter += (sARFilter.length() < 1 ? "" : " and ") + strFilter;
                arFilterParams.addAll(filterParams);
            }
            MiscUtils.getLogger().debug(i + tempVal + " tempVal: " + vecVarValue);
            MiscUtils.getLogger().debug(i + strFilter);
            vecFilter.add(strFilter);
        }

//        query sub
//        todo: filt out Delivered Clients
//         one table: demographic
        Vector vecFieldCaption = new Vector();
        Vector vecFieldName = new Vector();
        Vector vecFieldValue = new Vector();
        String ORDER_BY = " order by demographic.last_name, demographic.first_name";
        if (bDemoSelect && !bARSelect && !bSpecSelect && bDemoFilter && !bARFilter && !bSpecFilter) {
            String sql = "select " + sDemoSelect + " from demographic where " + sDemoFilter + ORDER_BY;
            MiscUtils.getLogger().debug(" one table: demographic: " + sql);
            String[] temp = sDemoSelect.replaceAll("demographic.", "").split(",");
            for (int i = 0; i < temp.length; i++) {
                vecFieldCaption.add(propDemoSelect.getProperty(temp[i].trim()));
                vecFieldName.add(temp[i].trim());
                MiscUtils.getLogger().debug(" vecFieldCaption: " + propDemoSelect.getProperty(temp[i].trim()));
            }
            vecFieldValue = (new RptReportCreator()).query(sql, vecFieldName, demoFilterParams.toArray());
        }

//         table: demographic and demographicExt
        Vector vecSpecCaption = new Vector();
        Properties propSpecValue = new Properties();
        if ((bDemoSelect && !bARSelect && bSpecSelect && !bARFilter) || (!bARFilter && bSpecFilter)) {
            if (bDemoSelect && !bARSelect && bSpecSelect && !bSpecFilter) {
                vecFieldName.add("demographic_no");
                String sql = "select demographic_no," + sDemoSelect + " from demographic where " + sDemoFilter + ORDER_BY;
                MiscUtils.getLogger().debug(" demographic and demographicExt: " + sql);
                String[] temp = sDemoSelect.replaceAll("demographic.", "").split(",");
                for (int i = 0; i < temp.length; i++) {
                    vecFieldCaption.add(propDemoSelect.getProperty(temp[i].trim()));
                    vecFieldName.add(temp[i].trim());
                    MiscUtils.getLogger().debug(" vecFieldCaption: " + propDemoSelect.getProperty(temp[i].trim()));
                }
                vecFieldValue = (new RptReportCreator()).query(sql, vecFieldName, demoFilterParams.toArray());
                vecFieldName.remove(0); // remove "demographic_no"

                //get demographic_no
                java.util.List<String> demoNoList = new java.util.ArrayList<>();
                for (int j = 0; j < vecFieldValue.size(); j++) {
                    Properties prop = (Properties) vecFieldValue.get(j);
                    demoNoList.add(prop.getProperty("demographic_no"));
                }
                temp = sSpecSelect.replaceAll("demographicExt.", "").split(",");
                if (!demoNoList.isEmpty()) {
                    String inPlaceholders = String.join(",", java.util.Collections.nCopies(demoNoList.size(), "?"));
                    for (int i = 0; i < temp.length; i++) {
                        vecSpecCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                        sql = "select demographic_no,value from demographicExt where key_val=? and demographic_no in (" + inPlaceholders + ") order by date_time desc limit 1";
                        Object[] params = new Object[1 + demoNoList.size()];
                        params[0] = temp[i].trim();
                        for (int k = 0; k < demoNoList.size(); k++) {
                            params[k + 1] = demoNoList.get(k);
                        }
                        try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(sql, Arrays.asList(params)))) {
                            if (rs != null) while (rs.next()) {
                                propSpecValue.setProperty(rs.getString("demographic_no") + temp[i], rs.getString("value"));
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < temp.length; i++) {
                        vecSpecCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                    }
                }
                MiscUtils.getLogger().debug(" demographic and demographicExt: " + sql);
            }
            if (bSpecFilter) {
                vecFieldName.add("demographic_no");
                // get demoNo
                String sql = null;
                String subQuery = "select distinct(demographic.demographic_no) from demographicExt, demographic where demographic.demographic_no=demographicExt.demographic_no ";
                String joined = RptReportCreator.joinPredicates(sDemoFilter, sSpecFilter);
                if (!joined.isEmpty()) subQuery += " and " + joined + "  ";
                MiscUtils.getLogger().debug(" demographic and demographicExt subQuery: " + subQuery);
                java.util.List<String> subDemoNoList = new java.util.ArrayList<>();
                List<Object> subQueryParams = new ArrayList<>();
                subQueryParams.addAll(demoFilterParams);
                subQueryParams.addAll(specFilterParams);
                try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(subQuery, subQueryParams))) {
                    if (rs != null) while (rs.next()) {
                        subDemoNoList.add(String.valueOf(rs.getInt("demographic.demographic_no")));
                    }
                }
                // Build comma-separated string — subFormDemoNo built from rs.getInt() (integer-only)
                String subFormDemoNo = subDemoNoList.isEmpty() ? "0" : String.join(",", subDemoNoList);
                // get value for spec
                String[] temp = sSpecSelect.replaceAll("demographicExt.", "").split(",");
                if (!subDemoNoList.isEmpty()) {
                    String inPlaceholders = String.join(",", java.util.Collections.nCopies(subDemoNoList.size(), "?"));
                    for (int i = 0; i < temp.length; i++) {
                        vecSpecCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                        sql = "select demographic_no,value from demographicExt where key_val=? and demographic_no in (" + inPlaceholders + ") order by date_time desc limit 1";
                        Object[] params = new Object[1 + subDemoNoList.size()];
                        params[0] = temp[i].trim();
                        for (int k = 0; k < subDemoNoList.size(); k++) {
                            params[k + 1] = subDemoNoList.get(k);
                        }
                        MiscUtils.getLogger().debug(" demographic and demographicExt: " + sql);
                        try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(sql, Arrays.asList(params)))) {
                            if (rs != null) while (rs.next()) {
                                propSpecValue.setProperty(rs.getString("demographic_no") + temp[i], rs.getString("value"));
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < temp.length; i++) {
                        vecSpecCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                    }
                }

                //sTempEle = sSpecSelect.length()>0? (","+sSpecSelect) : "";
                sql = "select demographic.demographic_no," + sDemoSelect + " from demographic where ";
                sql += " demographic.demographic_no in (" + subFormDemoNo + ") " + ORDER_BY; // subFormDemoNo built from rs.getInt() (integer-only)
                MiscUtils.getLogger().debug(" demographic and demographicExt: " + sql);

                temp = sDemoSelect.replaceAll("demographic.", "").split(",");
                for (int i = 0; i < temp.length; i++) {
                    vecFieldCaption.add(propDemoSelect.getProperty(temp[i].trim()));
                    vecFieldName.add(temp[i].trim());
                    MiscUtils.getLogger().debug(" vecFieldCaption: " + propDemoSelect.getProperty(temp[i].trim()));
                }
                /*
                if(bSpecSelect) {
                    temp = sSpecSelect.replaceAll("demographicExt.","").split(",");
                    for(int i=0; i<temp.length; i++) {
                        vecFieldCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                        vecFieldName.add(temp[i].trim());
                        MiscUtils.getLogger().debug(" vecFieldCaption: " + propSpecSelect.getProperty(temp[i].trim()));
                    }
                }
                */
                vecFieldValue = (new RptReportCreator()).query(sql, vecFieldName);
                vecFieldName.remove(0); // remove "demographic_no"
            }
        }

//         table: demographic and formBCAR


        if ((bDemoSelect && bARSelect && !bSpecSelect && !bSpecFilter) || (!bSpecSelect && bARFilter && !bSpecFilter)) {
            String joinedAr = RptReportCreator.joinPredicates(sDemoFilter, sARFilter);
            String subQuery = "select max(ID) from " + ARTYPE + ", demographic where demographic.demographic_no=" + ARTYPE + ".demographic_no ";
            if (!joinedAr.isEmpty()) subQuery += " and " + joinedAr;
            subQuery += " group by " + ARTYPE + ".demographic_no," + ARTYPE + ".formCreated ";
            MiscUtils.getLogger().debug(" demographic and " + ARTYPE + " subQuery: " + subQuery);
            String subFormId = "";
            List<Object> subQueryParams = new ArrayList<>();
            subQueryParams.addAll(demoFilterParams);
            subQueryParams.addAll(arFilterParams);
            try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(subQuery, subQueryParams))) {
                if (rs != null) while (rs.next()) {
                    subFormId += (subFormId.length() > 0 ? "," : "") + rs.getInt("max(ID)");
                }
            }

            sTempEle = sARSelect.length() > 0 ? ("," + sARSelect) : "";
            subFormId = subFormId.length() > 0 ? subFormId : "0";
            String sql = "select demographic.demographic_no," + sDemoSelect + sTempEle + " from demographic," + ARTYPE + " where ";
            sql += " " + ARTYPE + ".ID in (" + subFormId + ") and demographic.demographic_no=" + ARTYPE + ".demographic_no " + ORDER_BY; // subFormId built from rs.getInt() (integer-only)
            MiscUtils.getLogger().debug(" demographic and " + ARTYPE + ": " + sql);

            String[] temp = sDemoSelect.replaceAll("demographic.", "").split(",");
            for (int i = 0; i < temp.length; i++) {
                vecFieldCaption.add(propDemoSelect.getProperty(temp[i].trim()));
                vecFieldName.add(temp[i].trim());
                MiscUtils.getLogger().debug(" vecFieldCaption: " + propDemoSelect.getProperty(temp[i].trim()));
            }
            if (bARSelect) {
                temp = sARSelect.replaceAll(ARTYPE + ".", "").split(",");
                for (int i = 0; i < temp.length; i++) {
                    vecFieldCaption.add(propARSelect.getProperty(temp[i].trim()));
                    vecFieldName.add(temp[i].trim());
                    MiscUtils.getLogger().debug(" vecFieldCaption: " + propARSelect.getProperty(temp[i].trim()));
                }
            }
            vecFieldValue = (new RptReportCreator()).query(sql, vecFieldName);

            //vecFieldName.remove(0); // remove "demographic_no"
        }

//         table: all
        if ((bDemoSelect && bARSelect && bSpecSelect) || (bARFilter && bSpecFilter)) {
            if (bDemoSelect && bARSelect && bSpecSelect && !bSpecFilter) {
                vecFieldName.add("demographic_no");
                String joinedAllAr = RptReportCreator.joinPredicates(sDemoFilter, sARFilter);
                String subQuery = "select max(ID) from " + ARTYPE + ", demographic where demographic.demographic_no=" + ARTYPE + ".demographic_no ";
                if (!joinedAllAr.isEmpty()) subQuery += " and " + joinedAllAr;
                subQuery += " group by " + ARTYPE + ".demographic_no," + ARTYPE + ".formCreated ";
                MiscUtils.getLogger().debug(" demographic and " + ARTYPE + " subQuery: " + subQuery);
                String subFormId = "";
                List<Object> subQueryParams = new ArrayList<>();
                subQueryParams.addAll(demoFilterParams);
                subQueryParams.addAll(arFilterParams);
                try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(subQuery, subQueryParams))) {
                    if (rs != null) while (rs.next()) {
                        subFormId += (subFormId.length() > 0 ? "," : "") + rs.getInt("max(ID)");
                    }
                }

                sTempEle = sARSelect.length() > 0 ? ("," + sARSelect) : "";
                subFormId = subFormId.length() > 0 ? subFormId : "0";
                String sql = "select demographic.demographic_no," + sDemoSelect + sTempEle + " from demographic," + ARTYPE + " where ";
                sql += " " + ARTYPE + ".ID in (" + subFormId + ") and demographic.demographic_no=" + ARTYPE + ".demographic_no " + ORDER_BY; // subFormId built from rs.getInt() (integer-only)
                MiscUtils.getLogger().debug(" demographic and " + ARTYPE + ": " + sql);

                String[] temp = sDemoSelect.replaceAll("demographic.", "").split(",");
                for (int i = 0; i < temp.length; i++) {
                    vecFieldCaption.add(propDemoSelect.getProperty(temp[i].trim()));
                    vecFieldName.add(temp[i].trim());
                    MiscUtils.getLogger().debug(" vecFieldCaption: " + propDemoSelect.getProperty(temp[i].trim()));
                }
                if (bARSelect) {
                    temp = sARSelect.replaceAll(ARTYPE + ".", "").split(",");
                    for (int i = 0; i < temp.length; i++) {
                        vecFieldCaption.add(propARSelect.getProperty(temp[i].trim()));
                        vecFieldName.add(temp[i].trim());
                        MiscUtils.getLogger().debug(" vecFieldCaption: " + propARSelect.getProperty(temp[i].trim()));
                    }
                }
                vecFieldValue = (new RptReportCreator()).query(sql, vecFieldName);
                vecFieldName.remove(0); // remove "demographic_no"

                //get demographic_no
                java.util.List<String> demoNoList = new java.util.ArrayList<>();
                for (int j = 0; j < vecFieldValue.size(); j++) {
                    Properties prop = (Properties) vecFieldValue.get(j);
                    demoNoList.add(prop.getProperty("demographic_no"));
                }
                temp = sSpecSelect.replaceAll("demographicExt.", "").split(",");
                if (!demoNoList.isEmpty()) {
                    String inPlaceholders = String.join(",", java.util.Collections.nCopies(demoNoList.size(), "?"));
                    for (int i = 0; i < temp.length; i++) {
                        vecSpecCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                        sql = "select demographic_no,value from demographicExt where key_val=? and demographic_no in (" + inPlaceholders + ") order by date_time ";
                        Object[] params = new Object[1 + demoNoList.size()];
                        params[0] = temp[i].trim();
                        for (int k = 0; k < demoNoList.size(); k++) {
                            params[k + 1] = demoNoList.get(k);
                        }
                        try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(sql, Arrays.asList(params)))) {
                            if (rs != null) while (rs.next()) {
                                propSpecValue.setProperty(rs.getString("demographic_no") + temp[i], rs.getString("value"));
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < temp.length; i++) {
                        vecSpecCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                    }
                }
            }
            MiscUtils.getLogger().debug(" table: all: ");

            if (bARFilter && bSpecFilter) {
                // spec first
                vecFieldName.add("demographic_no");
                // get demoNo
                String sql = null;
                String joinedSpec1 = RptReportCreator.joinPredicates(sDemoFilter, sSpecFilter);
                String subQuery = "select distinct(demographic.demographic_no) from demographicExt, demographic where demographic.demographic_no=demographicExt.demographic_no ";
                if (!joinedSpec1.isEmpty()) subQuery += " and " + joinedSpec1 + "  ";
                MiscUtils.getLogger().debug(" demographic and demographicExt subQuery: " + subQuery);
                java.util.List<String> subDemoNoList = new java.util.ArrayList<>();
                List<Object> subQueryParams1 = new ArrayList<>();
                subQueryParams1.addAll(demoFilterParams);
                subQueryParams1.addAll(specFilterParams);
                try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(subQuery, subQueryParams1))) {
                    if (rs != null) while (rs.next()) {
                        subDemoNoList.add(String.valueOf(rs.getInt("demographic.demographic_no")));
                    }
                }
                // get value for spec
                String[] temp = sSpecSelect.replaceAll("demographicExt.", "").split(",");
                if (!subDemoNoList.isEmpty()) {
                    String inPlaceholders = String.join(",", java.util.Collections.nCopies(subDemoNoList.size(), "?"));
                    for (int i = 0; i < temp.length; i++) {
                        vecSpecCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                        sql = "select demographic_no,value from demographicExt where key_val=? and demographic_no in (" + inPlaceholders + ") order by date_time desc limit 1";
                        Object[] params = new Object[1 + subDemoNoList.size()];
                        params[0] = temp[i].trim();
                        for (int k = 0; k < subDemoNoList.size(); k++) {
                            params[k + 1] = subDemoNoList.get(k);
                        }
                        try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(sql, Arrays.asList(params)))) {
                            if (rs != null) while (rs.next()) {
                                propSpecValue.setProperty(rs.getString("demographic_no") + temp[i], rs.getString("value"));
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < temp.length; i++) {
                        vecSpecCaption.add(propSpecSelect.getProperty(temp[i].trim()));
                    }
                }

                // formAR second
                String joinedAr2 = RptReportCreator.joinPredicates(sDemoFilter, sARFilter);
                subQuery = "select max(ID) from " + ARTYPE + ", demographic where demographic.demographic_no=" + ARTYPE + ".demographic_no ";
                if (!joinedAr2.isEmpty()) subQuery += " and " + joinedAr2;
                subQuery += " group by " + ARTYPE + ".demographic_no," + ARTYPE + ".formCreated ";
                MiscUtils.getLogger().debug(" demographic and " + ARTYPE + " subQuery: " + subQuery);
                String subFormId = "";
                List<Object> subQueryParams2 = new ArrayList<>();
                subQueryParams2.addAll(demoFilterParams);
                subQueryParams2.addAll(arFilterParams);
                try (ResultSet rs = DBHelp.searchDBRecord(new ParameterizedSql(subQuery, subQueryParams2))) {
                    if (rs != null) while (rs.next()) {
                        subFormId += (subFormId.length() > 0 ? "," : "") + rs.getInt("max(ID)");
                    }
                }

                // total — subFormDemoNo and subFormId built from rs.getInt() (integer-only)
                sTempEle = sARSelect.length() > 0 ? ("," + sARSelect) : "";
                subFormId = subFormId.length() > 0 ? subFormId : "0";
                String subFormDemoNo = subDemoNoList.isEmpty() ? "0" : String.join(",", subDemoNoList);
                sql = "select demographic.demographic_no," + sDemoSelect + sTempEle + " from demographic," + ARTYPE + " where ";
                sql += " demographic.demographic_no in (" + subFormDemoNo + ") and "; // subFormDemoNo built from rs.getInt() (integer-only)
                sql += " " + ARTYPE + ".ID in (" + subFormId + ") and demographic.demographic_no=" + ARTYPE + ".demographic_no " + ORDER_BY; // subFormId built from rs.getInt() (integer-only)
                MiscUtils.getLogger().debug(" total: " + sql);

                temp = sDemoSelect.replaceAll("demographic.", "").split(",");
                for (int i = 0; i < temp.length; i++) {
                    vecFieldCaption.add(propDemoSelect.getProperty(temp[i].trim()));
                    vecFieldName.add(temp[i].trim());
                    MiscUtils.getLogger().debug(" vecFieldCaption: " + propDemoSelect.getProperty(temp[i].trim()));
                }
                if (bARSelect) {
                    temp = sARSelect.replaceAll(ARTYPE + ".", "").split(",");
                    for (int i = 0; i < temp.length; i++) {
                        vecFieldCaption.add(propARSelect.getProperty(temp[i].trim()));
                        vecFieldName.add(temp[i].trim());
                        MiscUtils.getLogger().debug(" vecFieldCaption: " + propARSelect.getProperty(temp[i].trim()));
                    }
                }
                vecFieldValue = (new RptReportCreator()).query(sql, vecFieldName);
                vecFieldName.remove(0); // remove "demographic_no"

            }
        }

        StringWriter swr = new StringWriter();
        CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
            .setDelimiter('\t')
            .build();
        CSVPrinter csvp = new CSVPrinter(swr, format);

        csvp.print("id");
        for (int i = 0; i < vecFieldCaption.size(); i++) {
            csvp.print((String) vecFieldCaption.get(i));
        }
        if (bSpecSelect) {
            for (int i = 0; i < vecSpecCaption.size(); i++) {
                csvp.print((String) vecSpecCaption.get(i));
            }
        }

        for (int i = 0; i < vecFieldValue.size(); i++) {
            Properties prop = (Properties) vecFieldValue.get(i);
            csvp.println();
            csvp.print("" + (i + 1));

            for (int j = 0; j < vecFieldName.size(); j++) {
                csvp.print(prop.getProperty((String) vecFieldName.get(j), ""));
            }
            if (bSpecSelect) {
                String demoNo = prop.getProperty("demographic_no");
                for (int j = 0; j < vecSpecCaption.size(); j++) {
                    csvp.print(propSpecValue.getProperty(demoNo + ((String) vecSpecCaption.get(j)).replaceAll(" ", "_"), ""));
                }
            }
        }

        in = swr.toString();
        return in;
    }

    Vector[] getConfiguredFilterValues(String reportId, HttpServletRequest request) throws Exception {
        return RptFormQuery.getValueParam(new RptReportFilter().getNameList(reportId, 1), request);
    }
}
