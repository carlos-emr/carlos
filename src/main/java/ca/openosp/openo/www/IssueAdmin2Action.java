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

package ca.openosp.openo.www;

import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.services.IssueAdminManager;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.commn.dao.SecRoleDao;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

// use your IDE to handle imports
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class IssueAdmin2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    private IssueAdminManager mgr = SpringUtils.getBean(IssueAdminManager.class);

    private SecRoleDao secRoleDao = SpringUtils.getBean(SecRoleDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        String mtd = request.getParameter("method");
        if ("cancel".equals(mtd)) {
            return cancel();
        } else if ("delete".equals(mtd)) {
            return delete();
        } else if ("edit".equals(mtd)) {
            return edit();
        } else if ("save".equals(mtd)) {
            return save();
        }
        return list();
    }

    public String cancel() {
        return list();
    }

    public String delete() {
        if (log.isDebugEnabled()) {
            log.debug("entering 'delete' method...");
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        mgr.removeIssueAdmin(request.getParameter("issueAdmin.id"));
        addActionMessage(getText("issueAdmin.deleted"));

        return list();
    }

    public String edit() {
        if (log.isDebugEnabled()) {
            log.debug("entering 'edit' method...");
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String issueAdminId = request.getParameter("id");
        // null issueAdminId indicates an add
        if (issueAdminId != null) {
            Issue issueAdmin = mgr.getIssueAdmin(issueAdminId);
            if (issueAdmin == null) {
                addActionError(getText("issueAdmin.missing"));
                return "list";
            }
            request.setAttribute("issueRole", issueAdmin.getRole());
            this.setIssueAdmin(issueAdmin);
        }

        request.setAttribute("caisiRoles", secRoleDao.findAll());
        return "edit";
    }

    public String list() {
        if (log.isDebugEnabled()) {
            log.debug("entering 'list' method...");
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        request.setAttribute("issueAdmins", mgr.getIssueAdmins());
        return "list";
    }

    public String save() {
        if (log.isDebugEnabled()) {
            log.debug("entering 'save' method...");
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        // run validation rules on this form
//        ActionMessages errors = form.validate(mapping, request);
//        if (!errors.isEmpty()) {
//            saveErrors(request, errors);
//            // request.setAttribute("caisiRoles", caisiRoleMgr.getRoles());
//            return "edit";
//        }

        //issue code cannot be duplicated
        String newCode = issueAdmin.getCode();
        String newId = String.valueOf(issueAdmin.getId());
        List<Issue> issueAdmins = mgr.getIssueAdmins();
        for (Iterator<Issue> it = issueAdmins.iterator(); it.hasNext(); ) {
            Issue issueAdmin = it.next();
            String existCode = issueAdmin.getCode();
            String existId = String.valueOf(issueAdmin.getId());
            if ((existCode.equals(newCode)) && !(existId.equals(newId))) {
                addActionError(getText("issueAdmin.code.exist"));
                //request.setAttribute("caisiRoles", caisiRoleMgr.getRoles());
                return "edit";
            }
        }

        mgr.saveIssueAdmin(issueAdmin);
        addActionMessage(getText("issueAdmin.saved"));

        return list();
    }

    private Issue issueAdmin;

    public Issue getIssueAdmin() {
        return issueAdmin;
    }

    public void setIssueAdmin(Issue issueAdmin) {
        this.issueAdmin = issueAdmin;
    }
}
