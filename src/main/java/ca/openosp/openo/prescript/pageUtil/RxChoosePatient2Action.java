//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.prescript.pageUtil;

import ca.openosp.OscarProperties;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.prescript.data.RxPatientData;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.struts2.ServletActionContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

        // p("locale",locale.toString());
        // p("messages",messages.toString());
        // Setup variables

        if (request.getSession().getAttribute("user") == null) {
            return "Logout";
        }

        String redirect = "error.html";
        String user_no;
        user_no = (String) request.getSession().getAttribute("user");
        // p("user_no",user_no);
        // p("frm",frm.toString());
        // Setup bean
        RxSessionBean bean = new RxSessionBean();

        bean.setProviderNo(user_no);
        bean.setDemographicNo(Integer.parseInt(this.getDemographicNo()));

        request.getSession().setAttribute("RxSessionBean", bean);

        RxPatientData rx = null;
        RxPatientData.Patient patient = null;

        patient = RxPatientData.getPatient(loggedInInfo, bean.getDemographicNo());

        String provider = (String) request.getSession().getAttribute("user");
        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
        userPropertyDAO = (UserPropertyDAO) ctx.getBean(UserPropertyDAO.class);
        boolean providerUseRx3 = false;
        UserProperty propUseRx3 = userPropertyDAO.getProp(provider, UserProperty.RX_USE_RX3);

        if (propUseRx3 != null) {
            providerUseRx3 = BooleanUtils.toBoolean(propUseRx3.getValue());
        }

        if (patient != null) {

            if (OscarProperties.getInstance().getBooleanProperty("RX3", "yes") || providerUseRx3) {
                redirect = "successRX3";
            }
            // place holder.
//			else if( OscarProperties.getInstance().getBooleanProperty("ENABLE_RX4", "yes") ) {
//				redirect = "successRX4";
//			} 
            else {
                redirect = "success";
            }

            // set the profile view
            UserProperty prop = userPropertyDAO.getProp(provider, UserProperty.RX_PROFILE_VIEW);
            if (prop != null) {
                try {
                    String propValue = prop.getValue();

                    HashMap hm = new HashMap();
                    // the order of strings in this array is important, because of removing string from propValue if it contains the string.
                    String[] va = {"show_current", "show_all", "longterm_acute_inactive_external", "inactive", "active", "all", "longterm_acute",};
                    for (int i = 0; i < va.length; i++) {
                        if (propValue.contains(va[i])) {
                            propValue = propValue.replace(va[i], "");
                            hm.put(va[i].trim(), true);
                        } else {
                            hm.put(va[i].trim(), false);
                        }
                    }

                    request.getSession().setAttribute("profileViewSpec", hm);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }

            }

            request.getSession().setAttribute("Patient", patient);
        }

        return redirect;

    }

    private String providerNo = null;
    private String demographicNo = null;

    public String getProviderNo() {
        return (this.providerNo);
    }

    public void setProviderNo(String RHS) {
        this.providerNo = RHS;
    }

    public String getDemographicNo() {
        return (this.demographicNo);
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }
}
