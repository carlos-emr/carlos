/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.sms.admin;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.assembler.SmsQueueViewModelAssembler;
import io.github.carlos_emr.carlos.sms.service.SmsPatientRestrictionLookup;
import io.github.carlos_emr.carlos.sms.service.SmsQueueViewAuditRecorder;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueWindow;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.Set;

/**
 * Administration &gt; SMS &gt; SMS queue ({@code admin/SmsQueue}): a read-only operational view of the outbound
 * SMS queue per SMS provider (counts by status, overdue queued messages, sends with an unknown outcome,
 * failures and consent blocks) and of this server's queue scheduler.
 * <p>
 * Needs {@code _admin.sms} read. It changes nothing, so it has no POST-only paths; the page shows no message
 * text and only the last four digits of phone numbers (see {@link SmsQueueViewModelAssembler}).
 * <p>
 * The optional {@code window} request parameter picks the time period of the failed and blocked sections
 * (see {@link SmsQueueWindow}). Messages of patients the viewer is restricted from are left out of the lists
 * and of the counts by code, and every patient whose messages are shown gets an audit record (see
 * {@link SmsQueueViewAuditRecorder}). Only a patient with a security entry of their own can be restricted
 * (see {@link SmsPatientRestrictionLookup}), so access is checked for those patients alone.
 *
 * @since 2026-09-28
 */
public class SmsQueue2Action extends ActionSupport {
    private static final String SECURITY_OBJECT = "_admin.sms";
    private static final String DEMOGRAPHIC_SECURITY_OBJECT = "_demographic";
    private static final String WINDOW_PARAMETER = "window";

    private final SecurityInfoManager securityInfoManager;
    private final SmsQueueViewModelAssembler assembler;
    private final SmsQueueViewAuditRecorder auditRecorder;
    private final SmsPatientRestrictionLookup restrictionLookup;

    public SmsQueue2Action(SecurityInfoManager securityInfoManager, SmsQueueViewModelAssembler assembler,
                           SmsQueueViewAuditRecorder auditRecorder,
                           SmsPatientRestrictionLookup restrictionLookup) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
        this.auditRecorder = auditRecorder;
        this.restrictionLookup = restrictionLookup;
    }

    /**
     * Whether this viewer may see that patient's messages. No, when the viewer is restricted from the
     * patient's record. Also no, for a viewer who may read demographics in general, when an entry for this
     * one patient takes that right away. The restriction is checked first: the per-patient privilege check
     * marks the session when it meets a restriction, which a list view must not do.
     */
    private boolean mayShowPatient(LoggedInInfo loggedInInfo, boolean readsDemographics, int demographicNo) {
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            return false;
        }
        return !readsDemographics || securityInfoManager.hasPrivilege(
                loggedInInfo, DEMOGRAPHIC_SECURITY_OBJECT, SecurityInfoManager.READ, demographicNo);
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // An admin-wide object, not a patient record, so no demographic number is passed.
        if (loggedInInfo == null
                || !securityInfoManager.hasPrivilege(loggedInInfo, SECURITY_OBJECT, SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (" + SECURITY_OBJECT + ")");
        }
        // Demographic numbers link a message to a patient, so only viewers who may read demographics see them.
        boolean showDemographicNumbers = securityInfoManager.hasPrivilege(
                loggedInInfo, DEMOGRAPHIC_SECURITY_OBJECT, SecurityInfoManager.READ, null);
        // Only an allowed value comes out of this; the text the browser sent goes no further.
        SmsQueueWindow window = SmsQueueWindow.fromParameter(request.getParameter(WINDOW_PARAMETER));
        // Only these patients can be restricted. Not caught: if they cannot be read, nothing is shown.
        Set<Integer> patientsWithOwnEntries = Set.copyOf(restrictionLookup.patientsWithOwnEntries());
        // A patient the viewer is restricted from has their messages left out of the lists and of the
        // counts by code. A patient without an entry of their own is never checked.
        SmsQueueViewModelAssembler.Result queue = assembler.assemble(window, showDemographicNumbers,
                patientsWithOwnEntries,
                demographicNo -> !patientsWithOwnEntries.contains(demographicNo)
                        || mayShowPatient(loggedInInfo, showDemographicNumbers, demographicNo));
        // Recorded before the page is handed over: if the view cannot be audited, nothing is shown.
        auditRecorder.recordViewed(loggedInInfo, window, queue.displayedDemographicNumbers());
        request.setAttribute("smsQueue", queue.model());
        return SUCCESS;
    }
}
