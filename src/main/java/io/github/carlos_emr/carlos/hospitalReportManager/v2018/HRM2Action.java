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
package io.github.carlos_emr.carlos.hospitalReportManager.v2018;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMCategoryDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMCategory;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentSubClass;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMProviderConfidentialityStatement;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for Hospital Report Manager (HRM) viewing and administration.
 * <p>
 * This action provides document viewing and limited administrative operations for existing HRM documents:
 * <ul>
 * <li>DataTables-based report listing with filtering and pagination</li>
 * <li>Report categorization and search (searchCategory, saveCategory)</li>
 * <li>Provider confidentiality statement management (read and write)</li>
 * </ul>
 * <p>
 * The SFTP integration for fetching new reports from Ontario MD has been removed.
 * Existing HRM documents remain accessible for viewing, printing, and export.
 * <p>
 * The action routes requests to specific methods based on the "method" request parameter.
 * <p>
 * All operations enforce role-based security checks using SecurityInfoManager with
 * three privilege levels:
 * <ul>
 * <li>_hrm - Standard HRM access for providers (read operations require "r", write operations require "w")</li>
 * <li>_hrm.administrator - Administrative HRM access</li>
 * <li>_admin.hrm - System-level HRM administration</li>
 * </ul>
 *
 * @since 2006-04-20
 */
