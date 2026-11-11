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
import java.util.Set;

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

        if ("merge".equals(action)) {
            if (!mergeRecords(dmDAO, loggedInInfo, records, head)) {
                logger.warn("Rejected demographic merge: a selected record is not a mergeable head record");
                outcome = FAILURE;
            }

        } else if ("unmerge".equals(action) && records.size() > 0) {
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
     * Validates the complete selection before merging any records. IDs are deduplicated and compared by
     * numeric identity, so alternate spellings cannot cause a self-merge or a repeated merge after an earlier
     * write. The target must be selected alongside at least one other record, and none may already be a
     * merged source. Canonical IDs are also used for the merge's privilege and audit records.
     */
    private static boolean mergeRecords(DemographicMerged dmDAO, LoggedInInfo loggedInInfo,
                                        List<String> records, String head) {
        try {
            Integer headId = Integer.valueOf(head);
            Set<Integer> recordIds = new LinkedHashSet<>();
            for (String recordId : records) {
                recordIds.add(Integer.valueOf(recordId));
            }
            if (recordIds.size() < 2 || !recordIds.contains(headId)
                    || recordIds.stream().anyMatch(dmDAO::isMergedSource)) {
                return false;
            }
            for (Integer recordId : recordIds) {
                if (!recordId.equals(headId)) {
                    dmDAO.Merge(loggedInInfo, recordId.toString(), headId.toString());
                }
            }
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }
}
