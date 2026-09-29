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

package io.github.carlos_emr.carlos.prescript.web;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Swaps the display position of two of a patient's prescriptions ({@code rx/reorderDrug}).
 * <p>
 * Called by the up/down arrows in SearchDrug3.jsp with a CSRF-protected AJAX POST. Reordering
 * changes the patient's medication list, so the action is POST-only (CSRFGuard does not check GET),
 * needs the global {@code _rx} update privilege, and needs patient-level {@code _rx} update plus
 * record access for the named patient before that patient's drugs are loaded (#3908). Both drugs
 * are looked up only among that patient's prescriptions, so another patient's drug id is ignored.
 * Malformed ids are a 400; the legacy code parsed them unchecked.
 *
 * @since 2011-06-27
 */
public class RxReorder2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final String ID_PATTERN = "\\d{1,9}";

    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws IOException {
        return update();
    }

    /**
     * Swaps the positions of {@code drugId} and {@code swapDrugId} for {@code demographicNo} and
     * answers {@code ok} as plain text.
     *
     * @return {@link #NONE}; the response is written directly or carries an error status
     * @throws IOException if the error or {@code ok} response cannot be written
     * @throws SecurityException if the caller lacks {@code _rx} update globally or for the patient
     */
    public String update() throws IOException {
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "u", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        String demographicNo = request.getParameter("demographicNo");
        String drugIdParam = request.getParameter("drugId");
        String swapDrugIdParam = request.getParameter("swapDrugId");
        if (demographicNo == null || !demographicNo.matches(ID_PATTERN)
                || drugIdParam == null || !drugIdParam.matches(ID_PATTERN)
                || swapDrugIdParam == null || !swapDrugIdParam.matches(ID_PATTERN)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        RxRequestedPatientAccess.requirePatient(securityInfoManager, loggedInInfo,
                Integer.parseInt(demographicNo), "_rx", "u");
        int drugId = Integer.parseInt(drugIdParam);
        int swapDrugId = Integer.parseInt(swapDrugIdParam);

        CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
        List<Drug> drugs = caseManagementManager.getPrescriptions(demographicNo, true);
        DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);

        Drug myDrug = null;
        Drug swapDrug = null;

        for (Drug drug : drugs) {
            if (drug.getId().intValue() == drugId) {
                myDrug = drug;
            }
            if (drug.getId().intValue() == swapDrugId) {
                swapDrug = drug;
            }
        }

        if (myDrug == null || swapDrug == null) {
            logger.warn("Couldn't find the drugs to swap");
        } else {
            int myPosition = myDrug.getPosition();
            int swapPosition = swapDrug.getPosition();
            myDrug.setPosition(swapPosition);
            swapDrug.setPosition(myPosition);
            drugDao.merge(myDrug);
            drugDao.merge(swapDrug);
        }

        try {
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().println("ok");
        } catch (IOException e) {
            logger.error("error", e);
        }
        return NONE;
    }
}
