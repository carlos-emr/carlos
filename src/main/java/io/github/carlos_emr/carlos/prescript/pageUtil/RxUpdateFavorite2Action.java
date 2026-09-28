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


    public String execute()
            throws IOException, ServletException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "u", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        // The Edit Favorites link opens this route without edit parameters. Preserve
        // that read-only navigation while rejecting all safe-method save attempts.
        if (("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                && getFavoriteId() == null && request.getParameter("favoriteId") == null
                && request.getParameter("method") == null) {
            return SUCCESS;
        }

        if ("ajaxEditFavorite".equals(request.getParameter("method"))) {
            return ajaxEditFavorite();
        }

        if (!requirePost()) return NONE;

        // Setup variables
        RxPrescriptionData.Favorite fav = findFavorite(this.getFavoriteId());
        if (fav == null) return NONE;

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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String ajaxEditFavorite() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "u", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        if (!requirePost()) return NONE;

        // Setup variables
        RxPrescriptionData.Favorite fav = findFavorite(request.getParameter("favoriteId"));
        if (fav == null) return NONE;
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

        // The editor has no dispensing control. Omission preserves the saved flag;
        // clients explicitly sending false must be able to clear it.
        if (request.getParameter("dispenseInternal") != null) {
            fav.setDispenseInternal("true".equalsIgnoreCase(request.getParameter("dispenseInternal")));
        }

        fav.Save();

        // This AJAX endpoint has no representation. An explicit no-content
        // response completes the fetch without an unread, empty 200 stream.
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return NONE;
    }

    /** Refuse safe-method edits before loading or changing a favorite. */
    private boolean requirePost() {
        if ("POST".equals(request.getMethod())) return true;
        response.setHeader("Allow", "POST");
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return false;
    }

    /** Resolve a positive favorite identity before applying any submitted fields. */
    private RxPrescriptionData.Favorite findFavorite(String rawId) throws IOException {
        int id;
        try {
            id = Integer.parseInt(rawId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalidId) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid prescription favorite identifier");
            return null;
        }
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "Prescription session is unavailable");
            return null;
        }
        RxPrescriptionData.Favorite favorite = new RxPrescriptionData().getFavorite(id);
        // The editor lists the current prescribing provider's favorites. Sharing copies
        // favorites into that list; it never grants permission to edit another provider's row.
        if (favorite == null || bean.getProviderNo() == null
                || !bean.getProviderNo().equals(favorite.getProviderNo())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Prescription favorite is unavailable");
            return null;
        }
        return favorite;
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
