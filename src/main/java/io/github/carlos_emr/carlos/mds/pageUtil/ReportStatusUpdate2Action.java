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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.mds.pageUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Calendar;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ReportStatusUpdate2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public ReportStatusUpdate2Action() {
    }

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Dispatches the POST-only status or comment mutation selected by the request method parameter.
     * @return NONE after writing the response, with no subsequent Struts view
     * @throws ServletException for servlet dispatch failures
     * @throws IOException for response I/O failures
     * @throws SecurityException if the caller lacks lab write access
     */
    public String execute() throws ServletException, IOException {
        if ("addComment".equals(request.getParameter("method"))) {
            return addComment();
        }
        return executemain();
    }

    /**
     * Updates the session provider's reviewed report and trusted older version chain.
     * Non-POST requests receive 405 before any mutation; authorized POSTs receive JSON
     * including the actual number of routing rows removed from NEW.
     * @return NONE because this method writes the complete response
     * @throws SecurityException if lab write access is missing
     * @throws NumberFormatException if the report identifier cannot be parsed
     */
    public String executemain() {

        if (!requirePost()) {
            return NONE;
        }
        if (!requireCanonicalReportType()) return NONE;

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        int labNo = Integer.parseInt(request.getParameter("segmentID"));
        String multiID = request.getParameter("multiID");
        // Session-derived, NOT the posted providerNo: this writes the acknowledgement into the
        // clinical audit trail, and a posted value let any user with _lab write record it
        // against a colleague. An encounter link can name a different routed provider, but
        // that must not let this acknowledgement impersonate them; their inbox remains
        // outstanding until they acknowledge it. The macro path uses the same policy.
        String providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        char status = request.getParameter("status").charAt(0);
        String comment = request.getParameter("comment");
        String lab_type = request.getParameter("labType");
        String ajaxcall = request.getParameter("ajaxcall");

        try {
            if ("DOC".equals(lab_type)) {
                io.github.carlos_emr.carlos.documentManager.IncomingDocumentCapacityResponse.requireStoredDocumentWriteAccess(
                        securityInfoManager, LoggedInInfo.getLoggedInInfoFromSession(request), String.valueOf(labNo));
            }
            // A real acknowledgement failure throws (handled below); updateReportStatus otherwise
            // persists the status. Its boolean is not an ack-success signal — do not gate the
            // response on it.
            // Whatever the status, the older versions of the same lab are filed with it: that
            // is what actually clears the collapsed inbox row, and it is what this endpoint has
            // always done. Shared with the macro path so the two ways of acknowledging a lab
            // cannot drift apart again.
            ReportUpdate update = withDocumentWriteLock(labNo, lab_type, () -> {
                // Resolve the audit patient after waiting and reauthorizing the actual document.
                // A simultaneous reassignment must not leave this ACK referencing the old chart.
                String demographic = status == 'A' ? getDemographicIdFromLab(lab_type, labNo) : null;
                int cleared = CommonLabResultData.updateReportStatusWithOlderVersions(
                        labNo, providerNo, status, comment, lab_type, false, multiID);
                return new ReportUpdate(cleared, demographic);
            });
            if (status == 'A') {
                try {
                    LogAction.addLog(providerNo, LogConst.ACK, LogConst.CON_HL7_LAB,
                            "" + labNo, request.getRemoteAddr(), update.demographic());
                } catch (RuntimeException auditFailure) {
                    // Routing has committed. Do not advertise a retryable mutation failure
                    // if the separate legacy audit writer is unavailable.
                    logger.error("Lab acknowledgement committed but its ACK audit write failed ({})",
                            auditFailure.getClass().getSimpleName());
                }
            }
            if (ajaxcall != null && ajaxcall.equals("yes")) {
                // The browser cannot work this number out for itself. It walks the posted
                // multiID, which the server ignores for HL7 in favour of the chain it derives
                // from the accession number, and which says nothing about which of those
                // versions were still NEW. Reporting it is what keeps the inbox badge in step
                // with the figure the next page load computes.
                writeClearedCount(update.cleared());
                return NONE;
            }
            return SUCCESS;
        } catch (Exception e) {
            if ("DOC".equals(lab_type)) response.setStatus(e instanceof SecurityException ? 403 : 500);
            logger.error("exception in ReportStatusUpdate2Action ({})", e.getClass().getSimpleName());
            return "failure";
        }
    }

    /**
     * Saves a literal comment on the session provider's report routing record via POST.
     * @return NONE after writing the response; GET/HEAD return 405 without a write
     * @throws SecurityException if lab write access is missing
     * @throws NumberFormatException if the report identifier cannot be parsed
     */
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String addComment() {
        if (!requirePost()) {
            return NONE;
        }
        if (!requireCanonicalReportType()) return NONE;
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }
        int labNo = Integer.parseInt(request.getParameter("segmentID"));
        // Session-derived for the same reason as executemain(): a comment on a lab is signed
        // by the provider it is recorded against.
        String providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        char status = request.getParameter("status").charAt(0);
        String comment = request.getParameter("comment");
        String lab_type = request.getParameter("labType");

        try {

            withDocumentWriteLock(labNo, lab_type, () -> CommonLabResultData.updateReportStatus(labNo, providerNo, status, comment, lab_type));

        } catch (Exception e) {
            if ("DOC".equals(lab_type)) response.setStatus(e instanceof SecurityException ? 403 : 500);
            logger.error("exception in setting comment ({})", e.getClass().getSimpleName());
            return "failure";
        }

        String now = ConversionUtils.toDateString(Calendar.getInstance().getTime(), "dd-MMM-yy HH mm");
        ObjectNode json = objectMapper.createObjectNode();
        json.put("date", now);
        logger.info("JSON " + json.toString());
        response.setContentType("application/json");
        try {
            response.getWriter().write(json.toString());
            response.flushBuffer();
        } catch (IOException e) {
            logger.error("FAILED TO RETURN DATE ({})", e.getClass().getSimpleName());
        }

        return NONE;
    }

    /** Keep document authorization and routing together while other sessions wait on its row. */
    private <T> T withDocumentWriteLock(int document, String type, java.util.function.Supplier<T> mutation) {
        if (!"DOC".equals(type)) return mutation.get();
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
        String id = String.valueOf(document);
        io.github.carlos_emr.carlos.documentManager.IncomingDocumentCapacityResponse.requireStoredDocumentWriteAccess(
                securityInfoManager, info, id);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                SpringUtils.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        return java.util.Objects.requireNonNull(transaction.execute(status -> {
            if (SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.DocumentDao.class).findForPageMutation(document) == null) {
                throw new SecurityException("Document is not available");
            }
            io.github.carlos_emr.carlos.documentManager.IncomingDocumentCapacityResponse.requireStoredDocumentWriteAccess(
                    securityInfoManager, info, id);
            return mutation.get();
        }), "Document status update did not return a result");
    }

    private record ReportUpdate(int cleared, String demographic) { }

    private boolean requireCanonicalReportType() {
        String[] types = request.getParameterValues("labType");
        // SQL routing columns may use case/accent-insensitive, space-padding collations.
        // Only canonical protocol values may select a source-specific authorization boundary.
        if (types != null && types.length == 1 && types[0] != null && java.util.Set.of(
                "MDS", "CML", "BCP", "HL7", "DOC", "Epsilon", "HRM", "Spire", "ALPHA", "TRUENORTH").contains(types[0])) return true;
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return false;
    }

    private boolean requirePost() {
        if ("POST".equals(request.getMethod())) {
            return true;
        }
        response.setHeader("Allow", "POST");
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return false;
    }

    /**
     * Writes the acknowledge/file response for the AJAX callers, and only for them.
     *
     * <p>A failure to write it is logged and swallowed: the status change is already
     * persisted, and turning a delivery problem into an error would tell the clinician their
     * acknowledgement failed when it did not. The inbox counter is corrected by the next page
     * load in that case.
     */
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private void writeClearedCount(int clearedCount) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("clearedCount", clearedCount);
        response.setContentType("application/json; charset=UTF-8");
        try {
            response.getWriter().write(json.toString());
            response.flushBuffer();
        } catch (IOException e) {
            logger.error("failed to return the cleared routing row count ({})", e.getClass().getSimpleName());
        }
    }

    private static String getDemographicIdFromLab(String labType, int labNo) {
        String demographicID = "";
        PatientLabRoutingDao dao = SpringUtils.getBean(PatientLabRoutingDao.class);
        for (PatientLabRouting r : dao.findByLabNoAndLabType(labNo, labType)) {
            demographicID = "" + r.getDemographicNo();
        }
        return demographicID;
    }
}
