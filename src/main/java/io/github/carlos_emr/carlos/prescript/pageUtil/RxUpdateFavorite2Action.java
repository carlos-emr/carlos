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

public final class RxUpdateFavorite2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public String execute()
            throws IOException, ServletException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "u", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        if ("ajaxEditFavorite".equals(request.getParameter("method"))) {
            return ajaxEditFavorite();
        }

        // Setup variables
        int favId = Integer.parseInt(this.getFavoriteId());

        RxPrescriptionData.Favorite fav = new RxPrescriptionData().getFavorite(favId);

        fav.setFavoriteName(this.getFavoriteName());
        fav.setCustomName(this.getCustomName());
        fav.setTakeMin(RxUtil.StringToFloat(this.getTakeMin()));
        fav.setTakeMax(RxUtil.StringToFloat(this.getTakeMax()));
        fav.setFrequencyCode(this.getFrequencyCode());
        fav.setDuration(this.getDuration());
        fav.setDurationUnit(this.getDurationUnit());
        fav.setQuantity(this.getQuantity());
        fav.setRepeat(Integer.parseInt(this.getRepeat()));
        fav.setNosubs(this.getNosubs());
        fav.setPrn(this.getPrn());
        fav.setSpecial(this.getSpecial());
        fav.setCustomInstr(this.getCustomInstr());

        fav.Save();

        return SUCCESS;
    }

    public String ajaxEditFavorite() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "u", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        // Setup variables
        int favId = Integer.parseInt(request.getParameter("favoriteId"));

        RxPrescriptionData.Favorite fav = new RxPrescriptionData().getFavorite(favId);
        String favName = request.getParameter("favoriteName");
        String customName = request.getParameter("customName");
        String takeMin = request.getParameter("takeMin");
        String takeMax = request.getParameter("takeMax");
        String freqCode = request.getParameter("frequencyCode");
        String duration = request.getParameter("duration");
        String durationUnit = request.getParameter("durationUnit");
        String quantity = request.getParameter("quantity");
        String repeat = request.getParameter("repeat");
        String noSubs = request.getParameter("nosubs");
        String prn = request.getParameter("prn");
        String special = request.getParameter("special");
        String customInstr = request.getParameter("customInstr");
        fav.setFavoriteName(favName);
        fav.setCustomName(customName);
        fav.setTakeMin(RxUtil.StringToFloat(takeMin));
        fav.setTakeMax(RxUtil.StringToFloat(takeMax));
        fav.setFrequencyCode(freqCode);
        fav.setDuration(duration);
        fav.setDurationUnit(durationUnit);
        fav.setQuantity(quantity);
        fav.setRepeat(Integer.parseInt(repeat));
        if (noSubs.equalsIgnoreCase("true"))
            fav.setNosubs(true);
        else
            fav.setNosubs(false);
        if (prn.equalsIgnoreCase("true"))
            fav.setPrn(true);
        else
            fav.setPrn(false);
        fav.setSpecial(special);
        if (customInstr.equalsIgnoreCase("true"))
            fav.setCustomInstr(true);
        else
            fav.setCustomInstr(false);

        if (request.getParameter("dispenseInternal") != null && request.getParameter("dispenseInternal").length() > 0) {
            fav.setDispenseInternal(true);
        }

        fav.Save();

        return null;
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
