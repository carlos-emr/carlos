/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.owasp.encoder.Encode;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

/** Responses that distinguish unaccepted incoming work from an uncertain filing outcome. */
public final class IncomingDocumentCapacityResponse {
    public static final String CONTRACT_HEADER = "X-Carlos-Incoming-Filing";
    public static final String CONTRACT_VERSION = "bounded-v1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> QUEUES = Set.of("Fax", "Mail", "File", "Refile");

    private IncomingDocumentCapacityResponse() { }

    @SuppressFBWarnings(value = "SERVLET_HEADER",
            justification = "Exact client-supplied version selects JSON versus HTML response representation only; "
                    + "patient/queue authorization, path validation and filing acceptance do not trust this header")
    public static boolean usesContract(HttpServletRequest request) {
        return CONTRACT_VERSION.equals(request.getHeader(CONTRACT_HEADER));
    }

    /** No submitted method, file name, clinical content or mutation parameters enter this GET. */
    public static String readOnlyUrl(HttpServletRequest request, String queue, String directory,
                                     String documentIndex, String page) {
        if (!positiveId(queue) || directory == null || !QUEUES.contains(directory)) {
            throw new IllegalArgumentException("Invalid incoming queue location");
        }
        String next = request.getContextPath() + "/documentManager/ViewIncomingDocs?queueId=" + queue
                + "&pdfDir=" + Encode.forUriComponent(directory) + "&pdfNo=" + positiveOrOne(documentIndex)
                + "&pdfPageNumber=" + positiveOrOne(page);
        String lastPatient = request.getParameter("lastdemographic_no");
        if (positiveId(lastPatient)) next += "&lastdemographic_no=" + lastPatient;
        String mode = request.getParameter("entryMode");
        if ("Normal".equals(mode) || "Fast".equals(mode)) next += "&entryMode=" + mode;
        return next;
    }

