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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.daos.DefaultIssueDao;
import ca.openosp.openo.model.DefaultIssue;
import ca.openosp.openo.casemgmt.dao.IssueDAO;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;

/**
 * @author Administrator
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DefaultEncounterIssue2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();



    public String execute() {
        String method = request.getParameter("method");
        if ("edit".equals(method)) {
            return edit();
        } else if ("editRemove".equals(method)) {
            return editRemove();
        }  else if ("save".equals(method)) {
            return save();
        }  else if ("remove".equals(method)) {
            return remove();
        } 
        return list();
    }

    public String list() {
        DefaultIssueDao issueDao = SpringUtils.getBean(DefaultIssueDao.class);
        if (issueDao == null) {
            return "list";
        }
        List<DefaultIssue> issueList = issueDao.findAll();
        if (issueList != null && issueList.size() > 0) {
            request.setAttribute("issueList", (issueList != null) ? issueList : new ArrayList<DefaultIssue>());
        }
        return "list";
    }

    private boolean saveDefaultIssuesToDb(String providerNo, String issueIds) {
        if (issueIds == null || issueIds.length() == 0) {
            return false;
        }
        DefaultIssueDao issueDao = SpringUtils.getBean(DefaultIssueDao.class);
        if (issueDao == null) {
            return false;
        }

        DefaultIssue defaultIssue = issueDao.getLastestDefaultIssue();
        if (defaultIssue == null) {
            defaultIssue = new DefaultIssue();
            defaultIssue.setAssignedtime(new Date());
        }
        defaultIssue.setUpdatetime(new Date());
        defaultIssue.setProviderNo(providerNo);
        defaultIssue.setIssueIds(issueIds);
        issueDao.saveDefaultIssue(defaultIssue);
        return true;
    }

    public String edit() {
        return "edit";
    }

    public String editRemove() {
        return "editRemove";
    }

    public String save() {
        String issueNames = request.getParameter("issueNames");
        if (issueNames == null || issueNames.length() == 0) {
            return "list";
        }
        IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
        if (issueDao == null) {
            return "list";
        }

        Set<Long> issueIdSet = new HashSet<Long>();
        String[] issueNamesArr = issueNames.split(",");
        for (String issueName : issueNamesArr) {
            List<Issue> issueList = issueDao.findIssueBySearch(issueName.trim());
            if (issueList == null || issueList.size() == 0) {
                continue;
            }
            for (Issue issue : issueList) {
                issueIdSet.add(issue.getId());
            }
        }

        if (issueIdSet.size() == 0) {
            return "list";
        }

        StringBuilder strIds = new StringBuilder();
        for (Long issueId : issueIdSet) {
            strIds.append(issueId.toString() + ",");
        }
        strIds.deleteCharAt(strIds.length() - 1);

        LogAction.log("write", "assign default issues", strIds.toString(), request);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        saveDefaultIssuesToDb(loggedInInfo.getLoggedInProviderNo(), strIds.toString());

        return list();
    }

    private void removeDefaultIssues(Set<Long> issueIdSet) {
        DefaultIssueDao issueDao = SpringUtils.getBean(DefaultIssueDao.class);
        if (issueDao == null) {
            return;
        }
        DefaultIssue issue = issueDao.getLastestDefaultIssue();
        if (issue == null) {
            return;
        }
        String issueIds = issue.getIssueIds();
        String[] issueIdArr = issueIds.split(",");
        StringBuilder strIds = new StringBuilder();
        for (String issueId : issueIdArr) {
            try {
                if (!issueIdSet.contains(Long.parseLong(issueId))) { // this issueId needn't to remove
                    strIds.append(issueId + ",");
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().info("Failed to parse the issue id!");
            }
        }

        if (strIds.length() == 0) { // delete the default issue's record
            issueDao.remove(issue.getId());
        } else {
            strIds.deleteCharAt(strIds.length() - 1);
            issue.setIssueIds(strIds.toString());
            issueDao.saveDefaultIssue(issue);
        }
    }

    public String remove() {
        String issueNames = request.getParameter("issueNames");
        if (issueNames == null || issueNames.length() == 0) {
            return "list";
        }
        IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
        if (issueDao == null) {
            return "list";
        }

        Set<Long> issueIdSet = new HashSet<Long>();
        String[] issueNamesArr = issueNames.split(",");
        StringBuilder idSb = new StringBuilder();
        for (String issueName : issueNamesArr) {
            List<Issue> issueList = issueDao.findIssueBySearch(issueName.trim());
            if (issueList == null || issueList.size() == 0) {
                continue;
            }
            for (Issue issue : issueList) {
                issueIdSet.add(issue.getId());
                idSb.append(issue.getId().toString() + ",");
            }
        }

        if (issueIdSet.size() == 0) {
            return list();
        }

        idSb.deleteCharAt(idSb.length() - 1);

        LogAction.log("remove", "remove default issues", idSb.toString(), request);

        removeDefaultIssues(issueIdSet);

        return list();
    }

}
