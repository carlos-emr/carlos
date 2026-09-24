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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public final class RxAddFavorite2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * A saved drug ({@code drugId}) is favourited without touching any stash; a staged card is chosen by
     * its position in the named patient's stash, and a malformed or out-of-range position is a 400.
     *
     * @return {@code success}, {@code NONE} after an error response, or {@code null} after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String execute()
            throws IOException, ServletException {

        if ("addFav2".equals(request.getParameter("parameterValue"))) {
            return addFav2();
        }
        
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "w", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        // A staged card is looked up by position in the named patient's stash, never the
        // no-patient fallback, so a stale window cannot favourite another patient's draft (#3875).
        // A saved drug id is favourited without touching any patient's stash.
        RxSessionBean bean = this.getDrugId() != null
                ? RxSessionBeanResolver.resolve(request)
                : RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        String providerNo = bean.getProviderNo();

        if (this.getDrugId() != null) {
            int drugId = Integer.parseInt(this.getDrugId());

            DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);
            Drug drug = drugDao.find(drugId);
            RxPrescriptionData.addToFavorites(providerNo, favoriteName, drug);
        } else {
            // The card's position in the named patient's stash; a malformed or out-of-range
            // position names no staged item, so nothing is favourited.
            int stashId;
            try {
                stashId = Integer.parseInt(this.getStashId());
            } catch (NumberFormatException e) {
                stashId = -1;
            }
            if (stashId < 0 || stashId >= bean.getStashSize()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            bean.getStashItem(stashId).AddToFavorites(providerNo, favoriteName);
        }

        return "success";
    }

    /**
     * Changes the staged Rx state of the patient the request names ({@code demographicNo}); never the
     * most recently opened patient. Needs {@code _rx} write, and the same privilege for that patient plus record access
     * ({@link io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess#resolveForWrite}).
     *
     * The AJAX variant of {@link #execute()}: a staged card is chosen by its stash key ({@code randomId});
     * a stale or malformed key is a 400.
     *
     * @return {@code NONE}, or {@code null} after a redirect
     * @throws SecurityException when the caller may not write Rx for the patient
     */
    public String addFav2()
            throws IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "w", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        String randomId = request.getParameter("randomId");
        String favoriteName = request.getParameter("favoriteName");
        String drugIdStr = request.getParameter("drugId");
        // Same rule as execute(): a staged card comes only from the named patient's stash (#3875).
        RxSessionBean bean = drugIdStr != null
                ? RxSessionBeanResolver.resolve(request)
                : RxRequestedPatientAccess.resolveForWrite(securityInfoManager, request, "_rx", "w");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        String providerNo = bean.getProviderNo();

        if (drugIdStr != null) {
            int drugId = Integer.parseInt(drugIdStr);
            DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);
            Drug drug = drugDao.find(drugId);
            RxPrescriptionData.addToFavorites(providerNo, favoriteName, drug);
        } else {
            int stashId;
            try {
                stashId = bean.getIndexFromRx(Integer.parseInt(randomId));
            } catch (NumberFormatException e) {
                stashId = -1;
            }
            if (stashId < 0) {
                // No staged card carries this key (stale or malformed): favourite nothing.
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            bean.getStashItem(stashId).AddToFavorites(providerNo, favoriteName);
        }
       
        /*
        request.setAttribute("BoxNoFillFirstLoad", "true");
        MiscUtils.getLogger().debug("fill box no");
        */
        RxUtil.printStashContent(bean);

        return null;
    }


    private String drugId = null;
    private String stashId = null;
    private String favoriteName = null;
    private String returnParams = null;

    public String getDrugId() {
        return (this.drugId);
    }

    @StrutsParameter
    public void setDrugId(String drugId) {
        this.drugId = drugId;
    }

    public String getStashId() {
        return (this.stashId);
    }

    @StrutsParameter
    public void setStashId(String stashId) {
        this.stashId = stashId;
    }

    public String getFavoriteName() {
        return (this.favoriteName);
    }

    @StrutsParameter
    public void setFavoriteName(String favoriteName) {
        this.favoriteName = favoriteName;
    }

    public String getReturnParams() {
        if (this.returnParams == null) {
            this.returnParams = "";
        }
        return (this.returnParams);
    }
}
