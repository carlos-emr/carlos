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

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;

public final class RxChoosePatient2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public void p(String s) {
        MiscUtils.getLogger().debug(s);
    }

    public void p(String s, String s2) {
        MiscUtils.getLogger().debug(s + "=" + s2);
    }

    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new RuntimeException("missing required sec object (_demographic)");
        }

        // p("locale", locale.toString());
        // p("messages", messages.toString());
        // Setup variables

        if (request.getSession().getAttribute("user") == null) {
            return "Logout";
        }

        String redirect = "error.html";
        String user_no;
        user_no = (String) request.getSession().getAttribute("user");
        // p("user_no", user_no);
        // p("frm", frm.toString());
        // Setup bean
        // The patient comes from the request through the resolver, which accepts the same
        // demographicNo repeated (URL and form body) and refuses a malformed or conflicting one.
        // Struts no longer binds it: a repeated value used to become "1, 1" here (#3908).
        int demographicNoInt = RxSessionBeanResolver.requestedDemographicNo(request);
        if (demographicNoInt <= 0) {
            // Missing, malformed, non-positive or conflicting (demographicNo and demographic_no
            // naming different patients): a bad request, answered here rather than through the
            // unmapped "error.html" result.
            MiscUtils.getLogger().warn("Rejected Rx open: missing or malformed demographicNo");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        this.demographicNo = String.valueOf(demographicNoInt);
        // Per-patient state (#3875): reuse this patient's bean so reopening Rx keeps staged drafts,
        // and never replace another patient's bean that a second window is still using.
        RxSessionBean bean = RxSessionBeanResolver.activate(request, demographicNoInt, user_no);

        RxPatientData rx = null;
        RxPatientData.Patient patient = null;

        patient = RxPatientData.getPatient(loggedInInfo, bean.getDemographicNo());

        String provider = (String) request.getSession().getAttribute("user");
        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
        userPropertyDAO = (UserPropertyDAO) ctx.getBean(UserPropertyDAO.class);

        if (patient != null) {

            redirect = "success";

            // set the profile view
            UserProperty prop = userPropertyDAO.getProp(provider, UserProperty.RX_PROFILE_VIEW);
            if (prop != null) {
                try {
                    String propValue = prop.getValue();

                    HashMap hm = new HashMap();
                    // the order of strings in this array is important, because of removing string from propValue if it contains the string.
                    String[] va = {"show_current", "show_all", "longterm_acute_inactive_external", "inactive", "active", "all", "longterm_acute", };
                    for (int i = 0; i < va.length; i++) {
                        if (propValue.contains(va[i])) {
                            propValue = propValue.replace(va[i], "");
                            hm.put(va[i].trim(), true);
                        } else {
                            hm.put(va[i].trim(), false);
                        }
                    }

                    // nosemgrep: tainted-session-from-http-request -- hm is derived from DAO-sourced UserProperty, not raw user input
                    request.getSession().setAttribute("profileViewSpec", hm);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }

            }

            // The patient record is no longer parked in the session (it followed the last chart
            // opened); Rx pages load it per request through RxSessionBeanResolver.resolvePatient.
        }

        return redirect;

    }

    private String providerNo = null;
    private String demographicNo = null;

    public String getProviderNo() {
        return (this.providerNo);
    }

    @StrutsParameter
    public void setProviderNo(String RHS) {
        this.providerNo = RHS;
    }

    public String getDemographicNo() {
        return (this.demographicNo);
    }

    /**
     * Not a Struts parameter: the patient is read from the request by
     * {@link RxSessionBeanResolver#requestedDemographicNo}. Binding it turned a repeated
     * demographicNo into one comma-joined string and the Rx page failed to open (#3908).
     */
    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }
}
