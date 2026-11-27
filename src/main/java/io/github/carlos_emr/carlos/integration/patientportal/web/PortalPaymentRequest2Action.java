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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.model.PortalPaymentRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalPaymentRequestRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.integration.patientportal.payment.PortalPaymentRequestException;
import io.github.carlos_emr.carlos.integration.patientportal.payment.PortalPaymentRequestService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.apache.struts2.ServletActionContext;

/**
 * Staff JSON contract for asking a patient, through the portal, to pay an Ontario invoice (billing
 * through the portal, Phase 0). {@code POST} only, {@code method=create|list|withdraw}.
 *
 * <p>Sending and withdrawing need the portal payment object and billing write; listing needs both
 * for read. The amount is never a parameter: the service reads the invoice's balance owing and GST.
 * The CSRFGuard filter protects every POST, including the read operation.
 */
public class PortalPaymentRequest2Action extends PortalJsonAction {
    private static final long serialVersionUID = 1L;
    /** The invoice is a billing record, so the same write right that saves a payment is required. */
    static final String OBJECT_BILLING = "_billing";
    private final transient SecurityInfoManager security;
    private final transient PortalStaffContextResolver resolver;
    private final transient PortalPaymentRequestService injectedRequests;

    public PortalPaymentRequest2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), null,
                SpringUtils.getBean(PortalStaffContextResolver.class), null);
    }

    PortalPaymentRequest2Action(SecurityInfoManager security, PatientPortalService service,
            PortalStaffContextResolver resolver, PortalPaymentRequestService requests) {
        super(service);
        this.security = security;
        this.resolver = resolver;
        this.injectedRequests = requests;
    }

    /** Looked up only once the portal is known to be configured, so a clinic without one needs no beans. */
    private PortalPaymentRequestService requests() {
        return injectedRequests != null ? injectedRequests : SpringUtils.getBean(PortalPaymentRequestService.class);
    }

    @Override
    protected String handleRequest() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            return methodNotAllowed(response);
        }
        LoggedInInfo session = LoggedInInfo.getLoggedInInfoFromSession(request);
        int patient = positiveInt(request.getParameter("demographicNo"));
        if (patient <= 0) {
            return badRequest(response, "a patient must be selected");
        }
        requirePatientAccess(security, session, patient);
        String method = request.getParameter("method");
        if (!Set.of("create", "list", "withdraw").contains(method == null ? "" : method)) {
            return badRequest(response, "unsupported payment request action");
        }
        String level = "list".equals(method) ? SecurityInfoManager.READ : SecurityInfoManager.WRITE;
        requirePatientPrivilege(security, session, PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST, level, patient);
        requirePatientPrivilege(security, session, OBJECT_BILLING, level, patient);
        int billingNo = positiveInt(request.getParameter("billingNo"));
        if (billingNo <= 0) {
            return badRequest(response, "an invoice must be selected");
        }
        String category = null;
        LocalDate dueOn = null;
        long requestId = 0;
        if ("create".equals(method)) {
            category = request.getParameter("category");
            if (category == null || !PatientPortalPaymentRequestRequest.CATEGORIES.contains(category)) {
                return badRequest(response, "payment request is invalid");
            }
            String due = request.getParameter("dueOn");
            try {
                dueOn = due == null ? null : LocalDate.parse(due);
            } catch (DateTimeParseException invalid) {
                dueOn = null;
            }
            if (dueOn == null) {
                return badRequest(response, "payment request is invalid");
            }
        } else if ("withdraw".equals(method)) {
            requestId = positiveLong(request.getParameter("requestId"));
            if (requestId <= 0) {
                return badRequest(response, "a payment request must be selected");
            }
        }
        PatientPortalService portal = portalService();
        if (portal == null) {
            return portalNotConfigured(response);
        }
        PatientPortalStaffContext staff = resolver.resolveForPatient(session,
                Set.of(PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST), patient);
        PortalPaymentRequestService service = requests();
        boolean mutationAttempted = false;
        try {
            ObjectNode payload = newPayload();
            payload.put("ok", true);
            if ("create".equals(method)) {
                mutationAttempted = true;
                PortalPaymentRequestService.Sent sent = service.send(staff, session.getLoggedInProviderNo(),
                        patient, billingNo, category, dueOn);
                audit(session, sent.created() ? "PortalPaymentRequest2Action.create"
                        : "PortalPaymentRequest2Action.create.confirmed", sent.request().getId(), patient, "");
                payload.set("request", requestJson(sent.request()));
                payload.put("created", sent.created());
                return write(response, sent.created() ? HttpServletResponse.SC_CREATED
                        : HttpServletResponse.SC_OK, payload);
            }
            if ("withdraw".equals(method)) {
                mutationAttempted = true;
                PortalPaymentRequest withdrawn = service.withdraw(staff, patient, billingNo, requestId);
                audit(session, "PortalPaymentRequest2Action.withdraw", withdrawn.getId(), patient, "");
                payload.set("request", requestJson(withdrawn));
                return write(response, HttpServletResponse.SC_OK, payload);
            }
            var items = payload.putArray("requests");
            for (PortalPaymentRequest listed : service.listForInvoice(staff, patient, billingNo)) {
                if (listed.getDemographicNo() == patient) {
                    items.add(requestJson(listed));
                }
            }
            return write(response, HttpServletResponse.SC_OK, payload);
        } catch (PortalPaymentRequestException refused) {
            return refused.reason() == PortalPaymentRequestException.Reason.INVOICE_NOT_FOUND
                    || refused.reason() == PortalPaymentRequestException.Reason.REQUEST_NOT_FOUND
                    ? notFound(response, refused.reason().code(), refused.getMessage())
                    : conflict(response, refused.reason().code(), refused.getMessage());
        } catch (PatientPortalException exception) {
            if (exception.isAccountAbsent()) {
                return notFound(response, "portal_account_inactive",
                        "This patient does not have an active portal account. Contact the patient directly.");
            }
            if (mutationAttempted) {
                auditIfUnconfirmed(session, "PortalPaymentRequest2Action." + method, patient, exception);
            }
            return portalFailure(response, exception);
        }
    }

    /**
     * The row as staff see it: the invoice reference stays server-side, and the amount is the one
     * that was sent, so the screen shows exactly what the patient reads.
     */
    private ObjectNode requestJson(PortalPaymentRequest row) {
        ObjectNode node = newPayload();
        node.put("id", row.getId());
        node.put("status", row.getStatus());
        node.put("category", row.getCategory());
        node.put("amountCents", row.getAmountCents());
        node.put("taxCents", row.getTaxCents());
        node.put("dueOn", row.getDueOn().toString());
        node.put("createdAt", row.getCreatedAt().toInstant().toString());
        node.put("paidAt", row.getPaidAt() == null ? null : row.getPaidAt().toInstant().toString());
        node.put("portalSynced", row.isPortalSynced());
        return node;
    }
}
