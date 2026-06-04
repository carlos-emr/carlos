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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Updates an existing {@link BillingPaymentType}: looks up by
 * {@code oldPaymentType}, and if a row exists and the requested new
 * {@code paymentType} doesn't collide, persists the rename. Returns
 * {@code application/json}: {@code {ret: 0}} on success or
 * {@code {ret: 1, reason: ...}} on missing/conflict/error. Returns
 * {@code null} from execute() so Struts skips result rendering.
 *
 * <p>Split out of the legacy {@code PaymentType2Action#editType} multi-method
 * dispatcher so each URL has a single responsibility.</p>
 *
 * @since 2026-04-27
 */
public class UpdatePaymentType2Action extends ActionSupport {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SecurityInfoManager securityInfoManager;
    private final BillingPaymentTypeDao billingPaymentTypeDao;

    public UpdatePaymentType2Action(SecurityInfoManager securityInfoManager,
                                    BillingPaymentTypeDao billingPaymentTypeDao) {
        this.securityInfoManager = securityInfoManager;
        this.billingPaymentTypeDao = billingPaymentTypeDao;
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

        String oldPaymentType = request.getParameter("oldPaymentType");
        String paymentType = request.getParameter("paymentType");
        if (oldPaymentType == null || oldPaymentType.isEmpty()
                || paymentType == null || paymentType.isEmpty()) {
            return null;
        }

        Map<String, String> ret = new HashMap<>();
        try {
            BillingPaymentType existing = billingPaymentTypeDao.getPaymentTypeByName(oldPaymentType);
            if (existing == null) {
                ret.put("ret", "1");
                ret.put("reason", "Old payment type: " + oldPaymentType + " doesn't exist!");
            } else {
                BillingPaymentType clash = billingPaymentTypeDao.getPaymentTypeByName(paymentType);
                if (clash != null) {
                    ret.put("ret", "1");
                    ret.put("reason", "Payment type: " + paymentType + " already exists!");
                } else {
                    existing.setPaymentType(paymentType);
                    billingPaymentTypeDao.merge(existing);
                    ret.put("ret", "0");
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to update payment type {} -> {}",
                    LogSafe.sanitize(oldPaymentType),
                    LogSafe.sanitize(paymentType), e);
            ret.put("ret", "1");
            ret.put("reason", "Failed to update payment type; see server logs.");
        }

        writeJsonResponse(response, ret);
        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private static void writeJsonResponse(HttpServletResponse response, Map<String, String> body) {
        try {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep -- JSON API response with application/json content-type
            response.getWriter().write(objectMapper.valueToTree(body).toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Failed to write JSON response", e);
        }
    }
}