    /** Positive database identity, with the same integer range used by the incoming viewer. */
    public static boolean positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,9}")) return false;
        try { return Integer.parseInt(value) > 0; }
        catch (NumberFormatException ignored) { return false; }
    }

    /** Default queue is shared, matching DmsInboxManage; named queues require read access. */
    public static void requireQueueAccess(io.github.carlos_emr.carlos.managers.SecurityInfoManager security,
                                          io.github.carlos_emr.carlos.utility.LoggedInInfo info, String queue) {
        if (!positiveId(queue)) throw new SecurityException("Invalid directory parameters");
        if (Integer.parseInt(queue) != io.github.carlos_emr.carlos.commn.model.Queue.DEFAULT_QUEUE_ID
                && !security.hasPrivilege(info, "_queue." + queue, "r", (String) null)) {
            throw new SecurityException("You do not have access to this document queue.");
        }
    }

    /** A refile may read only patient records and active queues visible to this operator. */
    public static void requireRefileSourceAccess(io.github.carlos_emr.carlos.managers.SecurityInfoManager security,
                                                 io.github.carlos_emr.carlos.utility.LoggedInInfo info, int documentNo) {
        io.github.carlos_emr.carlos.documentManager.annotation.DocumentPatientLink.requireAccess(info, documentNo, security,
                io.github.carlos_emr.carlos.utility.SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao.class));
        boolean hasActiveQueue = false;
        for (io.github.carlos_emr.carlos.commn.model.QueueDocumentLink link :
                io.github.carlos_emr.carlos.utility.SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class)
                        .getQueueFromDocument(documentNo)) {
            if (!"A".equals(link.getStatus())) continue;
            hasActiveQueue = true;
            if (link.getQueueId() == io.github.carlos_emr.carlos.commn.model.Queue.DEFAULT_QUEUE_ID
                    || security.hasPrivilege(info, "_queue." + link.getQueueId(), "r", (String) null)) return;
        }
        if (hasActiveQueue) throw new SecurityException("You do not have access to the source document queue.");
    }

    /** Authorize stored-view metadata and revision bytes without requiring edit permission. */
    public static void requireStoredDocumentReadAccess(io.github.carlos_emr.carlos.managers.SecurityInfoManager security,
                                                       io.github.carlos_emr.carlos.utility.LoggedInInfo info, int documentNo) {
        if (info == null || !security.hasPrivilege(info, "_edoc", "r", (String) null)) {
            throw new SecurityException("Document read access is required");
        }
        requireRefileSourceAccess(security, info, documentNo);
        Set<Integer> linkedPatients = new java.util.HashSet<>();
        for (io.github.carlos_emr.carlos.commn.model.CtlDocument link :
                io.github.carlos_emr.carlos.utility.SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao.class)
                        .findByDocumentNoAndModule(documentNo, "demographic")) {
            Integer patient = link.getId().getModuleId();
            if (patient != null && patient > 0) linkedPatients.add(patient);
        }
        for (io.github.carlos_emr.carlos.commn.model.PatientLabRouting route :
                io.github.carlos_emr.carlos.utility.SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao.class)
                        .findDocByDemographic(documentNo)) {
            Integer patient = route.getDemographicNo();
            // ctl_document patients were checked by the shared source gate. Also
            // cover valid patient routing while a document is still unfiled.
            if (patient != null && patient > 0 && linkedPatients.add(patient)
                    && !security.isAllowedAccessToPatientRecord(info, patient)) {
                throw new SecurityException("Unauthorized access to patient record");
            }
        }
        for (Integer patient : linkedPatients) {
            if (!security.hasPrivilege(info, "_edoc", "r", patient.toString())) {
                throw new SecurityException("Patient document read access denied");
            }
        }
    }

    private static String positiveOrOne(String value) {
        try { return String.valueOf(Math.max(1, Integer.parseInt(value))); }
        catch (NumberFormatException ignored) { return "1"; }
    }

    private static String waitingMessage(HttpServletRequest request) {
        return ResourceBundle.getBundle("oscarResources", request.getLocale())
                .getString("faxAnnotateViewer.status.documentServerBusy");
    }

    private static void busyHeaders(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", "1");
        response.setHeader("Cache-Control", "no-store");
    }

    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "All dynamic text and attributes are OWASP-encoded; retry URL contains only validated queue and numeric read parameters")
    public static void pageCountBusy(HttpServletRequest request, HttpServletResponse response, Writer out,
                                     String queue, String directory, String documentIndex, String page,
                                     String previousActionError) throws IOException {
        String retry = readOnlyUrl(request, queue, directory, documentIndex, page);
        busyHeaders(response);
        response.setContentType("text/html;charset=UTF-8");
        boolean canRetry = previousActionError == null || previousActionError.isEmpty();
        out.write("<!DOCTYPE html><html><body><p id=\"incomingDocumentPageCountWait\" role=\"status\" data-auto-retry=\""
                + canRetry + "\" data-retry-url=\"" + Encode.forHtmlAttribute(retry) + "\">"
                + Encode.forHtml(waitingMessage(request)) + "</p>");
        if (!canRetry) out.write("<p role=\"alert\">" + Encode.forHtml(previousActionError) + "</p>");
        out.write("<a href=\"" + Encode.forHtmlAttribute(retry) + "\">" + Encode.forHtml(ResourceBundle.getBundle("oscarResources", request.getLocale()).getString("global.btnContinue")) + "</a><script src=\""
                + Encode.forHtmlAttribute(request.getContextPath() + "/js/incomingDocumentPageCountWait.js")
                + "\"></script></body></html>");
    }

    /** A parse error cannot safely replay the request that led to this view. */
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "Messages and manual read-only navigation URL are OWASP encoded")
    public static void pageCountFailed(HttpServletRequest request, HttpServletResponse response, Writer out,
                                       String queue, String directory, String documentIndex, String page,
                                       String previousActionError) throws IOException {
        String retry = readOnlyUrl(request, queue, directory, documentIndex, page);
        ResourceBundle messages = ResourceBundle.getBundle("oscarResources", request.getLocale());
        response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        out.write("<!DOCTYPE html><html><body><p role=\"alert\">"
                + Encode.forHtml(messages.getString("dms.incomingDocs.parseFailed")) + "</p>");
        if (previousActionError != null && !previousActionError.isEmpty()) {
            out.write("<p role=\"alert\">" + Encode.forHtml(previousActionError) + "</p>");
        }
        out.write("<a href=\"" + Encode.forHtmlAttribute(retry) + "\">"
                + Encode.forHtml(messages.getString("global.btnContinue")) + "</a></body></html>");
    }

    /** Only used for an explicit pre-dispatch source-lease refusal. */
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "All posted names/values and fixed-context URLs are OWASP attribute encoded")
    public static void pageEditBusy(HttpServletRequest request, HttpServletResponse response, Writer out,
                                    String queue, String directory, String documentIndex) throws IOException {
        busyHeaders(response);
        response.setContentType("text/html;charset=UTF-8");
        String refresh = readOnlyUrl(request, queue, directory, documentIndex, "1");
        ResourceBundle messages = ResourceBundle.getBundle("oscarResources", request.getLocale());
        out.write("<!DOCTYPE html><html><body><p role=\"status\">" + Encode.forHtml(waitingMessage(request))
                + "</p><form id=\"incoming-page-edit-wait\" data-accepted=\"false\" method=\"post\" action=\""
                + Encode.forHtmlAttribute(request.getContextPath() + "/documentManager/ViewIncomingDocs") + "\">");
        for (Map.Entry<String, String[]> field : request.getParameterMap().entrySet()) {
            for (String value : field.getValue()) out.write("<input type=\"hidden\" name=\""
                    + Encode.forHtmlAttribute(field.getKey()) + "\" value=\"" + Encode.forHtmlAttribute(value) + "\">");
        }
        out.write("</form><a id=\"incoming-page-edit-cancel\" href=\"" + Encode.forHtmlAttribute(refresh) + "\">"
                + Encode.forHtml(messages.getString("global.btnContinue")) + "</a><script src=\""
                + Encode.forHtmlAttribute(request.getContextPath() + "/js/incomingDocumentPageEditWait.js")
                + "\"></script></body></html>");
    }

    /** A stale selection must be explicitly refreshed, never silently given a new revision. */
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "Localized messages and validated read-only URL are OWASP encoded")
    public static void pageSourceChanged(HttpServletRequest request, HttpServletResponse response, Writer out,
                                         String queue, String directory, String documentIndex) throws IOException {
        String refresh = readOnlyUrl(request, queue, directory, documentIndex, "1");
        ResourceBundle messages = ResourceBundle.getBundle("oscarResources", request.getLocale());
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        out.write("<!DOCTYPE html><html><body><p role=\"alert\">"
                + Encode.forHtml(messages.getString("documentMutation.sourceChanged")) + "</p><a href=\""
                + Encode.forHtmlAttribute(refresh) + "\">" + Encode.forHtml(messages.getString("global.btnContinue"))
                + "</a></body></html>");
    }

    public static void filingSourceChanged(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setHeader("Cache-Control", "no-store");
        if (usesContract(request)) {
            response.setContentType("application/json;charset=UTF-8");
            JSON.writeValue(response.getWriter(), Map.of("success", false, "accepted", false,
                    "retryable", false, "sourceChanged", true, "error",
                    ResourceBundle.getBundle("oscarResources", request.getLocale()).getString("documentMutation.sourceChanged")));
        } else {
            pageSourceChanged(request, response, response.getWriter(), request.getParameter("queueId"),
                    request.getParameter("pdfDir"), request.getParameter("pdfNo"));
        }
    }

    /** Called only before a source move, database write, audit or routing mutation. */
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "JSON serialized by Jackson; native fallback form fields and URLs are individually OWASP HTML-attribute encoded")
    public static void filingBusy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        busyHeaders(response);
        unaccepted(request, response, true, waitingMessage(request));
    }

    /** A failed parse accepted no filing, but is not a capacity event and must not auto-retry. */
    public static void filingRejected(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
        response.setHeader("Cache-Control", "no-store");
        unaccepted(request, response, false, ResourceBundle.getBundle("oscarResources", request.getLocale())
                .getString("dms.incomingDocs.parseFailed"));
    }

    /** Another session removed the source before this request acquired its lease. */
    public static void filingSourceUnavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
        response.setHeader("Cache-Control", "no-store");
        unaccepted(request, response, false, ResourceBundle.getBundle("oscarResources", request.getLocale())
                .getString("dms.incomingDocs.noLongerAvailable"));
    }

    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "JSON uses Jackson; all dynamic native-form text and attributes use OWASP encoders")
    private static void unaccepted(HttpServletRequest request, HttpServletResponse response, boolean retryable, String message) throws IOException {
        if (usesContract(request)) {
            response.setContentType("application/json;charset=UTF-8");
            JSON.writeValue(response.getWriter(), Map.of("success", false, "retryable", retryable, "accepted", false, "error", message));
            return;
        }
        // Legacy native submissions retain every field (including repeated provider IDs and
        // the CSRF token) after this explicit pre-mutation refusal. No POST is auto-replayed.
        response.setContentType("text/html;charset=UTF-8");
        Writer out = response.getWriter();
        out.write("<!DOCTYPE html><html><body><p role=\"status\">" + Encode.forHtml(message)
                + "</p><form method=\"post\" action=\""
                + Encode.forHtmlAttribute(request.getContextPath() + "/documentManager/ManageDocument") + "\">");
        for (Map.Entry<String, String[]> field : request.getParameterMap().entrySet()) {
            for (String value : field.getValue()) {
                out.write("<input type=\"hidden\" name=\"" + Encode.forHtmlAttribute(field.getKey())
                        + "\" value=\"" + Encode.forHtmlAttribute(value) + "\">");
            }
        }
        out.write("<button type=\"submit\">" + Encode.forHtml(ResourceBundle.getBundle("oscarResources", request.getLocale())
                .getString("dms.incomingDocs.retrySaving")) + "</button></form></body></html>");
    }

    /** Filing started, so an incomplete follow-up must never invite another submission. */
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "Only a localized message is written to HTML through the OWASP HTML-text encoder")
    public static void filingUnconfirmed(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Cache-Control", "no-store");
        String message = ResourceBundle.getBundle("oscarResources", request.getLocale())
                .getString("faxAnnotateViewer.alert.saveUnconfirmed");
        if (usesContract(request)) {
            response.setContentType("application/json;charset=UTF-8");
            JSON.writeValue(response.getWriter(), Map.of("success", false, "accepted", true, "retryable", false, "error", message));
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!DOCTYPE html><html><body><p role=\"alert\">"
                    + Encode.forHtml(message) + "</p></body></html>");
        }
    }

    public static void filed(HttpServletRequest request, HttpServletResponse response, int documentNo) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        String next = readOnlyUrl(request, request.getParameter("queueId"), request.getParameter("pdfDir"),
                request.getParameter("pdfNo"), "1");
        JSON.writeValue(response.getWriter(), Map.of("success", true, "accepted", true,
                "documentNo", documentNo, "nextUrl", next));
    }
}
