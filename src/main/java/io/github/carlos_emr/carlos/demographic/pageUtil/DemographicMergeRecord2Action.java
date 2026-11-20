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

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.demographic.data.DemographicMerged;

/**
 * @author wrighd
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Component(DemographicMergeRecord2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DemographicMergeRecord2Action extends ActionSupport {
    public static final String SPRING_BEAN_NAME = "demographicMergeMutation2Action";
    private static final String FAILURE = "failure";

    Logger logger = MiscUtils.getLogger();
    private final transient SecurityInfoManager securityInfoManager;
    private final transient DemographicDao demographicDao;
    private final transient TransactionTemplate transactions;

    /** Creates the merge action with an explicit transaction boundary. */
    public DemographicMergeRecord2Action(SecurityInfoManager securityInfoManager, DemographicDao demographicDao,
                                         PlatformTransactionManager transactionManager) {
        this.securityInfoManager = securityInfoManager;
        this.demographicDao = demographicDao;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() {

        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.requireLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
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
            if (!Boolean.TRUE.equals(transactions.execute(status -> mergeRecords(dmDAO, loggedInInfo, records, head)))) {
                logger.warn("Rejected demographic merge: a selected record is not an existing mergeable head record");
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
     * merged source. Every selected ID must name an existing patient before the first write, so a missing
     * source or target cannot leave orphan merge/privilege rows or a partially merged selection.
     * Patient locks are acquired in numeric order before reading merge state, and retained through every
     * merge, privilege and audit write. Canonical IDs are also used for the privilege and audit records.
     */
    private boolean mergeRecords(DemographicMerged dmDAO, LoggedInInfo loggedInInfo,
                                        List<String> records, String head) {
        try {
            Integer headId = Integer.valueOf(head);
            Set<Integer> recordIds = new LinkedHashSet<>();
            for (String recordId : records) {
                recordIds.add(Integer.valueOf(recordId));
            }
            if (recordIds.size() < 2 || !recordIds.contains(headId) || recordIds.stream().anyMatch(id -> id <= 0)) {
                return false;
            }
            // Lock the whole selection before any non-locking read establishes a repeatable-read snapshot.
            for (Integer id : recordIds.stream().sorted().toList()) {
                if (demographicDao.findForUpdate(id) == null) {
                    return false;
                }
            }
            if (recordIds.stream().anyMatch(dmDAO::isMergedSource)) {
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
