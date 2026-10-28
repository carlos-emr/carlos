/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import io.github.carlos_emr.carlos.billings.ca.bc.decisionSupport.BillingGuidelines;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.RequestNegotiation;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * BC-only billing setup chained behind {@link
 * io.github.carlos_emr.carlos.billings.ca.pageUtil.Billing2Action} on the
 * {@code "BC"} result. Opens a new bill ({@link BillingSessionBean}) for the
 * requested patient and appointment, or resumes the bill the request names by
 * its key, runs the BC decision-support {@link BillingGuidelines}, and forwards
 * to {@code billingBC.jsp}. Lives in the BC subpackage on purpose — every
 * symbol it touches is BC-specific.
 *
 * <p>Each bill is staged under its own key ({@link BcBillStaging}). It used to be
 * the single session attribute {@code billingSessionBean}, so a bill opened in a
 * second window replaced the first window's, and a request that opened no bill
 * showed whichever bill was opened last (issue #4673). An XMLHttpRequest, the
 * bill form reloading part of itself in place, is rendered without being staged.</p>
 *
 * <p>Lifted out of the former {@code ca.bc.pageUtil.Billing2Action}, which
 * conflated cross-province routing with BC bean fill. The cross-province
 * router now lives at {@code ca.pageUtil.Billing2Action}.</p>
 *
 * @since 2026-04-27
 */
public final class BillingBCSetup2Action extends ActionSupport {

    private static final Logger _log = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    private BillingCreateBilling2Form form;

    @Override
    public String execute() throws IOException, ServletException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (request.getParameter("demographic_no") != null
                && request.getParameter("appointment_no") != null) {
            String demoNo = request.getParameter("demographic_no");
            if (!demoNo.matches("\\d{1,9}")) {
                _log.warn("Invalid demographic_no rejected");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            String apptNo = request.getParameter("appointment_no");
            if (!apptNo.matches("\\d{1,9}")) {
                _log.warn("Invalid appointment_no rejected");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            String newWCBClaim = request.getParameter("newWCBClaim");
            // If newWCBClaim == 1, this action was invoked from the WCB form,
            // so seed the form with the codes/diagnostic the WCB flow handed in.
            if ("1".equals(newWCBClaim)) {
                // The regular BC entry path may not have created the form yet;
                // WCB handoff still expects the same mutable form bean to exist
                // before we seed legacy xml_* fields below.
                if (form == null) {
                    form = new BillingCreateBilling2Form();
                }
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

            BillingSessionBean bean = new BillingSessionBean();
            fillBean(request, bean);
            // fillBean reads patientNo / apptNo from raw request params; overwrite
            // with the regex-validated values so downstream code can trust them.
            bean.setPatientNo(demoNo);
            bean.setApptNo(apptNo);
            // The creator is an audit field: take it from the session, not the request.
            bean.setCreator(loggedInInfo.getLoggedInProviderNo());
            if (request.getAttribute("serviceDate") != null) {
                MiscUtils.getLogger().debug("service Date set to the appointment Date"
                        + (String) request.getAttribute("serviceDate"));
                bean.setApptDate((String) request.getAttribute("serviceDate"));
            }

            if (RequestNegotiation.isAjax(request)) {
                // The open bill form reloading its patient bar or code table in place after a
                // provider, bill type or form change. It keeps posting the bill it opened with, so
                // this one is only rendered: staging every reload would push that bill, and other
                // windows' bills, out of the session.
                BcBillStaging.render(request, bean);
            } else {
                // This window's own bill; its pages carry the key through review and save.
                BcBillStaging.open(request, bean);
            }

            try {
                _log.debug("Start of billing rules");
                List<DSConsequence> list = BillingGuidelines.getInstance().evaluateAndGetConsequences(
                        loggedInInfo, demoNo, loggedInInfo.getLoggedInProviderNo());

                for (DSConsequence dscon : list) {
                    _log.debug("DSTEXT {}", LogSafe.sanitize(dscon.getText()));
                    addActionError(getText("message.custom", new String[]{dscon.getText()}));
                }
            } catch (Exception e) {
                // Drools rule evaluation is best-effort guidance; failure must not block the
                // billing-setup screen, but it must surface to ops with patient context so a
                // broken ruleset doesn't fail silently for every claim.
                MiscUtils.getLogger().error("BC billing-guideline rules failed for demoNo {}: setup will continue without consequences",
                        LogSafe.sanitize(demoNo), e);
            }
        } else {
            BillingSessionBean resumed = BcBillStaging.resume(request);
            if (resumed == null) {
                // No new bill and no open bill named by this request (a validation return, Go Back, or
                // the encounter's save-and-bill forward name theirs). Never another window's bill: the
                // window is told its billing session expired, as the save step tells it.
                BillingSaveBilling2Action.rejectExpiredBillingSession(request, response);
                return NONE;
            }
            showHeldRefusal(request, resumed);
        }
        return SUCCESS;
    }

    /**
     * A refused Continue comes back here by redirect, its reasons held on the bill (issue #4864
     * item 10, finding 432). They are shown on this render and cleared, so a reload or a later Go
     * Back does not show them again.
     */
    private void showHeldRefusal(HttpServletRequest request, BillingSessionBean bill) {
        List<String> messages = bill.takeRefusalMessages();
        List<String> wcbFormNeeds = bill.takeWcbFormNeeds();
        if (messages.isEmpty() && wcbFormNeeds.isEmpty()) {
            return;
        }
        messages.forEach(this::addActionError);
        if (!wcbFormNeeds.isEmpty()) {
            request.setAttribute("WCBFormNeeds", wcbFormNeeds);
        }
        BcBillStaging.save(request, bill);
    }

    private void fillBean(HttpServletRequest request, BillingSessionBean bean) {
        bean.setApptProviderNo(request.getParameter("apptProvider_no"));
        bean.setPatientName(request.getParameter("demographic_name"));
        bean.setProviderView(request.getParameter("providerview"));
        bean.setBillRegion(request.getParameter("billRegion"));
        bean.setBillForm(request.getParameter("billForm"));
        bean.setPatientNo(request.getParameter("demographic_no"));
        bean.setApptNo(request.getParameter("appointment_no"));
        bean.setApptDate(request.getParameter("appointment_date"));
        bean.setApptStart(request.getParameter("start_time"));
        bean.setApptStatus(request.getParameter("status"));
    }

    @StrutsParameter(depth = 1)
    public BillingCreateBilling2Form getForm() {
        return form;
    }

    @StrutsParameter
    public void setForm(BillingCreateBilling2Form form) {
        this.form = form;
    }
}
