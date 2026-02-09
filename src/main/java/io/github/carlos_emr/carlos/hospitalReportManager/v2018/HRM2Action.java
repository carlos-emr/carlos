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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMCategoryDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMCategory;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentSubClass;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMProviderConfidentialityStatement;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for Hospital Report Manager (HRM) viewing and administration.
 * <p>
 * This action provides read-only access to existing HRM documents:
 * <ul>
 * <li>DataTables-based report listing with filtering and pagination</li>
 * <li>Report categorization and search (searchCategory, saveCategory)</li>
 * <li>Provider confidentiality statement management</li>
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
 * <li>_hrm - Standard HRM access for providers</li>
 * <li>_hrm.administrator - Administrative HRM access</li>
 * <li>_admin.hrm - System-level HRM administration</li>
 * </ul>
 *
 * @since 2006-04-20
 */
public class HRM2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    static int draw = 0;

    Logger logger = MiscUtils.getLogger();

    private HRMDocumentDao hrmDocumentDao = SpringUtils.getBean(HRMDocumentDao.class);
    private HRMCategoryDao hrmCategoryDao = SpringUtils.getBean(HRMCategoryDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao = SpringUtils.getBean(HRMDocumentToDemographicDao.class);

    public String getConfidentialityStatement() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

        String data = hrmProviderConfidentialityStatementDao.getConfidentialityStatementForProvider(loggedInInfo.getLoggedInProviderNo());
        JSONObject res = new JSONObject();
        res.put("value", data != null ? data : "");

        response.setContentType("application/json");
        res.write(response.getWriter());

        return null;
    }

    public String saveConfidentialityStatement() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

        String value = request.getParameter("value");

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

        String query = request.getParameter("query");

        List<HRMCategory> categoryList = hrmCategoryDao.search(query);
        JSONArray data = new JSONArray();
        for (HRMCategory category : categoryList) {
            JSONObject obj = new JSONObject();
            obj.put("id", category.getId());
            obj.put("mnemonic", category.getSubClassNameMnemonic());
            obj.put("name", category.getCategoryName());
            data.put(obj);
        }
        JSONObject d = new JSONObject();
        d.put("results", data);

        response.setContentType("text/x-json");
        d.write(response.getWriter());

        return null;
    }

    public String saveCategory() throws Exception {

        String hrmDocumentId = request.getParameter("hrmDocumentId");
        String categoryId = request.getParameter("categoryId");

        boolean isHrm = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null);

        HRMCategory category = null;
        HRMDocument doc = null;

        if (isHrm) {

            category = hrmCategoryDao.find(Integer.parseInt(categoryId));
            if (category != null) {
                doc = hrmDocumentDao.find(Integer.parseInt(hrmDocumentId));

                if (doc != null) {
                    doc.setHrmCategoryId(Integer.parseInt(categoryId));
                    hrmCategoryDao.merge(doc);
                }
            }
        }


        JSONObject d = new JSONObject();

        if (doc != null && doc.getHrmCategoryId() != null) {
            HRMCategory setCat = hrmCategoryDao.find(doc.getHrmCategoryId());
            if (setCat != null) {
                d.put("value", setCat.getSubClassNameMnemonic() + ":" + setCat.getCategoryName());
            }
        }

        response.setContentType("text/x-json");
        d.write(response.getWriter());

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
        boolean isHrmAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm.administrator", "r", null);
        boolean isHrm = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "r", null);
        boolean isAdmin = securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.hrm", "r", null);


        String start = request.getParameter("start");
        String length = request.getParameter("length");

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
            providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
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

        JSONArray data = new JSONArray();

        long total = 0L;


        if (isHrm) {
            List<HRMDocument> docs = hrmDocumentDao.query(providerNo, "true".equals(providerUnmatched), "true".equals(noSignOff), "true".equals(demographicUnmatched), Integer.parseInt(start), Integer.parseInt(length), orderBy, orderingColumnDirection);

            total = hrmDocumentDao.queryForCount(providerNo, "true".equals(providerUnmatched), "true".equals(noSignOff), "true".equals(demographicUnmatched), Integer.parseInt(start), Integer.parseInt(length), orderBy, orderingColumnDirection);


            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            for (HRMDocument d : docs) {
                HRMCategory category = null;
                if (d.getHrmCategoryId() != null) {
                    category = hrmCategoryDao.find(d.getHrmCategoryId());
                }

                List<HRMDocumentToDemographic> ptList = hrmDocumentToDemographicDao.findByHrmDocumentId(d.getId().toString());
                Integer demographicNo = ptList.get(0).getDemographicNo();
                JSONObject data1 = new JSONObject();
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

                data1.put("report_date", reportDate != null ? reportDate : "");


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

                data1.put("received_date", fmt.format(d.getTimeReceived()));
                data1.put("category", category != null ? category.getCategoryName() : "");
                data1.put("description", d.getDescription());

                data.put(data1);
            }
        }

        JSONObject obj = new JSONObject();
        obj.put("draw", ++draw);
        obj.put("recordsTotal", total);
        obj.put("recordsFiltered", total);
        obj.put("data", data);

        response.setContentType("application/json");
        obj.write(response.getWriter());

        return null;
    }

}
