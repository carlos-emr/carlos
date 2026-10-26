/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosStaffApprovalService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.IOException;
import org.apache.struts2.ServletActionContext;

/** Patient-scoped staff approval; CSRFGuard protects POST exactly as other portal actions. */
public final class PortalDeviceApproval2Action extends PortalJsonAction {
    private static final long serialVersionUID = 1L;
    private final transient SecurityInfoManager security;

    public PortalDeviceApproval2Action() { this(SpringUtils.getBean(SecurityInfoManager.class)); }
    PortalDeviceApproval2Action(SecurityInfoManager security) { this.security = security; }

    @Override protected String handleRequest() throws IOException {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        String operation = request.getParameter("method");
        boolean read = operation == null || "read".equals(operation);
        if (!(read ? "GET" : "POST").equals(request.getMethod())) return methodNotAllowed(response);
        if (!read && !"refresh".equals(operation) && !"approveFirst".equals(operation)) return badRequest(response, getText("mycarlos.approval.error.changed"));
        int patient = positiveInt(request.getParameter("demographicNo"));
        if (patient <= 0) return badRequest(response, getText("mycarlos.approval.error.changed"));
        var info = LoggedInInfo.getLoggedInInfoFromSession(request);
        requirePatientAccess(security, info, patient);
        requirePatientPrivilege(security, info, "_portal.mycarlos.device_approval",
                read ? SecurityInfoManager.READ : SecurityInfoManager.WRITE, patient);
        if (!PortalMyCarlosStaffApprovalService.isEnabled()) return unavailable(response);
        var service = SpringUtils.getBean(PortalMyCarlosStaffApprovalService.class);
        try {
            PortalMyCarlosStaffApprovalService.State state;
            if (read) state = service.read(info, patient);
            else if ("refresh".equals(operation)) state = service.refresh(info, patient, portalService());
            else {
                if (!"true".equals(request.getParameter("confirmed"))) throw new SecurityException("device confirmation required");
                state = service.approveFirst(info, patient, request.getParameter("challengeId"),
                        request.getParameter("challengeSha256"), request.getParameter("fingerprint"), portalService());
            }
            var json = newPayload().put("ok", true);
            var refusals = json.putArray("refusedRequests");
            state.refusedRequests().forEach(refusals::add);
            var pending = json.putArray("pending");
            for (var row : state.pending()) pending.addObject().put("challengeId", row.challengeId())
                    .put("challengeSha256", row.challengeSha256()).put("fingerprint", row.fingerprint())
                    .put("expiresAt", row.expiresAt().toString()).put("firstDevice", row.firstDevice());
            var devices = json.putArray("devices");
            for (var row : state.devices()) {
                var device = devices.addObject().put("deviceKeyId", row.deviceKeyId()).put("fingerprint", row.fingerprint())
                        .put("acceptedAt", row.acceptedAt().toString());
                if (row.removedAt() == null) device.putNull("removedAt");
                else device.put("removedAt", row.removedAt().toString());
            }
            return write(response, 200, json);
        } catch (PatientPortalException failure) { return portalFailure(response, failure); }
        catch (IllegalArgumentException failure) { return badRequest(response, getText("mycarlos.approval.error.changed")); }
        catch (IllegalStateException failure) { return unavailable(response); }
    }
    private String unavailable(jakarta.servlet.http.HttpServletResponse response) throws IOException {
        return write(response, 503, newPayload().put("ok", false).put("reason", "approval_unavailable")
                .put("message", getText("mycarlos.approval.status.unavailable")));
    }
    @Override String allowedMethods() { return "GET, POST"; }
}
