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


/*
 * DemographicMergeRecord2Action.java
 *
 * Created on September 11, 2007, 3:52 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.demographic.data.DemographicMerged;

/**
 * @author wrighd
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class DemographicMergeRecord2Action extends ActionSupport {
    private static final String FAILURE = "failure";

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public DemographicMergeRecord2Action() {

    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (request.getParameterValues("records") == null) {
            return FAILURE;
        }
        String outcome = "success";
        ArrayList<String> records = new ArrayList<String>(Arrays.asList(request.getParameterValues("records")));
        String head = request.getParameter("head");
        String action = request.getParameter("mergeAction");
        String provider_no = request.getParameter("provider_no");
        DemographicMerged dmDAO = new DemographicMerged();

        if (action.equals("merge") && head != null && records.size() > 1 && records.contains(head)) {
            records = new ArrayList<String>(new LinkedHashSet<String>(records));

            // Validate every id before the first write so a rejected request leaves no partial merge behind.
            if (!isMergeable(dmDAO, records, head)) {
                logger.warn("Rejected demographic merge: a selected record is not a mergeable head record");
                outcome = FAILURE;
            } else {
                for (String recordId : records) {
                    if (!recordId.equals(head))
                        dmDAO.Merge(loggedInInfo, recordId, head);

                }
            }

        } else if (action.equals("unmerge") && records.size() > 0) {
            outcome = "successUnMerge";
            for (int i = 0; i < records.size(); i++) {
                String demographic_no = records.get(i);
                dmDAO.UnMerge(loggedInInfo, demographic_no, provider_no);

            }

        } else {
            outcome = FAILURE;
        }
        request.setAttribute("mergeoutcome", outcome);

        if (request.getParameter("caisiSearch") != null && request.getParameter("caisiSearch").equalsIgnoreCase("yes")) {
            outcome = "caisiSearch";
        }

        return outcome;
    }

    /**
     * Server-side guard for a merge request. The merge page only offers head records, but the request is
     * attacker-controllable, so each id must be numeric and none of the selected records, the target
     * included, may already be merged into another record: merging a merged source (or into one) can close a
     * loop in {@code demographic_merged}, which makes {@code getHead} and lab routing recurse forever.
     */
    private static boolean isMergeable(DemographicMerged dmDAO, List<String> records, String head) {
        try {
            for (String recordId : records) {
                if (dmDAO.isMergedSource(Integer.parseInt(recordId))) {
                    return false;
                }
            }
            return !dmDAO.isMergedSource(Integer.parseInt(head));
        } catch (NumberFormatException _) {
            return false;
        }
    }
}
