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

    public String execute()
            throws IOException, ServletException {

        if ("useFav2".equals(request.getParameter("parameterValue"))) {
            return useFav2();
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }


        // Setup variables
        RxSessionBean bean =
                (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "Prescription session is unavailable");
            return null;
        }

        try {
            RxPrescriptionData rxData =
                    new RxPrescriptionData();

            // get favorite
            RxPrescriptionData.Favorite fav =
                    findFavorite(rxData, this.getFavoriteId());
            if (fav == null) return null;

            // create Prescription
            RxPrescriptionData.Prescription rx =
                    rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo(), fav);

            bean.setStashIndex(bean.addStashItem(loggedInInfo, rx));
            request.setAttribute("BoxNoFillFirstLoad", "true");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Could not stage prescription favorite ({})", e.getClass().getSimpleName());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Prescription favorite could not be loaded");
            return null;
        }

        return SUCCESS;
    }

    public String useFav2()
            throws IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        // Setup variables
        RxSessionBean bean =
                (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "Prescription session is unavailable");
            return null;
        }

        try {
            long randomId;
            try {
                randomId = Long.parseLong(request.getParameter("randomId"));
                if (randomId < 0 || randomId > Integer.MAX_VALUE) throw new NumberFormatException();
            } catch (NumberFormatException invalidId) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid staged prescription identifier");
                return null;
            }


            RxPrescriptionData rxData =
                    new RxPrescriptionData();

            // get favorite
            RxPrescriptionData.Favorite fav =
                    findFavorite(rxData, request.getParameter("favoriteId"));
            if (fav == null) return null;

            // create Prescription
            RxPrescriptionData.Prescription rx =
                    rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo(), fav);
            rx.setRandomId(randomId);

            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);

            List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();
            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listRxDrugs.add(rx);
                int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
                bean.setStashIndex(rxStashIndex);
            }


            request.setAttribute("listRxDrugs", listRxDrugs);
            request.setAttribute("BoxNoFillFirstLoad", "true");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Could not stage prescription favorite ({})", e.getClass().getSimpleName());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Prescription favorite could not be loaded");
            return null;
        }

        return "useFav2";
    }

    /** Resolve a positive favorite identity before changing the prescription stash. */
    private RxPrescriptionData.Favorite findFavorite(RxPrescriptionData data, String rawId) throws IOException {
        int id;
        try {
            id = Integer.parseInt(rawId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalidId) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid prescription favorite identifier");
            return null;
        }
        RxPrescriptionData.Favorite favorite = data.getFavorite(id);
        if (favorite == null) response.sendError(HttpServletResponse.SC_NOT_FOUND, "Prescription favorite is unavailable");
        return favorite;
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
