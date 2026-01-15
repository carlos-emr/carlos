//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.PMmodule.web;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.PMmodule.service.ClientManager;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.web.formbean.ClientSearchFormBean;
import ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;

import ca.openosp.openo.services.LookupManager;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ClientSearchAction22Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private LookupManager lookupManager = SpringUtils.getBean(LookupManager.class);
    private ClientManager clientManager = SpringUtils.getBean(ClientManager.class);
    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);

    private CaseManagementNoteDAO caseManagementNoteDao = SpringUtils.getBean(CaseManagementNoteDAO.class);

    public String execute() {
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
            request.getSession().setAttribute("outsideOfDomainEnabled", "true");
        } else {
            request.getSession().setAttribute("outsideOfDomainEnabled", "false");
        }


        request.setAttribute("genders", lookupManager.LoadCodeList("GEN", true, null, null));

        return "form";
    }

    public String attachForm() {
        if (clientManager.isOutsideOfDomainEnabled()) {
            request.getSession().setAttribute("outsideOfDomainEnabled", "true");
        } else {
            request.getSession().setAttribute("outsideOfDomainEnabled", "false");
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
        if (noteId == null || noteId.trim().length() == 0) {
            //don't do anything?
        } else {
            request.getSession().setAttribute("noteId", noteId);
            request.setAttribute("noteId", noteId);
        }


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


        ClientSearchFormBean formBean = this.getCriteria();


        formBean.setProgramDomain((List) request.getSession().getAttribute("program_domain"));

        /* do the search */
        request.setAttribute("clients", clientManager.search(formBean));

        if (formBean.isSearchOutsideDomain()) {
            LogAction.log("read", "out of domain client search", "", request);
        }
        request.setAttribute("genders", lookupManager.LoadCodeList("GEN", true, null, null));

        return "attachSearch";
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

    public ClientSearchFormBean getCriteria() {
        return criteria;
    }

    public void setCriteria(ClientSearchFormBean criteria) {
        this.criteria = criteria;
    }
}
