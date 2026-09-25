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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public final class RxUseFavorite2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * Stages a favourite as a new card.
     *
     * @return the staging view, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    // Staging and cursor selection use the same monitor as edits and closes in other windows.
    @SuppressWarnings("java:S2445")
    public String execute()
            throws IOException, ServletException {
        // Staging a favourite changes the patient's stash: POST-only (#3908). SearchDrug3's
        // useFav2 posts it.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        if ("useFav2".equals(request.getParameter("parameterValue"))) {
            return useFav2();
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }


        // Setup variables
        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875),
        // staged only by a caller with _rx write, globally and for that patient: a staged card can
        // only ever be saved by a writer (#3908).
        RxSessionBean bean =
                RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        // Favourites are provider-owned: only the caller's own may be staged (400/404/403 otherwise),
        // so another provider's dosing and instruction text cannot be copied by guessing ids (#3908).
        RxPrescriptionData.Favorite fav = RxFavoriteAccess.loadOwned(request, response, this.getFavoriteId());
        if (fav == null) {
            return NONE;
        }
        try {
            synchronized (bean) {
                RxPrescriptionData rxData =
                        new RxPrescriptionData();

                // create Prescription
                RxPrescriptionData.Prescription rx =
                        rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo(), fav);

                bean.setStashIndex(bean.addStashItem(loggedInInfo, rx));
                request.setAttribute("BoxNoFillFirstLoad", "true");
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Favorite staging failed ({})", e.getClass().getSimpleName());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return NONE;
        }

        return SUCCESS;
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * The AJAX variant of {@link #execute()}: stages the favourite and returns its card.
     *
     * @return {@code useFav2}, or {@code NONE} after an error response
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    // The uniqueness decision, insertion and rendered card must describe one atomic operation.
    @SuppressWarnings("java:S2445")
    public String useFav2()
            throws IOException {
        // Staging a favourite changes the patient's stash: POST-only (#3908). SearchDrug3's
        // useFav2 posts it.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        // Setup variables
        // Changes staged Rx state: only the explicitly named patient's bean, never the fallback (#3875),
        // staged only by a caller with _rx write, globally and for that patient: a staged card can
        // only ever be saved by a writer (#3908).
        RxSessionBean bean =
                RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }

        // Only the caller's own favourite may be staged; see execute() (#3908).
        RxPrescriptionData.Favorite fav = RxFavoriteAccess.loadOwned(request, response, request.getParameter("favoriteId"));
        if (fav == null) {
            return NONE;
        }
        try {
            synchronized (bean) {
                String randomId = request.getParameter("randomId");

                RxPrescriptionData rxData =
                        new RxPrescriptionData();

                // create Prescription
                RxPrescriptionData.Prescription rx =
                        rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo(), fav);
                // The page names the card's key; it must be unused in this stash (#3908).
                rx.setRandomId(RxStashIds.acceptOrNext(bean, randomId, RxStashIds.DEFAULT_BOUND));

                String spec = RxUtil.trimSpecial(rx);
                rx.setSpecial(spec);

                List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();
                if (RxUtil.isRxUniqueInStash(bean, rx)) {
                    int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
                    // addStashItem may reuse an existing card. Never render the discarded copy
                    // with a key that no card in the shared stash actually carries.
                    if (bean.getStashItem(rxStashIndex) == rx) {
                        listRxDrugs.add(rx);
                    }
                    bean.setStashIndex(rxStashIndex);
                }


                request.setAttribute("listRxDrugs", listRxDrugs);
                request.setAttribute("BoxNoFillFirstLoad", "true");
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Favorite staging failed ({})", e.getClass().getSimpleName());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return NONE;
        }

        RxUtil.printStashContent(bean);

        return "useFav2";
    }

    private String favoriteId = null;

    public String getFavoriteId() {
        return (this.favoriteId);
    }

    @StrutsParameter
    public void setFavoriteId(String favoriteId) {
        this.favoriteId = favoriteId;
    }
}
