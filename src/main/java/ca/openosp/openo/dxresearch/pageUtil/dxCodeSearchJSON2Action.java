//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.dxresearch.pageUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.DiagnosticCodeDao;
import ca.openosp.openo.commn.dao.Icd10Dao;
import ca.openosp.openo.commn.dao.Icd9Dao;
import ca.openosp.openo.commn.model.DiagnosticCode;
import ca.openosp.openo.commn.model.Icd10;
import ca.openosp.openo.commn.model.Icd9;
import ca.openosp.openo.managers.CodingSystemManager;
import ca.openosp.openo.utility.JsonUtil;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class dxCodeSearchJSON2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static Logger logger = MiscUtils.getLogger();

    public String execute() {
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

        response.setContentType("text/x-json");

        try (PrintWriter pout = response.getWriter()) {
            pout.write(jsonResponse.toString());
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

        response.setContentType("text/x-json");

        try (PrintWriter pout = response.getWriter()) {
            pout.write(jsonResponse.toString());
        } catch (IOException e) {
            logger.error("JSON Error", e);
        }

        return null;
    }

    private static void jsonify(final List<?> classList,
                                final HttpServletResponse response, String[] ignoreMethods) throws IOException {

        response.setContentType("text/x-json");

        String jsonstring = null;

        if (classList != null && !classList.isEmpty()) {
            jsonstring = JsonUtil.pojoCollectionToJson(classList, ignoreMethods);
        }

        if (jsonstring == null) {
            jsonstring = "{}";
        }

        try (PrintWriter pout = response.getWriter()) {
            pout.write(jsonstring);
        }
    }


}
