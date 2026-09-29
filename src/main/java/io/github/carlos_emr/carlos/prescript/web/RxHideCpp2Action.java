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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
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
 * Hides or shows one prescription in the encounter's CPP medication list ({@code rx/hideCpp}).
 * <p>
 * Called by the per-drug checkbox in ListDrugs.jsp with a CSRF-protected {@code fetch} POST. The
 * flag changes the patient's chart, so the action is POST-only (CSRFGuard does not check GET), needs
 * the global {@code _rx} update privilege, and needs patient-level {@code _rx} update plus record
 * access for the patient who owns the drug (#3908). A malformed id is a 400 and an unknown drug a
 * 404; the legacy code merged a {@code null} drug and parsed the id unchecked.
 *
 * @since 2011-06-20
 */
public class RxHideCpp2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws IOException {
        return update();
    }

    /**
     * Sets or clears the drug's hide-from-CPP flag and answers {@code ok} as plain text.
     *
     * @return {@link #NONE}; the response is written directly or carries an error status
     * @throws IOException if the error or {@code ok} response cannot be written
     * @throws SecurityException if the caller lacks {@code _rx} update globally or for the drug's patient
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

        String prescriptId = request.getParameter("prescriptId");
        if (prescriptId == null || !prescriptId.matches("\\d{1,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        // Validated above as 1-9 digits, so parseInt cannot fail; the primitive selects the
        // AbstractDao#find(int) overload the other Rx actions (and their tests) use.
        Drug drug = drugDao.find(Integer.parseInt(prescriptId));
        if (drug == null || drug.getDemographicId() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        // The drug id comes from the request, so authorise the patient who owns it, not only _rx.
        RxRequestedPatientAccess.requirePatient(securityInfoManager, loggedInInfo, drug.getDemographicId(), "_rx", "u");

        drug.setHideFromCpp(Boolean.parseBoolean(request.getParameter("value")));
        drugDao.merge(drug);
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
