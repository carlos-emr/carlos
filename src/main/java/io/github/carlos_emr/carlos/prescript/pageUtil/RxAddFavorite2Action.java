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

/**
 * Struts 2 action for saving a prescription as a provider favorite template.
 * <p>
 * Supports saving favorites from either a persisted drug ID or a stash item. The AJAX
 * variant (addFav2) is used with the RX3 interface. Requires {@code _rx} write privilege.
 *
 * @since 2026-03-17
 */
public final class RxAddFavorite2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public String execute()
            throws IOException, ServletException {

        if ("addFav2".equals(request.getParameter("parameterValue"))) {
            return addFav2();
        }
        
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "w", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
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
            int stashId = Integer.parseInt(this.getStashId());

            bean.getStashItem(stashId).AddToFavorites(providerNo, favoriteName);
        }

        return "success";
    }

    //used with rx3
    public String addFav2()
            throws IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "w", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        String randomId = request.getParameter("randomId");
        String favoriteName = request.getParameter("favoriteName");
        String drugIdStr = request.getParameter("drugId");
        String providerNo = bean.getProviderNo();

        if (drugIdStr != null) {
            int drugId = Integer.parseInt(drugIdStr);
            DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);
            Drug drug = drugDao.find(drugId);
            RxPrescriptionData.addToFavorites(providerNo, favoriteName, drug);
        } else {
            int stashId = bean.getIndexFromRx(Integer.parseInt(randomId));
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
