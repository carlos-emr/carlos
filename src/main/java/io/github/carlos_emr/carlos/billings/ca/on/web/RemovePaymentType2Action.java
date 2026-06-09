/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Removes a {@link io.github.carlos_emr.carlos.commn.model.BillingPaymentType}
 * by id, refusing the delete when the type is still referenced by an existing
 * payment row. Returns {@code application/json}: {@code {ret: 0}} on success
 * or {@code {ret: 1, reason: ...}} on parse failure / in-use guard.
 * Returns {@code null} from execute() so Struts skips result rendering.
 *
 * <p>Split out of the legacy {@code PaymentType2Action#removeType} multi-method
 * dispatcher so each URL has a single responsibility.</p>
 *
 * @since 2026-04-27
 */
public class RemovePaymentType2Action extends ActionSupport {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SecurityInfoManager securityInfoManager;
    private final BillingPaymentTypeDao billingPaymentTypeDao;
    private final BillingONPaymentDao billPaymentDao;

    public RemovePaymentType2Action(SecurityInfoManager securityInfoManager,
                                    BillingPaymentTypeDao billingPaymentTypeDao,
                                    BillingONPaymentDao billPaymentDao) {
        this.securityInfoManager = securityInfoManager;
        this.billingPaymentTypeDao = billingPaymentTypeDao;
        this.billPaymentDao = billPaymentDao;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        ObjectNode ret = objectMapper.createObjectNode();
        int paymentTypeId;
        try {
            paymentTypeId = Integer.parseInt(request.getParameter("paymentTypeId"));
        } catch (Exception e) {
            MiscUtils.getLogger().error("Invalid paymentTypeId parameter on remove", e);
            ret.put("ret", 1);
            ret.put("reason", "Invalid paymentTypeId");
            writeJsonResponse(response, ret);
            return null;
        }

        if (paymentTypeId == 0) {
            // Explicit reject branch — the legacy code skipped the if-body
            // and wrote an empty {} JSON, which the client JS interprets as
            // missing/undefined fields and either silently fails or throws
            // on `.ret`. Surface a real reason so the operator sees the
            // rejection in the popup.
            MiscUtils.getLogger().info("RemovePaymentType: paymentTypeId=0 rejected (would-be no-op silent ack)");
            ret.put("ret", 1);
            ret.put("reason", "Invalid paymentTypeId");
        } else {
            int count = billPaymentDao.getCountOfPaymentByPaymentTypeId(paymentTypeId);
            if (count == 0) {
                billingPaymentTypeDao.remove(paymentTypeId);
                ret.put("ret", 0);
            } else {
                ret.put("ret", 1);
                ret.put("reason", "This payment type has been used in some payment!");
            }
        }

        writeJsonResponse(response, ret);
        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private static void writeJsonResponse(HttpServletResponse response, ObjectNode body) {
        try {
            response.setCharacterEncoding("utf-8");
            response.setContentType("application/json");
            // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep -- JSON API response with application/json content-type
            response.getWriter().print(body);
            response.getWriter().flush();
            response.getWriter().close();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to write removePaymentType JSON response", e);
        }
    }
}
