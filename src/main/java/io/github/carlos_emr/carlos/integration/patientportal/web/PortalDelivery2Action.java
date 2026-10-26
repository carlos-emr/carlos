/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryApplicationService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.struts2.ServletActionContext;

/** One authorized eDoc/device send. All mutations use POST and the existing CSRFGuard boundary. */
public final class PortalDelivery2Action extends PortalJsonAction {
    private static final long serialVersionUID = 1L;
    private int patient;
    private int document;
    private String deliveryId;
    private boolean available;
    private transient PortalMyCarlosDeliveryApplicationService.State state;

    @Override protected String handleRequest() throws IOException {
        var request = ServletActionContext.getRequest(); var response = ServletActionContext.getResponse();
        String operation = request.getParameter("method");
        boolean page = operation == null || "page".equals(operation);
        boolean read = page || "read".equals(operation);
        if (!(read ? "GET" : "POST").equals(request.getMethod())) return methodNotAllowed(response);
        if (!read && !"send".equals(operation) && !"refresh".equals(operation)) return badRequest(response, getText("mycarlos.delivery.changed"));
        patient = positiveInt(request.getParameter("demographicNo")); document = positiveInt(request.getParameter("documentId"));
        if (patient <= 0 || document <= 0) return badRequest(response, getText("mycarlos.delivery.changed"));
        var info = LoggedInInfo.getLoggedInInfoFromSession(request);
        var security = SpringUtils.getBean(SecurityInfoManager.class);
        requirePatientAccess(security, info, patient);
        requirePatientPrivilege(security, info, PortalMyCarlosDeliveryApplicationService.OBJECT, SecurityInfoManager.WRITE, patient);
        available = PortalMyCarlosDeliveryApplicationService.isEnabled();
        try { deliveryId = page ? pageDeliveryId(request.getParameter("deliveryId")) : request.getParameter("deliveryId"); }
        catch (IllegalArgumentException malformed) { return badRequest(response, getText("mycarlos.delivery.changed")); }
        if (!available) return page ? SUCCESS : unavailable(response);
        var service = SpringUtils.getBean(PortalMyCarlosDeliveryApplicationService.class);
        try {
            if (page || "read".equals(operation)) state = service.read(info, patient, document, cursor(request.getParameter("before")), portalService());
            else if ("send".equals(operation)) state = service.send(info, patient, document, deliveryId, request.getParameter("deviceKeyId"), portalService());
            else state = service.refresh(info, patient, document, portalService());
            if (page) return SUCCESS;
            var json = newPayload().put("ok", true).put("title", state.title());
            if (state.nextCursor() == null) json.putNull("nextCursor"); else json.put("nextCursor", state.nextCursor());
            if (state.syncStatus() == null) json.putNull("syncStatus"); else json.put("syncStatus", state.syncStatus());
            var devices = json.putArray("devices");
            for (var device : state.devices()) devices.addObject().put("deviceKeyId", device.deviceKeyId())
                    .put("fingerprint", device.fingerprint()).put("acceptedAt", device.acceptedAt().toString());
            var deliveries = json.putArray("deliveries");
            for (var row : state.deliveries()) deliveries.addObject().put("deliveryId", row.deliveryId())
                    .put("deviceKeyId", row.deviceKeyId()).put("queuedAt", row.queuedAt().toString()).put("status", row.status());
            return write(response, 200, json);
        } catch (PatientPortalException failure) { return portalFailure(response, failure); }
        catch (IllegalArgumentException failure) { return badRequest(response, getText("mycarlos.delivery.changed")); }
        catch (IllegalStateException | IOException failure) {
            if (page) { available = false; return SUCCESS; }
            return unavailable(response);
        }
    }

    private String unavailable(jakarta.servlet.http.HttpServletResponse response) throws IOException {
        return write(response, 503, newPayload().put("ok", false).put("message", getText("mycarlos.delivery.unavailable")));
    }
    private static long cursor(String value) {
        if (value == null || value.isEmpty()) return 0;
        if (!value.matches("0|[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
        return Long.parseLong(value);
    }
    private static String pageDeliveryId(String supplied) {
        if (supplied == null) return UUID.randomUUID().toString();
        UUID id = UUID.fromString(supplied);
        if (!id.toString().equals(supplied) || id.version() != 4 || id.variant() != 2) throw new IllegalArgumentException();
        return supplied;
    }
    @Override String allowedMethods() { return "GET, POST"; }
    public int getDemographicNo() { return patient; }
    public int getDocumentId() { return document; }
    public String getDeliveryId() { return deliveryId; }
    public boolean isAvailable() { return available; }
    public String getDocumentTitle() { return state == null ? "" : state.title(); }
    public List<PortalMyCarlosDeliveryApplicationService.Device> getDevices() { return state == null ? List.of() : state.devices(); }
    public List<PortalMyCarlosDeliveryApplicationService.Delivery> getDeliveries() { return state == null ? List.of() : state.deliveries(); }
    public String getNextCursor() { return state == null ? null : state.nextCursor(); }
}
