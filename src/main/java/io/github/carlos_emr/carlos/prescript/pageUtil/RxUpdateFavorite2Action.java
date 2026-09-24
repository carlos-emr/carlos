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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public final class RxUpdateFavorite2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    /**
     * Saves an edited favourite of the logged-in provider. POST-only (405 otherwise), needs global
     * {@code _rx} update, and refuses a malformed id or repeat (400), a missing favourite (404) or
     * another provider's (403) before anything changes (#3908). {@code method=ajaxEditFavorite}
     * dispatches to {@link #ajaxEditFavorite()}.
     *
     * @return {@code success}, or {@code NONE} after an error response
     */
    public String execute()
            throws IOException, ServletException {
        if (RxFavoriteAccess.refuseUnlessPost(request, response)) {
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "u", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        if ("ajaxEditFavorite".equals(request.getParameter("method"))) {
            return ajaxEditFavorite();
        }

        Integer repeat = RxFavoriteAccess.parseRepeat(this.getRepeat());
        if (repeat == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        RxPrescriptionData.Favorite fav = RxFavoriteAccess.loadOwned(request, response, this.getFavoriteId());
        if (fav == null) {
            return NONE;
        }

        fav.setFavoriteName(this.getFavoriteName());
        fav.setCustomName(this.getCustomName());
        fav.setTakeMin(RxUtil.StringToFloat(this.getTakeMin()));
        fav.setTakeMax(RxUtil.StringToFloat(this.getTakeMax()));
        fav.setFrequencyCode(this.getFrequencyCode());
        fav.setDuration(this.getDuration());
        fav.setDurationUnit(this.getDurationUnit());
        fav.setQuantity(this.getQuantity());
        fav.setRepeat(repeat);
        fav.setNosubs(this.getNosubs());
        fav.setPrn(this.getPrn());
        fav.setSpecial(this.getSpecial());
        fav.setCustomInstr(this.getCustomInstr());

        fav.Save();

        return SUCCESS;
    }

    /**
     * The AJAX edit from EditFavorites2.jsp, with the same POST, ownership and input checks as
     * {@link #execute()}.
     *
     * @return {@code NONE}; the status is the answer
     * @throws IOException when an error response cannot be sent
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of "true"/"false" form flags; not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of true/false form flags; not a security or authorization decision")
    public String ajaxEditFavorite() throws IOException {
        if (RxFavoriteAccess.refuseUnlessPost(request, response)) {
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "u", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        Integer repeat = RxFavoriteAccess.parseRepeat(request.getParameter("repeat"));
        if (repeat == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        RxPrescriptionData.Favorite fav = RxFavoriteAccess.loadOwned(request, response, request.getParameter("favoriteId"));
        if (fav == null) {
            return NONE;
        }
        fav.setFavoriteName(request.getParameter("favoriteName"));
        fav.setCustomName(request.getParameter("customName"));
        fav.setTakeMin(RxUtil.StringToFloat(request.getParameter("takeMin")));
        fav.setTakeMax(RxUtil.StringToFloat(request.getParameter("takeMax")));
        fav.setFrequencyCode(request.getParameter("frequencyCode"));
        fav.setDuration(request.getParameter("duration"));
        fav.setDurationUnit(request.getParameter("durationUnit"));
        fav.setQuantity(request.getParameter("quantity"));
        fav.setRepeat(repeat);
        fav.setNosubs("true".equalsIgnoreCase(request.getParameter("nosubs")));
        fav.setPrn("true".equalsIgnoreCase(request.getParameter("prn")));
        fav.setSpecial(request.getParameter("special"));
        fav.setCustomInstr("true".equalsIgnoreCase(request.getParameter("customInstr")));

        if (request.getParameter("dispenseInternal") != null && request.getParameter("dispenseInternal").length() > 0) {
            fav.setDispenseInternal(true);
        }

        fav.Save();

        return NONE;
    }


    private String favoriteId = null;
    private String favoriteName = null;
    private String customName = null;
    private String takeMin = null;
    private String takeMax = null;
    private String frequencyCode = null;
    private String duration = null;
    private String durationUnit = null;
    private String quantity = null;
    private String repeat = null;
    private boolean nosubs = false;
    private boolean prn = false;
    private boolean customInstr = false;
    private String special = null;

    public boolean getCustomInstr() {
        return this.customInstr;
    }

    @StrutsParameter
    public void setCustomInstr(boolean customInstr) {
        this.customInstr = customInstr;
    }

    public String getFavoriteId() {
        return (this.favoriteId);
    }

    @StrutsParameter
    public void setFavoriteId(String favoriteId) {
        this.favoriteId = favoriteId;
    }

    public String getFavoriteName() {
        return (this.favoriteName);
    }

    @StrutsParameter
    public void setFavoriteName(String RHS) {
        this.favoriteName = RHS;
    }

    public String getCustomName() {
        return this.customName;
    }

    @StrutsParameter
    public void setCustomName(String RHS) {
        this.customName = RHS;
    }

    public String getTakeMin() {
        return (this.takeMin);
    }

    @StrutsParameter
    public void setTakeMin(String RHS) {
        this.takeMin = RHS;
    }

    public String getTakeMax() {
        return (this.takeMax);
    }

    @StrutsParameter
    public void setTakeMax(String RHS) {
        this.takeMax = RHS;
    }

    public String getFrequencyCode() {
        return (this.frequencyCode);
    }

    @StrutsParameter
    public void setFrequencyCode(String RHS) {
        this.frequencyCode = RHS;
    }

    public String getDuration() {
        return (this.duration);
    }

    @StrutsParameter
    public void setDuration(String RHS) {
        this.duration = RHS;
    }

    public String getDurationUnit() {
        return (this.durationUnit);
    }

    @StrutsParameter
    public void setDurationUnit(String RHS) {
        this.durationUnit = RHS;
    }

    public String getQuantity() {
        return (this.quantity);
    }

    @StrutsParameter
    public void setQuantity(String RHS) {
        this.quantity = RHS;
    }

    public String getRepeat() {
        return (this.repeat);
    }

    @StrutsParameter
    public void setRepeat(String RHS) {
        this.repeat = RHS;
    }

    public boolean getNosubs() {
        return (this.nosubs);
    }

    @StrutsParameter
    public void setNosubs(boolean RHS) {
        this.nosubs = RHS;
    }

    public boolean getPrn() {
        return (this.prn);
    }

    @StrutsParameter
    public void setPrn(boolean RHS) {
        this.prn = RHS;
    }

    public String getSpecial() {
        return (this.special);
    }

    @StrutsParameter
    public void setSpecial(String RHS) {
        this.special = RHS;
    }

}
