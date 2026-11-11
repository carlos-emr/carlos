/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.PMmodule.service.ClientManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;

import io.github.carlos_emr.carlos.services.LookupManager;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ClientSearchAction22Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final Logger logger = MiscUtils.getLogger();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private LookupManager lookupManager = SpringUtils.getBean(LookupManager.class);
    private ClientManager clientManager = SpringUtils.getBean(ClientManager.class);
    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);

    private CaseManagementNoteDAO caseManagementNoteDao = SpringUtils.getBean(CaseManagementNoteDAO.class);

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pmm_management", "r", null)) {
            throw new SecurityException("missing required sec object (_pmm_management)");
        }

        String method = request.getParameter("method");
        if ("attachForm".equals(method)) {
            return attachForm();
        } else if ("search".equals(method)) {
            return search();
        } else if ("attachSearch".equals(method)) {
            return attachSearch();
        }
        return form();
    }

    public String form() {
        if (clientManager.isOutsideOfDomainEnabled()) {
            request.getSession().setAttribute("outsideOfDomainEnabled", "true"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        } else {
            request.getSession().setAttribute("outsideOfDomainEnabled", "false"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        }


        request.setAttribute("genders", lookupManager.LoadCodeList("GEN", true, null, null));

        return "form";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String attachForm() {
        if (clientManager.isOutsideOfDomainEnabled()) {
            request.getSession().setAttribute("outsideOfDomainEnabled", "true"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        } else {
            request.getSession().setAttribute("outsideOfDomainEnabled", "false"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        }

        String noteId = request.getParameter("noteId");
        if (noteId == null || noteId.trim().length() == 0 || noteId.trim().equalsIgnoreCase("null") || noteId.trim().substring(0, 1).equalsIgnoreCase("0")) {
            String demographicNo = request.getParameter("demographicNo");
            if (demographicNo == null || demographicNo.trim().length() == 0) {
                //don't do anything?
            } else {
                List<CaseManagementNote> notes = caseManagementNoteDao.getNotesByDemographic(demographicNo);
                if (notes != null && notes.size() > 0) noteId = notes.get(notes.size() - 1).getId() + "";
            }
        }
        publishAttachNote(noteId);


        request.setAttribute("genders", lookupManager.LoadCodeList("GEN", true, null, null));

        return "attachSearch";
    }

    public String search() {


        ClientSearchFormBean formBean = this.getCriteria();


        formBean.setProgramDomain((List) request.getSession().getAttribute("program_domain"));

        /* do the search */
        request.setAttribute("clients", clientManager.search(formBean));

        if (formBean.isSearchOutsideDomain()) {
            LogAction.log("read", "out of domain client search", "", request);
        }
        request.setAttribute("genders", lookupManager.LoadCodeList("GEN", true, null, null));

        return "form";
    }

    public String attachSearch() {
        // The note this popup attaches, posted back by its own search form.
        publishAttachNote(request.getParameter("noteId"));

        ClientSearchFormBean formBean = this.getCriteria();


        formBean.setProgramDomain((List) request.getSession().getAttribute("program_domain"));

        /* do the search */
        int requestedPage;
        int sort;
        boolean descending = "true".equals(request.getParameter("tableDescending"));
        try {
            requestedPage = io.github.carlos_emr.carlos.utility.ServerTablePage.requested(request);
            sort = request.getParameter("tableSort") == null ? 0 : Integer.parseInt(request.getParameter("tableSort"));
            String change = request.getParameter("tableSortChange");
            if (change != null) {
                int changed = Integer.parseInt(change);
                descending = changed == sort && !descending;
                sort = changed;
                requestedPage = 0;
            }
            if (sort < 0 || sort > 7) throw new IllegalArgumentException("Invalid table sort");
        } catch (IllegalArgumentException invalidPage) { response.setStatus(400); return NONE; }
        var page = clientManager.searchClientPage(formBean, requestedPage, sort, descending);
        request.setAttribute("clients", page.getRows());
        java.util.Map<String, String[]> parameters = new java.util.LinkedHashMap<>();
        request.getParameterMap().forEach((key, value) -> {
            if (key.startsWith("criteria.") || java.util.Set.of("noteId", "sourceChartContext", "chartContext").contains(key))
                parameters.put(key, value);
        });
        parameters.put("method", new String[]{"attachSearch"});
        parameters.put("tableSort", new String[]{Integer.toString(sort)});
        parameters.put("tableDescending", new String[]{Boolean.toString(descending)});
        request.setAttribute("serverTableSort", sort);
        request.setAttribute("serverTableDescending", descending);
        io.github.carlos_emr.carlos.utility.ServerTablePage.publish(request, page,
                "/PMmodule/ClientSearch2", "post", parameters);

        if (formBean.isSearchOutsideDomain()) {
            LogAction.log("read", "out of domain client search", "", request);
        }
        request.setAttribute("genders", lookupManager.LoadCodeList("GEN", true, null, null));

        return "attachSearch";
    }


    /**
     * Hands the attach page the note it attaches, when {@code noteId} names an existing note:
     * the page's search form posts it back and its client links carry it to the attach step.
     *
     * <p>It used to be kept in the session as well, and the page read it from there, so with two
     * charts' attach popups open the first popup's search linked to the note of the second, which
     * the attach step then saved into the chosen client's chart (issue #4673).</p>
     */
    private void publishAttachNote(String noteId) {
        if (noteId == null || noteId.trim().length() == 0) {
            return;
        }
        // Parse and validate noteId before the page uses it (CWE-501: Trust Boundary Violation).
        // Reject non-numeric values and confirm the note record exists in the database.
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = (loggedInInfo != null) ? loggedInInfo.getLoggedInProviderNo() : "unknown";
        try {
            long parsedNoteId = Long.parseLong(noteId.trim());
            CaseManagementNote note = caseManagementNoteDao.getNote(parsedNoteId);
            if (note != null) {
                String sourceContext = io.github.carlos_emr.carlos.casemgmt.common.NoteAttachContext.sourceToken(
                        request, note.getDemographic_no());
                if (sourceContext != null) {
                    request.setAttribute("sourceChartContext", sourceContext);
                }
                request.setAttribute("noteId", String.valueOf(parsedNoteId));
            } else {
                logger.warn("Rejected noteId that does not exist in database (trust boundary enforcement) - provider: {}", providerNo);
            }
        } catch (NumberFormatException e) {
            logger.warn("Rejected non-numeric noteId parameter (trust boundary enforcement) - provider: {}", providerNo);
        }
    }

    public void setLookupManager(LookupManager lookupManager) {
        this.lookupManager = lookupManager;
    }

    public void setClientManager(ClientManager mgr) {
        this.clientManager = mgr;
    }

    public void setProgramManager(ProgramManager mgr) {
        this.programManager = mgr;
    }

    private ClientSearchFormBean criteria;

    @StrutsParameter(depth = 1)
    public ClientSearchFormBean getCriteria() {
        return criteria;
    }

    @StrutsParameter
    public void setCriteria(ClientSearchFormBean criteria) {
        this.criteria = criteria;
    }
}
