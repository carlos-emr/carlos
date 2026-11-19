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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * @author Toby
 */

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class BillingreferralEdit2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ProfessionalSpecialistDao psDao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute() throws Exception {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        String method = request.getParameter("method");
        if ("searchByNo".equals(method)) {
            return searchByNo();
        } else if ("searchBySpecialty".equals(method)) {
            return searchBySpecialty();
        } else if ("searchByName".equals(method)) {
            return searchByName();
        } else if ("modifyBatch".equals(method)) {
            return modifyBatch();
        } else if ("advancedSearch".equals(method)) {
            return advancedSearch();
        }
        return list();
    }

    public String list() {
        return directoryPage(null, null, null, null, null, true, "searchByName");
    }

    public String searchByNo() {
        return directoryPage(search, null, null, null, null, true, "searchByNo");
    }

    public String searchBySpecialty() {
        return directoryPage(null, null, null, search, null, true, "searchBySpecialty");
    }

    public String searchByName() {
        String[] names = (search == null ? "" : search).split(",", 2);
        return directoryPage(null, names[0].trim(), names.length == 2 ? names[1].trim() : "",
                null, null, true, "searchByName");
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String modifyBatch() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing write)");
        }
        String referralId = request.getParameter("id");
        String checked = request.getParameter("checked");
        String clear = request.getParameter("clear");

        List<ProfessionalSpecialist> checkedSpecs = (List<ProfessionalSpecialist>) request.getSession().getAttribute("billingReferralAdminCheckList");
        if (checkedSpecs == null) {
            checkedSpecs = new ArrayList<ProfessionalSpecialist>();
        }

        if ("true".equals(clear)) {
            //empty list
            checkedSpecs.clear();
        }

        if ("true".equals(checked)) {
            //add to list
            ProfessionalSpecialist ps = psDao.find(Integer.parseInt(referralId));
            if (ps != null && !checkedSpecs.contains(ps)) {
                checkedSpecs.add(ps);
            }
        } else {
            //remove from list
            ProfessionalSpecialist tmp = null;
            for (ProfessionalSpecialist ps : checkedSpecs) {
                if (ps.getId().intValue() == Integer.parseInt(referralId)) {
                    tmp = ps;
                    break;
                }
            }
            if (tmp != null) {
                checkedSpecs.remove(tmp);
            }
        }

        request.getSession().setAttribute("billingReferralAdminCheckList", checkedSpecs); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep

        ArrayNode arr = objectMapper.valueToTree(checkedSpecs);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(arr); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- JSON API response with application/json content-type

        return null;
    }

    public String advancedSearch() {
        String name = request.getParameter("nameQuery");
        String specialty = request.getParameter("specialtyQuery");
        String address = request.getParameter("addressQuery");
        boolean showHidden = "on".equals(request.getParameter("showHidden"));
        request.setAttribute("name", name);
        request.setAttribute("specialty", specialty);
        request.setAttribute("address", address);
        request.setAttribute("showHidden", showHidden);
        Integer referralNumber = null;
        if (name != null && !name.isEmpty()) {
            try { referralNumber = Integer.valueOf(name); }
            catch (NumberFormatException notReferralNumber) {
                // Preserve the legacy fallback: nonnumeric or overflowing input is a name.
            }
        }
        if (referralNumber != null) {
            return directoryPage(referralNumber.toString(), null, null, null, null, true, "searchByName");
        }
        String[] names = (name == null ? "" : name).split(",", 2);
        return directoryPage(null, names[0].trim(), names.length == 2 ? names[1].trim() : "",
                specialty, address, showHidden, "searchByName");
    }

    private String directoryPage(String referral, String last, String first, String specialty,
            String address, boolean includeHidden, String searchBy) {
        int requestedPage;
        try { requestedPage = io.github.carlos_emr.carlos.utility.ServerTablePage.requested(request); }
        catch (IllegalArgumentException invalidPage) {
            ServletActionContext.getResponse().setStatus(400);
            return NONE;
        }
        var page = psDao.findDirectoryPage(referral, last, first, specialty, address, includeHidden, requestedPage);
        request.setAttribute("referrals", page.getRows());
        java.util.Set<Integer> selectedIds = new java.util.HashSet<>();
        Object selected = request.getSession().getAttribute("billingReferralAdminCheckList");
        if (selected instanceof List<?> rows) {
            for (Object row : rows) if (row instanceof ProfessionalSpecialist specialist) selectedIds.add(specialist.getId());
        }
        request.setAttribute("selectedReferralIds", selectedIds);
        request.setAttribute("searchBy", searchBy);
        java.util.Map<String, String[]> parameters = new java.util.LinkedHashMap<>();
        for (String key : List.of("method", "search", "nameQuery", "specialtyQuery", "addressQuery", "showHidden")) {
            if (request.getParameterValues(key) != null) parameters.put(key, request.getParameterValues(key));
        }
        io.github.carlos_emr.carlos.utility.ServerTablePage.publish(request, page,
                "/admin/ManageBillingReferral", "get", parameters);
        return "list";
    }

    private String search;

    public String getSearch() {
        return search;
    }

    @StrutsParameter
    public void setSearch(String search) {
        this.search = search;
    }
}
