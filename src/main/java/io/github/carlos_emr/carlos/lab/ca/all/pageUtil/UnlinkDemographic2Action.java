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
 * <p>
 * Modified by the CARLOS EMR Project in 2026: unlink archives the readings the report filed
 * (issue #6214) and accepts only POST.
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.lab.service.LabPatientAccess;
import io.github.carlos_emr.carlos.lab.service.LabPatientUnlinkService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Unlinks an HL7 lab report from the patient it was matched to: the Unlink button of the lab
 * display ({@code labDisplay.jsp}), which posts {@code labNo} and the clinician's {@code reason}
 * and reads a JSON answer.
 *
 * <p>The work is done by {@link LabPatientUnlinkService} in one transaction: the report's patient
 * routing is cleared, the readings it filed to the chart are archived (issue #6214) and the report
 * returns to its providers' inboxes as new. The answer carries {@code success},
 * {@code unlinkedDemographicNo}, {@code labNo}, {@code reason} and
 * {@code archivedMeasurementCount}, which the page uses to tell the clinician the readings were
 * archived.</p>
 *
 * <p>Security: POST only (GET and HEAD answer 405 before anything else runs, since CSRFGuard does
 * not protect GET and unlink now archives clinical data), then the global {@code _lab} update
 * privilege, then the service's patient-specific {@code _lab} update right and record lock on every
 * chart the unlink changes.</p>
 *
 * @since 2004-02-04
 */
public class UnlinkDemographic2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient LabPatientUnlinkService unlinkService;

    public UnlinkDemographic2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(LabPatientUnlinkService.class));
    }

    UnlinkDemographic2Action(SecurityInfoManager securityInfoManager, LabPatientUnlinkService unlinkService) {
        this.securityInfoManager = securityInfoManager;
        this.unlinkService = unlinkService;
    }

    /**
     * Unlinks the posted report and writes the JSON outcome.
     *
     * @return {@link #NONE}: the response is completed here
     * @throws IOException if the response cannot be written
     */
    @Override
    public String execute() throws IOException {
        // Unlinking rewrites the report's patient routing and archives the readings it filed, so a
        // crafted link must not reach it: CSRFGuard does not protect GET. The only caller posts.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "u", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String reason = request.getParameter("reason");
        Integer labNo = LabPatientAccess.positiveIdentity(request.getParameter("labNo"));
        if (labNo == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        LabPatientUnlinkService.Result result;
        try {
            result = unlinkService.unlinkReport(loggedInInfo, labNo, reason, request.getRemoteAddr());
        } catch (SecurityException refused) {
            throw refused;
        } catch (RuntimeException failure) {
            // Nothing was saved: the routing change, the archive and their audit rows roll back
            // together. The page keeps the reason for a retry when the answer is not a success.
            // The trace carries exception types and frames only: a nested database message can
            // quote the reading being archived.
            logger.error("Lab {} could not be unlinked: {}", LogSafe.sanitizeObject(labNo),
                    LogSafe.exceptionTrace(failure));
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return NONE;
        }

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("success", result.unlinked());
        jsonObject.put("unlinkedDemographicNo", result.unlinkedDemographicNo());
        jsonObject.put("labNo", labNo);
        jsonObject.put("reason", reason);
        jsonObject.put("archivedMeasurementCount", result.archivedMeasurements());

        JSONUtil.jsonResponse(response, jsonObject);
        return NONE;
    }
}
