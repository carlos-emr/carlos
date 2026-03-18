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

package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.utility.CppUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

public class EctDisplayResolvedIssues2Action extends EctDisplayAction {
    private String cmd = "resolvedIssues";


    private CaseManagementManager caseManagementMgr;

    public void setCaseManagementManager(CaseManagementManager caseManagementMgr) {
        this.caseManagementMgr = caseManagementMgr;
    }

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO navBarDisplayDAO) {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // set lefthand module heading and link
        navBarDisplayDAO.setLeftHeading(getText("oscarEncounter.NavBar.resolvedIssues"));

        navBarDisplayDAO.setLeftURL("$('check_issue').value='';document.caseManagementViewForm.submit();");

        // set righthand link to same as left so we have visual consistency with other modules
        String url = "return false;";
        navBarDisplayDAO.setRightURL(url);
        navBarDisplayDAO.setRightHeadingID(cmd); // no menu so set div id to unique id for this action

        // grab all of the diseases associated with patient and add a list item for each
        List<CaseManagementIssue> issues = null;
        int demographicId = Integer.parseInt(bean.getDemographicNo());
        issues = caseManagementMgr.getIssues(demographicId);
        String programId = (String) request.getSession().getAttribute("case_program_id");
        issues = caseManagementMgr.filterIssues(loggedInInfo, providerNo, issues, programId);

        List<CaseManagementIssue> issues_unr = new ArrayList<CaseManagementIssue>();
        //only list resolved issues
        for (CaseManagementIssue issue : issues) {
            if (containsIssue(CppUtils.cppCodes, issue.getIssue().getCode())) {
                continue;
            }

            if (issue.isResolved()) {
                issues_unr.add(issue);
            }
        }


        for (int idx = 0; idx < issues_unr.size(); ++idx) {
            NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();

            CaseManagementIssue issue = issues_unr.get(idx);
            String tmp = issue.getIssue().getDescription();

            String strTitle = StringUtils.maxLenString(tmp, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);

            item.setTitle(strTitle);
            item.setLinkTitle(tmp);
            //issues value=
            url = "setIssueCheckbox('" + issue.getId() + "');return filter(false);";
            item.setURL(url);
            navBarDisplayDAO.addItem(item);
        }


        return true;
    }

    public String getCmd() {
        return cmd;
    }

    public boolean containsIssue(String[] issues, String issueCode) {
        for (String caseManagementIssue : issues) {
            if (caseManagementIssue.equals(issueCode)) {
                return (true);
            }
        }
        return false;
    }
}
