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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.commn.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.owasp.encoder.Encode;

import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.model.DxRegistedPTInfo;
import io.github.carlos_emr.carlos.managers.CodingSystemManager;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarDocumentCreator;
import io.github.carlos_emr.carlos.dxresearch.bean.dxCodeSearchBean;
import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListBeanHandler;
import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListItemsHandler;
import io.github.carlos_emr.carlos.dxresearch.util.dxResearchCodingSystem;


/**
 * @author toby
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class DxresearchReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final static String SUCCESS = "success";
    private final static String EDIT_DESC = "editdesc";
    private DxresearchDAO dxresearchdao = SpringUtils.getBean(DxresearchDAO.class);
    private MyGroupDao mygroupdao = SpringUtils.getBean(MyGroupDao.class);
    private static final String REPORTS_PATH = "org/oscarehr/common/web/DxResearchReport.jrxml";

    /**
     * Allowlist of valid values for the {@code radiovaluestatus} session attribute.
     * Note: "patientRegistedDistincted" is an intentional legacy identifier used consistently
     * across this Action class and the companion JSP (oscarReportDxReg.jsp). Changing it
     * would require a coordinated rename of both server-side method names and JSP references,
     * and is therefore preserved as-is to avoid breakage.
     */
    private static final Set<String> VALID_STATUS_VALUES = Set.of(
            "patientRegistedAll",
            "patientRegistedDistincted",
            "patientRegistedDeleted",
            "patientRegistedActive",
            "patientRegistedResolve"
    );

    /**
     * Validates that a provider_no parameter matches the expected format.
     * Provider numbers are alphanumeric strings (max 6 chars) optionally prefixed
     * with {@code _grp_} for group lookups. Logs a warning when validation fails.
     *
     * @param providerNo the provider number to validate
     * @return true if the value is non-null and matches the expected format
     */
    private static boolean isValidProviderNo(String providerNo) {
        if (providerNo != null && providerNo.matches("^(_grp_)?[a-zA-Z0-9]{1,6}$")) {
            return true;
        }
        MiscUtils.getLogger().warn("Invalid provider_no rejected: {}", LogSanitizer.sanitize(providerNo));
        return false;
    }

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("patientRegistedAll".equals(method)) {
            return patientRegistedAll();
        } else if ("patientExcelReport".equals(method)) {
            return patientExcelReport();
        } else if ("patientRegistedDistincted".equals(method)) {
            return patientRegistedDistincted();
        } else if ("patientRegistedDeleted".equals(method)) {
            return patientRegistedDeleted();
        } else if ("patientRegistedActive".equals(method)) {
            return patientRegistedActive();
        } else if ("patientRegistedResolve".equals(method)) {
            return patientRegistedResolve();
        } else if ("editDesc".equals(method)) {
            return editDesc();
        } else if ("addSearchCode".equals(method)) {
            return addSearchCode();
        } else if ("clearSearchCode".equals(method)) {
            return clearSearchCode();
        } else if ("getQuickListName".equals(method)) {
            return getQuickListName();
        }

        request.getSession().setAttribute("listview", new DxRegistedPTInfo());
        dxQuickListBeanHandler quicklistHd = new dxQuickListBeanHandler();
        request.getSession().setAttribute("allQuickLists", quicklistHd);
        dxResearchCodingSystem codingSys = new dxResearchCodingSystem();
        request.getSession().setAttribute("codingSystem", codingSys);
        // Whitelist-validate the existing session value before writing it back (CWE-501).
        // If the value is null or not in the allowlist, remove it from session so that the
        // JSP fallback defaults to "patientRegistedAll" (see oscarReportDxReg.jsp).
        String radiovaluestatus = (String) request.getSession().getAttribute("radiovaluestatus");
        if (radiovaluestatus != null && VALID_STATUS_VALUES.contains(radiovaluestatus)) {
            request.getSession().setAttribute("radiovaluestatus", radiovaluestatus);
        } else {
            request.getSession().removeAttribute("radiovaluestatus");
        }
        return SUCCESS;
    }

    public String patientRegistedAll() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (!isValidProviderNo(providerNo)) {
            return ERROR;
        }
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);


        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedAll(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedAll");
        return SUCCESS;
    }

    public String patientExcelReport() {
        ServletOutputStream outputStream = getServletOstream(response);

        List<DxRegistedPTInfo> patients = null;

        if (request.getSession().getAttribute("listview").getClass().getCanonicalName().contains("ArrayList")) {
            patients = (List<DxRegistedPTInfo>) request.getSession().getAttribute("listview");
        } else if (request.getSession().getAttribute("listview").getClass().getCanonicalName().contains("DxRegistedPTInfo")) {
            patients = new ArrayList<DxRegistedPTInfo>();
            DxRegistedPTInfo info = (DxRegistedPTInfo) request.getSession().getAttribute("listview");
            patients.add(info);
        }

        String providerNo = request.getParameter("provider_no");
        if (!isValidProviderNo(providerNo)) {
            return ERROR;
        }

        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
        }

        String mode = (String) request.getSession().getAttribute("radiovaluestatus");

        OscarDocumentCreator osc = new OscarDocumentCreator();
        HashMap<String, String> reportParams = new HashMap<String, String>();
        reportParams.put("providers", providerNo);
        reportParams.put("mode", mode);

        InputStream reportInstream = this.getClass().getClassLoader().getResourceAsStream(REPORTS_PATH);

        response.setContentType("application/excel");
        response.setHeader("Content-disposition", "inline; filename=dxResearchReport.xls");

        osc.fillDocumentStream(reportParams, outputStream, OscarDocumentCreator.EXCEL, reportInstream, patients);

        return null;
    }

    public String patientRegistedDistincted() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (!isValidProviderNo(providerNo)) {
            return ERROR;
        }
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);

        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedDistincted(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedDistincted");
        return SUCCESS;
    }

    protected ServletOutputStream getServletOstream(HttpServletResponse response) {
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
        } catch (IOException ex) {
            MiscUtils.getLogger().warn("Warning", ex);
        }
        return outputStream;
    }

    public String patientRegistedDeleted() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (!isValidProviderNo(providerNo)) {
            return ERROR;
        }
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);

        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedDeleted(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedDeleted");
        return SUCCESS;
    }

    public String patientRegistedActive() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (!isValidProviderNo(providerNo)) {
            return ERROR;
        }
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);

        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedActive(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedActive");
        return SUCCESS;
    }

    public String patientRegistedResolve() {

        List<String> providerNoList = new ArrayList<String>();
        String providerNo = request.getParameter("provider_no");
        if (!isValidProviderNo(providerNo)) {
            return ERROR;
        }
        if (providerNo.startsWith("_grp_")) {
            providerNo = providerNo.replaceFirst("_grp_", "");
            providerNoList = mygroupdao.getGroupDoctors(providerNo);
        } else
            providerNoList.add(providerNo);

        List codeSearch = (List) request.getSession().getAttribute("codeSearch");
        List patientInfo = dxresearchdao.patientRegistedResolve(codeSearch, providerNoList);
        request.getSession().setAttribute("listview", patientInfo);
        if (patientInfo == null || patientInfo.size() == 0) {
            request.getSession().setAttribute("Counter", 0);
        } else
            request.getSession().setAttribute("Counter", patientInfo.size());
        request.getSession().setAttribute("radiovaluestatus", "patientRegistedResolve");
        return SUCCESS;
    }

    public String editDesc() {
        String editingCodeType = request.getParameter("editingCodeType");
        String editingCodeCode = request.getParameter("editingCodeCode");
        String editingCodeDesc = request.getParameter("editingCodeDesc");

        dxQuickListItemsHandler.updatePatientCodeDesc(editingCodeType, editingCodeCode, editingCodeDesc);

        // Encode and length-limit before storing in session to prevent XSS and unbounded session storage (CWE-501)
        editingCodeDesc = Encode.forHtml(editingCodeDesc != null && editingCodeDesc.length() > 1000 ? editingCodeDesc.substring(0, 1000) : editingCodeDesc);
        request.getSession().setAttribute("editingCodeDesc", editingCodeDesc); // nosemgrep: tainted-session-from-http-request -- HTML-encoded before storage

        return SUCCESS;
    }

    @SuppressWarnings("unchecked")
    public String addSearchCode() {

        String quickListName = this.getQuickListName();
        List<dxCodeSearchBean> codeSearch = dxresearchdao.getQuickListItems(quickListName);
        String codeSingle = request.getParameter("codesearch");
        String codeSystem = request.getParameter("codesystem");
        String action = request.getParameter("action");
        dxCodeSearchBean newAddition = null;

        // check the code
        CodingSystemManager codingSystemManager = SpringUtils.getBean(CodingSystemManager.class);
        String codeDescription = null;

        if (codeSystem != null && !codeSystem.isEmpty()) {
            // Whitelist codeSystem against the known coding-system enum to prevent trust
            // boundary violation (CWE-501) before storing request data in session.
            String normalizedCodeSystem = codeSystem.toLowerCase().trim();
            try {
                // Use valueOf() as an allowlist check; the result is intentionally discarded —
                // only validation is needed here, and getCodeDescription() accepts the String form.
                AbstractCodeSystemDao.codingSystem.valueOf(normalizedCodeSystem);
                codeDescription = codingSystemManager.getCodeDescription(normalizedCodeSystem, codeSingle);
            } catch (IllegalArgumentException ignored) {
                MiscUtils.getLogger().warn("addSearchCode: rejected unrecognised coding system: {}",
                        Encode.forJava(codeSystem));
            }
        }

        if (codeDescription != null && !codeDescription.isEmpty()) {
            newAddition = new dxCodeSearchBean();
            newAddition.setType(codeSystem);
            newAddition.setDxSearchCode(codeSingle);
            newAddition.setDescription(codeDescription);
        }

        if (request.getSession().getAttribute("codeSearch") != null) {
            List<dxCodeSearchBean> existcodeSearch = (List<dxCodeSearchBean>) request.getSession().getAttribute("codeSearch");
            codeSearch.addAll(existcodeSearch);
        }
        if (newAddition != null) {
            codeSearch.add(newAddition);
        }

        request.getSession().setAttribute("codeSearch", codeSearch);
        return SUCCESS;
    }

    public String clearSearchCode() {

        List existcodeSearch = null;

        if (request.getSession().getAttribute("codeSearch") != null && ((List) (request.getSession().getAttribute("codeSearch"))).size() > 0) {
            existcodeSearch = (List) (request.getSession().getAttribute("codeSearch"));
            existcodeSearch.clear();
        }

        request.getSession().setAttribute("codeSearch", existcodeSearch);

        return SUCCESS;
    }

    private String quickListName;

    public String getQuickListName() {
        return quickListName;
    }

    @StrutsParameter
    public void setQuickListName(String quickListName) {
        this.quickListName = quickListName;
    }
}
