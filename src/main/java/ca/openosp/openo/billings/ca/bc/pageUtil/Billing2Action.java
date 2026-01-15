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


package ca.openosp.openo.billings.ca.bc.pageUtil;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.decisionSupport.model.DSConsequence;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.billings.ca.bc.decisionSupport.BillingGuidelines;
import ca.openosp.openo.util.plugin.OscarProperties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class Billing2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger _log = MiscUtils.getLogger();

    //  private ServiceCodeValidationLogic vldt = new ServiceCodeValidationLogic();
    public String execute() throws IOException,
            ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        BillingSessionBean bean = null;
        String region = request.getParameter("billRegion") != null ? request.getParameter("billRegion") : OscarProperties.getProperties().getProperty("billregion");

        if ("ON".equals(region)) {
            String newURL = request.getContextPath() + "/billing/CA/ON/billingOB.jsp";
            newURL = newURL + "?" + request.getQueryString();
            response.sendRedirect(newURL);
            return NONE;
        } else {
            if (request.getParameter("demographic_no") != null &
                    request.getParameter("appointment_no") != null) {
                String newWCBClaim = request.getParameter("newWCBClaim");
                //If newWCBClaim == 1, this action was invoked from the WCB form
                //Therefore, we need to set the appropriate parameters to set up the subsequent bill
                if ("1".equals(newWCBClaim)) {

                    form.setXml_billtype("WCB");

                    List l = (List) request.getAttribute("billingcodes");
                    if (l != null && l.size() > 0) {
                        form.setXml_other1("" + l.get(0));
                        if (l.size() > 1) {
                            form.setXml_other2("" + l.get(1));
                        }

                    }

                    form.setXml_diagnostic_detail1("" + request.getAttribute("icd9"));
                    request.setAttribute("WCBFormId", request.getAttribute("WCBFormId"));
                    request.setAttribute("newWCBClaim", request.getParameter("newWCBClaim"));
                    request.setAttribute("loadFromSession", "y");
                }
                bean = new BillingSessionBean();
                fillBean(request, bean);
                if (request.getAttribute("serviceDate") != null) {
                    MiscUtils.getLogger().debug("service Date set to the appointment Date" + (String) request.getAttribute("serviceDate"));
                    bean.setApptDate((String) request.getAttribute("serviceDate"));
                }

                request.getSession().setAttribute("billingSessionBean", bean);
                
                try {
                    _log.debug("Start of billing rules");
                    List<DSConsequence> list = BillingGuidelines.getInstance().evaluateAndGetConsequences(loggedInInfo, request.getParameter("demographic_no"), (String) request.getSession().getAttribute("user"));

                    for (DSConsequence dscon : list) {
                        _log.debug("DSTEXT " + dscon.getText());
                        addActionError(getText("message.custom", new String[]{dscon.getText()}));
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }
            }
        }
        return region;
    }

    private void fillBean(HttpServletRequest request, BillingSessionBean bean) {
        bean.setApptProviderNo(request.getParameter("apptProvider_no"));
        bean.setPatientName(request.getParameter("demographic_name"));
        bean.setProviderView(request.getParameter("providerview"));
        bean.setBillRegion(request.getParameter("billRegion"));
        bean.setBillForm(request.getParameter("billForm"));
        bean.setCreator(request.getParameter("user_no"));
        bean.setPatientNo(request.getParameter("demographic_no"));
        bean.setApptNo(request.getParameter("appointment_no"));
        bean.setApptDate(request.getParameter("appointment_date"));
        bean.setApptStart(request.getParameter("start_time"));
        bean.setApptStatus(request.getParameter("status"));
    }

    private BillingCreateBilling2Form form;

    public BillingCreateBilling2Form getForm() {
        return form;
    }

    public void setForm(BillingCreateBilling2Form form) {
        this.form = form;
    }
}
