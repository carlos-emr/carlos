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
package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.dao.Icd10Dao;
import io.github.carlos_emr.carlos.commn.dao.Icd9Dao;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import io.github.carlos_emr.carlos.commn.model.Icd10;
import io.github.carlos_emr.carlos.commn.model.Icd9;
import io.github.carlos_emr.carlos.managers.CodingSystemManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.JsonUtil;
import io.github.carlos_emr.carlos.utility.JsonResponseWriter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class dxCodeSearchJSON2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Logger logger = MiscUtils.getLogger();

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!hasCodeSearchPrivilege(loggedInInfo)) {
            throw new SecurityException("missing required sec object (_dxresearch/_rx/_billing/_report r)");
        }
        String method = request.getParameter("method");
        if ("searchICD9".equals(method)) {
            return searchICD9();
        } else if ("searchICD10".equals(method)) {
            return searchICD10();
        } else if ("searchMSP".equals(method)) {
            return searchMSP();
        } else if ("validateCode".equals(method)) {
            return validateCode();
        } else if ("getDescription".equals(method)) {
            return getDescription();
        } 
        return getDescription();
        
    }

    /**
     * Shared code-search JSON is used by dxresearch, prescribing, billing,
     * and reporting workflows. Keep the endpoint protected, but authorize
     * callers based on the workflow privileges that already gate those UIs.
     */
    private boolean hasCodeSearchPrivilege(LoggedInInfo loggedInInfo) {
        if (loggedInInfo == null) {
            return false;
        }

        return securityInfoManager.hasPrivilege(loggedInInfo, "_dxresearch", SecurityInfoManager.READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_billing", SecurityInfoManager.READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.reporting", SecurityInfoManager.READ, null);
    }

    @SuppressWarnings("unused")
    public String searchICD9() {

        String keyword = request.getParameter("keyword");
        keyword = StringUtils.trimToEmpty(keyword);
        Icd9Dao dao = SpringUtils.getBean(Icd9Dao.class);
        List<Icd9> icd9List = dao.getIcd9(keyword);

        try {
            jsonify(icd9List, response, new String[]{
                    "handler",
                    "hibernateLazyInitializer"});
        } catch (IOException e) {
            logger.error("JSON Error", e);
        }

        return null;
    }

    @SuppressWarnings("unused")
    public String searchICD10() {

        String keyword = request.getParameter("keyword");
        keyword = StringUtils.trimToEmpty(keyword);
        Icd10Dao dao = SpringUtils.getBean(Icd10Dao.class);
        List<Icd10> icd10List = dao.searchCode(keyword);

        try {
            jsonify(icd10List, response, new String[]{
                    "handler",
                    "hibernateLazyInitializer"});
        } catch (IOException e) {
            logger.error("JSON Error", e);
        }

        return null;
    }

    /**
     * This method searches the table by text description only. NOT by code.
     * This is intentional for smooth operation in BC Billing.
     *
     * @return JSON result string
     */
    @SuppressWarnings("unused")
    public String searchMSP() {

        String keyword = request.getParameter("keyword");
        keyword = StringUtils.trimToEmpty(keyword);
        DiagnosticCodeDao dao = SpringUtils.getBean(DiagnosticCodeDao.class);
        List<DiagnosticCode> mspCodeList = dao.search(keyword);

        try {
            jsonify(mspCodeList, response, new String[]{
                    "handler",
                    "hibernateLazyInitializer"});
        } catch (IOException e) {
            logger.error("JSON Error", e);
        }

        return null;
    }

    @SuppressWarnings("unused")
    public String validateCode() {

        String code = request.getParameter("keyword");
        code = StringUtils.trimToEmpty(code);
        String codeSystem = request.getParameter("codeSystem");
        CodingSystemManager codingSystemManager = SpringUtils.getBean(CodingSystemManager.class);
        boolean dxvalid = codingSystemManager.isCodeAvailable(codeSystem, code);

        ObjectNode jsonResponse = objectMapper.createObjectNode();
        jsonResponse.put("dxvalid", dxvalid);

        try {
            JsonResponseWriter.write(response, jsonResponse);
        } catch (IOException e) {
            logger.error("JSON Error", e);
        }

        return null;
    }

    @SuppressWarnings("unused")
    public String getDescription() {

        String code = request.getParameter("keyword");
        code = StringUtils.trimToEmpty(code);
        String codeSystem = request.getParameter("codeSystem");

        CodingSystemManager codingSystemManager = SpringUtils.getBean(CodingSystemManager.class);
        String description = codingSystemManager.getCodeDescription(codeSystem, code);
        boolean dxvalid = (description != null);

        ObjectNode jsonResponse = objectMapper.createObjectNode();
        jsonResponse.put("dxvalid", dxvalid);
        jsonResponse.put("description", description);
        jsonResponse.put("code", code);

        try {
            JsonResponseWriter.write(response, jsonResponse);
        } catch (IOException e) {
            logger.error("JSON Error", e);
        }

        return null;
    }

    private static void jsonify(final List<?> classList,
                                final HttpServletResponse response, String[] ignoreMethods) throws IOException {

        String jsonString = "{}";

        if (classList != null && !classList.isEmpty()) {
            jsonString = JsonUtil.pojoCollectionToJson(classList, ignoreMethods);
        }

        JsonResponseWriter.write(response, jsonString);
    }


}
