/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Recovery of a saved form's render; never carries its clinical save parameters. */
public final class EFormSavedRenderResponse {
    private EFormSavedRenderResponse() { }

    public static String busy(HttpServletRequest request, HttpServletResponse response,
            EFormRenderApprovalService service, LoggedInInfo user, int fdid, String patient,
            EFormRenderApprovalService.Operation operation, EFormRenderApproval approval, boolean autoClose) {
        String token = service.reissueAfterCapacity(request, user, fdid, patient, operation, approval);
        if (token == null) {
            // Expired omission consent is discarded; the replacement authorizes zero omissions.
            token = service.issueCapacityContinuation(request, user, fdid, patient, operation);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("fdid", String.valueOf(fdid));
        fields.put("demographicNo", patient);
        fields.put("renderApproval", token);
        if (autoClose) fields.put("autoClose", "true");
        return EFormRenderCapacityResponse.offer(request, response, operation, fields);
    }

    public static String missing(HttpServletRequest request, EFormRenderApprovalService service,
            LoggedInInfo user, EformContentUnavailableException failure, int fdid, String patient,
            EFormRenderApprovalService.Operation operation, EFormRenderApproval previous, boolean autoClose) {
        boolean edoc = operation == EFormRenderApprovalService.Operation.EDOC;
        return missing(request, service, user, failure, fdid, patient, operation, previous, autoClose,
                edoc ? "This eForm could not be fully rendered, so it was not added to the patient's documents. Review the omissions below before archiving it."
                        : "Some content of this eForm could not be rendered. Review the omissions below before downloading it.",
                edoc ? "eform/saveEFormAsEDoc" : "eform/downloadEFormPdf",
                edoc ? "eform.renderMissingContent.btnApproveAndAddToDocuments" : "eform.renderMissingContent.btnApproveAndDownload");
    }

    public static String missing(HttpServletRequest request, EFormRenderApprovalService service,
            LoggedInInfo user, EformContentUnavailableException failure, int fdid, String patient,
            EFormRenderApprovalService.Operation operation, EFormRenderApproval previous, boolean autoClose,
            String message, String action, String buttonKey) {
        // An expired approval must not seed fresh consent for omissions not shown on this page.
        if (previous != null && !Instant.now().isBefore(previous.expiresAt())) previous = null;
        String token = service.issue(request, user, fdid, patient, operation,
                failure.getReport(), previous, failure.getFdid());
        EFormRenderCompletenessReport report = failure.getReport();
        request.setAttribute("renderApproval", token);
        request.setAttribute("fdid", String.valueOf(fdid));
        request.setAttribute("demographicNo", patient);
        request.setAttribute("missingContentMessage", message);
        request.setAttribute("approvalAction", action);
        request.setAttribute("approvalButtonLabelKey", buttonKey);
        request.setAttribute("failedContentResources", report.failedContentResources());
        request.setAttribute("excludedContentElements", report.excludedContentElements());
        request.setAttribute("severeConsoleErrors", report.severeConsoleErrors());
        request.setAttribute("severeConsoleErrorDetails", failure.getSevereConsoleDetails());
        request.setAttribute("containedInteractions", report.containedInteractions());
        request.setAttribute("decorativeExcludedElements", report.decorativeExcludedElements());
        request.setAttribute("signatureMissing", report.signatureMissing());
        request.setAttribute("timerCompatibilityFailure", report.timerCompatibilityFailure());
        request.setAttribute("stabilizationCapped", report.stabilizationCapped());
        request.setAttribute("labDecisionSupportStubbed", report.labDecisionSupportStubbed());
        request.setAttribute("providerStampMissing", report.providerStampMissing());
        if (autoClose) request.setAttribute("approvalAutoClose", "true");
        return "missingContent";
    }
}