public class HRM2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private HRMDocumentDao hrmDocumentDao = SpringUtils.getBean(HRMDocumentDao.class);
    private HRMCategoryDao hrmCategoryDao = SpringUtils.getBean(HRMCategoryDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao = SpringUtils.getBean(HRMDocumentToDemographicDao.class);

    public String getConfidentialityStatement() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
            return null;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            throw new SecurityException("missing required security object _hrm");
        }
        HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

        String data = hrmProviderConfidentialityStatementDao.getConfidentialityStatementForProvider(loggedInInfo.getLoggedInProviderNo());
        ObjectNode res = objectMapper.createObjectNode();
        res.put("value", data != null ? data : "");

        response.setContentType("application/json");
        response.getWriter().write(res.toString());

        return null;
    }

    public String saveConfidentialityStatement() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
            return null;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "w", null)) {
            throw new SecurityException("missing required security object _hrm");
        }
        HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

        String value = StringUtils.trimToEmpty(request.getParameter("value"));

        HRMProviderConfidentialityStatement stmt = hrmProviderConfidentialityStatementDao.findByProvider(loggedInInfo.getLoggedInProviderNo());

        if (stmt == null) {
            stmt = new HRMProviderConfidentialityStatement();
            stmt.setId(loggedInInfo.getLoggedInProviderNo());
        }
        stmt.setStatement(value);
        hrmProviderConfidentialityStatementDao.merge(stmt);

        return null;
    }

    public String searchCategory() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
            return null;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            throw new SecurityException("missing required security object _hrm");
        }

        String query = request.getParameter("query");

        List<HRMCategory> categoryList = hrmCategoryDao.search(query);
        ArrayNode data = objectMapper.createArrayNode();
        for (HRMCategory category : categoryList) {
            ObjectNode obj = objectMapper.createObjectNode();
            obj.put("id", category.getId());
            obj.put("mnemonic", category.getSubClassNameMnemonic());
            obj.put("name", category.getCategoryName());
            data.add(obj);
        }
        ObjectNode d = objectMapper.createObjectNode();
        d.set("results", data);

        response.setContentType("application/json");
        response.getWriter().write(d.toString());

        return null;
    }

    public String saveCategory() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
            return null;
        }

        String hrmDocumentId = request.getParameter("hrmDocumentId");
        String categoryId = request.getParameter("categoryId");

        boolean isHrm = securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "w", null);

        HRMCategory category = null;
        HRMDocument doc = null;

        if (isHrm
                && io.github.carlos_emr.carlos.util.StringUtils.isInteger(categoryId)
                && io.github.carlos_emr.carlos.util.StringUtils.isInteger(hrmDocumentId)) {

            int catId = Integer.parseInt(categoryId);
            int docId = Integer.parseInt(hrmDocumentId);
            category = hrmCategoryDao.find(catId);
            if (category != null) {
                doc = hrmDocumentDao.find(docId);

                if (doc != null) {
                    doc.setHrmCategoryId(catId);
                    hrmDocumentDao.merge(doc);
                }
            }
        }


        ObjectNode d = objectMapper.createObjectNode();

        if (doc != null && doc.getHrmCategoryId() != null) {
            HRMCategory setCat = hrmCategoryDao.find(doc.getHrmCategoryId());
            if (setCat != null) {
                d.put("value", setCat.getSubClassNameMnemonic() + ":" + setCat.getCategoryName());
            }
        }

        response.setContentType("application/json");
        response.getWriter().write(d.toString());

        return null;
    }

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("getConfidentialityStatement".equals(method)) {
            return getConfidentialityStatement();
        } else if ("saveConfidentialityStatement".equals(method)) {
            return saveConfidentialityStatement();
        } else if ("searchCategory".equals(method)) {
            return searchCategory();
        } else if ("saveCategory".equals(method)) {
            return saveCategory();
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
            return null;
        }

        boolean isHrmAdmin = securityInfoManager.hasPrivilege(loggedInInfo, "_hrm.administrator", "r", null);
        boolean isHrm = securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null);
        boolean isAdmin = securityInfoManager.hasPrivilege(loggedInInfo, "_admin.hrm", "r", null);


        String start = request.getParameter("start");
        String length = request.getParameter("length");

        if (!StringUtils.isNumeric(start) || !StringUtils.isNumeric(length)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid pagination parameters");
            return null;
        }

        String orderingColumnIndex = request.getParameter("order[0][column]"); //idx (eg 0)
        String orderingColumnDirection = request.getParameter("order[0][dir]"); //asc,desc


        String providerNo = request.getParameter("providerNo");
        String providerUnmatched = StringUtils.trimToEmpty(request.getParameter("providerUnmatched"));

        String noSignOff = StringUtils.trimToEmpty(request.getParameter("noSignOff"));
        String demographicUnmatched = StringUtils.trimToEmpty(request.getParameter("demographicUnmatched"));

        if (isHrmAdmin && "ALL".equals(providerNo)) {
            providerNo = null;
        }
        if (!isHrmAdmin && !isAdmin) {
            providerNo = loggedInInfo.getLoggedInProviderNo();
        }

        //setup a column map from request parameters
        Map<Integer, ColumnInfo> columnMap = new HashMap<Integer, ColumnInfo>();
        int idx = 0;
        while (true) {
            if (request.getParameter("columns[" + idx + "][data]") == null) {
                break;
            }
            columnMap.put(idx, new ColumnInfo(idx, request.getParameter("columns[" + idx + "][data]")));
            idx++;
        }

        String orderBy = null;

        if (!StringUtils.isEmpty(orderingColumnIndex)) {
            if (!StringUtils.isNumeric(orderingColumnIndex)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid column index");
                return null;
            }
            ColumnInfo columnInfo = columnMap.get(Integer.parseInt(orderingColumnIndex));
            if ("patient_name".equals(columnInfo.getData())) {
                orderBy = "formattedName";
            } else if ("patient_dob".equals(columnInfo.getData())) {
                orderBy = "dob";
            } else if ("report_date".equals(columnInfo.getData())) {
                orderBy = "reportDate";
            } else if ("received_date".equals(columnInfo.getData())) {
                orderBy = "timeReceived";
            } else if ("sending_facility".equals(columnInfo.getData())) {
                orderBy = "sourceFacility";
            }
        }

        ArrayNode data = objectMapper.createArrayNode();

        long total = 0L;


        if (isHrm) {
            List<HRMDocument> docs = hrmDocumentDao.query(providerNo, "true".equals(providerUnmatched), "true".equals(noSignOff), "true".equals(demographicUnmatched), Integer.parseInt(start), Integer.parseInt(length), orderBy, orderingColumnDirection);

            total = hrmDocumentDao.queryForCount(providerNo, "true".equals(providerUnmatched), "true".equals(noSignOff), "true".equals(demographicUnmatched), Integer.parseInt(start), Integer.parseInt(length), orderBy, orderingColumnDirection);

            // Audit logging for patient data access
            if (!docs.isEmpty()) {
                LogAction.addLogSynchronous(loggedInInfo, LogConst.READ, LogConst.CON_HRM);
            }

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            for (HRMDocument d : docs) {
                HRMCategory category = null;
                if (d.getHrmCategoryId() != null) {
                    category = hrmCategoryDao.find(d.getHrmCategoryId());
                }

                List<HRMDocumentToDemographic> ptList = hrmDocumentToDemographicDao.findByHrmDocumentId(d.getId().toString());
                Integer demographicNo = (ptList != null && !ptList.isEmpty()) ? ptList.get(0).getDemographicNo() : null;
                ObjectNode data1 = objectMapper.createObjectNode();
                data1.put("id", d.getId() + "");
                data1.put("provider_no", d.getRecipientProviderNo() != null ? d.getRecipientProviderNo() : "");
                data1.put("demographic_no", demographicNo != null ? demographicNo.toString() : "");
                data1.put("recipient_name", d.getRecipientName() != null ? d.getRecipientName() : "");
                data1.put("patient_name", d.getFormattedName());
                data1.put("patient_dob", d.getDob());
                data1.put("patient_hcn", d.getHcn());
                data1.put("patient_gender", d.getGender());

                String reportDate = "";
                if (d.getReportDate() != null) {
                    reportDate = fmt.format(d.getReportDate());
                } else if (!d.getAccompanyingSubClasses().isEmpty()) {
                    for (HRMDocumentSubClass hdsc : d.getAccompanyingSubClasses()) {
                        if (hdsc.isActive() && hdsc.getSubClassDateTime() != null) {
                            reportDate = fmt.format(hdsc.getSubClassDateTime());
                        }
                    }
                }

                data1.put("report_date", reportDate);


                data1.put("sending_facility", d.getSourceFacility() != null ? d.getSourceFacility() : "");
                if (!StringUtils.isEmpty(d.getClassName()) && !StringUtils.isEmpty(d.getSubClassName())) {
                    String className = d.getClassName();
                    String subClassName = d.getSubClassName();
                    String displaySubClass = "";
                    if (subClassName != null) {
                        if (subClassName.indexOf("^") != -1) {
                            displaySubClass = subClassName.split("\\^")[1];
                        } else {
                            displaySubClass = subClassName;
                        }
                    }
                    data1.put("class_subclass", className + (displaySubClass.length() > 0 ? ":" + displaySubClass : ""));
                }
                if (!StringUtils.isEmpty(d.getClassName()) && !d.getAccompanyingSubClasses().isEmpty()) {
                    for (HRMDocumentSubClass sc : d.getAccompanyingSubClasses()) {
                        if (sc.isActive()) {
                            data1.put("class_subclass", d.getClassName() + " " + sc.getSubClass() + ":" + sc.getSubClassMnemonic() + ":" + sc.getSubClassDescription());
                        }
                    }
                }

                data1.put("received_date", d.getTimeReceived() != null ? fmt.format(d.getTimeReceived()) : "");
                data1.put("category", category != null ? category.getCategoryName() : "");
                data1.put("description", d.getDescription());

                data.add(data1);
            }
        }

        ObjectNode obj = objectMapper.createObjectNode();
        String drawParam = request.getParameter("draw");
        obj.put("draw", drawParam != null ? Integer.parseInt(drawParam) : 1);
        obj.put("recordsTotal", total);
        obj.put("recordsFiltered", total);
        obj.set("data", data);

        response.setContentType("application/json");
        response.getWriter().write(obj.toString());

        return null;
    }

}
