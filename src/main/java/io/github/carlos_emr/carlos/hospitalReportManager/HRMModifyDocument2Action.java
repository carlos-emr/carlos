/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.hospitalReportManager;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.IncomingLabRulesDao;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentCommentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentComment;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentSubClass;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * POST-only JSON endpoint behind the HRM report viewer's inline controls (comments,
 * description, sign-off, demographic/provider matching, category and sub-class).
 *
 * <p><strong>Why JSON and not a JSP.</strong> Every handler used to forward to a tiny
 * {@code text/html} JSP fragment that said "Success", and {@code hrmActions.js} wrote the
 * response body straight into the page. Anything the response-decorating filter chain adds to a
 * {@code text/html} response therefore ended up on screen: a clinician adding a comment saw the
 * {@link io.github.carlos_emr.carlos.app.LogoutBroadcastFilter} heartbeat script rendered as
 * literal JavaScript next to the comment box, while the comment itself saved correctly. Replying
 * with {@code application/json} written directly to the response — and returning {@link #NONE}
 * per the direct-response contract in CLAUDE.md — keeps those filters out of the body for good,
 * since each of them bails on a non-HTML content type.</p>
 *
 * <p><strong>Why POST-only.</strong> CSRFGuard protects POST/PUT/DELETE/PATCH, and
 * {@code HttpMethodGuardFilter} does not recognise this route's {@code method} values as
 * mutations, so a GET reached the DAOs with no CSRF token at all. The gate runs before
 * authorization and before any handler so no side effect can fire on an unsafe verb.</p>
 */
public class HRMModifyDocument2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /** Body text the viewer shows beside the control that was used. */
    private static final String SUCCESS_MESSAGE = "Success";
    private static final String FAILURE_MESSAGE = "Error encountered";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);
    HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);
    HRMDocumentToProviderDao hrmDocumentToProviderDao = (HRMDocumentToProviderDao) SpringUtils.getBean(HRMDocumentToProviderDao.class);
    HRMDocumentSubClassDao hrmDocumentSubClassDao = (HRMDocumentSubClassDao) SpringUtils.getBean(HRMDocumentSubClassDao.class);
    HRMDocumentCommentDao hrmDocumentCommentDao = (HRMDocumentCommentDao) SpringUtils.getBean(HRMDocumentCommentDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Routes one viewer control to its handler and answers in JSON.
     *
     * <p>Dispatch is by the {@code method} request parameter, not by a Struts method mapping, so
     * Strict Method Invocation keeps every handler below unreachable from a URL. An unsafe verb is
     * rejected with 405 first, then {@code _hrm} write rights, then the dispatch; an unrecognised
     * {@code method} answers 400 rather than a silent success.</p>
     *
     * @return {@link #NONE} in every case — the response body is always written directly, so
     *         Struts must not resolve a result and forward a JSP over the top of it
     * @throws IOException if the response body cannot be written
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws IOException {
        // Unsafe verbs are rejected before authorization and before any handler runs, so no DAO
        // write can be reached by a crafted link.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String method = request.getParameter("method");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        if (method != null) {
            if (method.equalsIgnoreCase("makeIndependent"))
                return makeIndependent();
            else if (method.equalsIgnoreCase("signOff"))
                return signOff();
            else if (method.equalsIgnoreCase("assignProvider"))
                return assignProvider();
            else if (method.equalsIgnoreCase("removeDemographic"))
                return removeDemographic();
            else if (method.equalsIgnoreCase("assignDemographic"))
                return assignDemographic();
            else if (method.equalsIgnoreCase("makeActiveSubClass"))
                return makeActiveSubClass();
            else if (method.equalsIgnoreCase("removeProvider"))
                return removeProvider();
            else if (method.equalsIgnoreCase("addComment"))
                return addComment();
            else if (method.equalsIgnoreCase("deleteComment"))
                return deleteComment();
            else if (method.equalsIgnoreCase("setDescription"))
                return setDescription();
            else if (method.equalsIgnoreCase("updateCategory"))
                return updateCategory();
        }

        // An unrecognised dispatch is a client bug, not a silent success.
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return writeResult(false, "Unsupported HRM modify method");
    }

    /**
     * Writes the single JSON shape every handler replies with and ends Struts processing.
     *
     * <p>{@code message} is display text for the viewer, never an exception string: HRM bodies and
     * the identifiers around them are PHI-correlating, so failures are logged server-side and the
     * clinician is told only that the action did not take.</p>
     *
     * @param success whether the requested change was persisted
     * @param message short status text rendered beside the control that was used
     * @param clearedCount routing rows this call actually took out of the inbox, or null when the
     *        operation does not clear anything. The viewer forwards it to the Inboxhub, whose
     *        badges count routing rows; see {@link #signOff()} for why guessing is not good enough
     * @return {@link #NONE}, because the response body is already written
     */
    // FindSecBugs XSS_SERVLET: the body is Jackson-serialised from a boolean, an int and one of
    // this class's own constant message strings — no request data reaches it — and it is sent as
    // application/json, which the deployment also serves with X-Content-Type-Options: nosniff.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "JSON body built from a boolean, an int and this class's constant message strings; no request data reaches the writer, and the response is application/json")
    private String writeResult(boolean success, String message, Integer clearedCount) throws IOException {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("success", success);
        body.put("message", message);
        if (clearedCount != null) {
            body.put("clearedCount", clearedCount.intValue());
        }

        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body.toString()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer, java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss -- constant JSON status body, no request data
        response.getWriter().flush();
        return NONE;
    }

    /** Writes a status reply that clears nothing from the inbox. */
    private String writeResult(boolean success, String message) throws IOException {
        return writeResult(success, message, null);
    }

    /** Convenience wrapper for handlers that report the standard success/failure wording. */
    private String writeResult(boolean success) throws IOException {
        return writeResult(success, success ? SUCCESS_MESSAGE : FAILURE_MESSAGE);
    }

    /**
     * Breaks one HRM report out of its similar-report group.
     *
     * <p>Reads {@code reportId}. A child report simply loses its parent; a parent hands the group
     * to its earliest child and re-points the remaining siblings at that new parent, so the
     * remaining reports stay grouped with each other.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String makeIndependent() throws IOException {
        boolean success = false;
        String reportId = request.getParameter("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            HRMDocument document = hrmDocumentDao.find(Integer.parseInt(reportId));
            if (document.getParentReport() != null && !document.getParentReport().equals(Integer.parseInt(reportId))) {
                // There is a parent report that isn't itself, implies this is a child document
                document.setParentReport(null);
                hrmDocumentDao.merge(document);
            } else {
                // This is a parent document so we need to find and disassociate all the children documents (if any)
                List<HRMDocument> documentChildren = hrmDocumentDao.getAllChildrenOf(document.getId());
                if (documentChildren != null && documentChildren.size() > 0) {
                    // If there's children, choose the first child (which has the earliest id) and mark its parent as null
                    HRMDocument newParentDoc = documentChildren.get(0);
                    newParentDoc.setParentReport(null);
                    hrmDocumentDao.merge(newParentDoc);

                    // update all children to have this first child as their parent instead
                    for (HRMDocument childDoc : documentChildren) {
                        if (childDoc.getId().intValue() != newParentDoc.getId().intValue()) {
                            childDoc.setParentReport(newParentDoc.getId());
                            hrmDocumentDao.merge(childDoc);
                        }
                    }
                }
            }

            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to set make document independent but failed.", e);
            success = false;
        }

        return writeResult(success);
    }

    /**
     * Sets or clears the signed-off flag on the logged-in provider's routing row for ONE HRM report.
     *
     * <p>Reads exactly one {@code reportId} and one {@code signedOff}, the latter being 0 or 1 and
     * nothing else; anything else is answered with 400, because a single success flag cannot
     * honestly describe a partly-applied batch and because any other state value hides the report
     * from every inbox view. A routing row is created for the provider when none exists, and an
     * unclaimed row ({@code providerNo} of {@code -1}) is claimed rather than duplicated.</p>
     *
     * <p>Replies with {@code clearedCount}: how many routing rows this call actually moved INTO
     * signed-off. The viewer forwards that number to the Inboxhub, whose Documents/Labs/HRMs
     * badges count routing rows and are adjusted client-side because a list re-fetch does not
     * recompute them. Reporting a count the server did not clear — for a report already signed
     * off, or one with no routing row in the inbox the clinician is looking at — walks that badge
     * below the truth until a full page reload. Zero is a real answer and the Inboxhub honours it.
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String signOff() throws IOException {
        String[] reportIds = request.getParameterValues("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        // A sign-off with no report named is a malformed request, not an empty success: reporting
        // "Success" would tell the viewer to clear the report out of the inbox having filed nothing.
        String[] signedOffValues = request.getParameterValues("signedOff");

        // Exactly one report per request. A sign-off with none named is malformed, and so is one
        // naming several: this endpoint answers with a SINGLE success flag and a single
        // clearedCount, which cannot honestly describe a batch that partly succeeded. A batch
        // whose first report persisted and whose second threw would report failure, the viewer
        // would suppress the inbox notification, and the report already signed off in the database
        // would sit in the inbox until the next full reload. The viewer has only ever sent one id,
        // so the loop that allowed this was unreachable capability with an incoherent contract.
        // Supporting batches properly means per-report outcomes or a real transaction boundary,
        // neither of which belongs in a viewer status endpoint.
        if (reportIds == null || reportIds.length != 1
                || signedOffValues == null || signedOffValues.length != 1) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return writeResult(false, FAILURE_MESSAGE);
        }

        // signedOff is a binary state, and the only two values the inbox can see. Its DAO uses 2
        // as an "any" sentinel (HRMDocumentToProviderDao appends "AND x.signedOff = :signedOff"
        // only when the filter is not 2), so a row persisted as 2 matches neither the unsigned
        // (=0) nor the signed-off (=1) query: the report drops out of every inbox view at once.
        // Parse and range-check before anything is looked up or written.
        int signedOffValue;
        try {
            signedOffValue = Integer.parseInt(signedOffValues[0].trim());
        } catch (NumberFormatException e) {
            signedOffValue = -1;
        }
        if (signedOffValue != 0 && signedOffValue != 1) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return writeResult(false, FAILURE_MESSAGE);
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // The id must name a real HRM document before a routing row is created for it. Parsing
        // was the only check, so an authorised caller could sign off any integer; with no routing
        // row to update, the branch below PERSISTS one pointing at nothing. HRMResultsData then
        // walks every routing row and calls hrmDocumentDao.findById(id).get(0) with no emptiness
        // guard, so one forged sign-off throws IndexOutOfBoundsException on every subsequent
        // inbox load for that provider — a persistent denial of the inbox, not a bad row.
        Integer reportId;
        try {
            reportId = Integer.valueOf(reportIds[0].trim());
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return writeResult(false, FAILURE_MESSAGE);
        }
        // Guarded, because this route's whole contract is that it answers JSON. An unguarded DAO
        // call here escapes the action, Struts resolves the document package's global `error`
        // result, and /Modify replies with an HTML error page — which hrmModify() requested as
        // dataType "json", so jQuery's parse throws into a catch the clinician never sees. That
        // is the silent failure this PR exists to remove, reintroduced one line above the try
        // that was meant to prevent it.
        //
        // intValue() deliberately: AbstractDaoImpl declares both find(Object) and find(int), and
        // an Integer would silently bind to the Object overload rather than the primary-key
        // lookup the rest of this class uses.
        boolean reportExists;
        try {
            reportExists = hrmDocumentDao.find(reportId.intValue()) != null;
        } catch (Exception e) {
            // A lookup failure is not a malformed request: the caller cannot correct it, so it
            // is a 500 rather than the 400 an unknown id gets.
            MiscUtils.getLogger().error("Tried to resolve HRM document before sign-off but failed.", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return writeResult(false, FAILURE_MESSAGE, 0);
        }
        if (!reportExists) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return writeResult(false, FAILURE_MESSAGE);
        }

        boolean success = false;
        int clearedCount = 0;
        try {
            int signedOff = signedOffValue;
            Date signedOffAt = new Date();

            // EVERY matching row, not the last one. HRMDocumentToProvider has no unique constraint
            // on (hrmDocumentId, providerNo) — only PRIMARY KEY(id) and two non-unique indexes —
            // and findByHrmDocumentIdAndProviderNo returns results.get(size - 1). Signing off just
            // that row left any other signedOff=0 row for the same pair behind, so the server kept
            // listing the report while the viewer had already hidden or closed it. That is the
            // "sign-off does nothing" the tester reported, and no amount of client-side
            // notification fixes it. HRMReportParser already reads these rows as a list.
            List<HRMDocumentToProvider> providerMappings =
                    hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNoList(reportId, providerNo);
            if (providerMappings == null || providerMappings.isEmpty()) {
                //check for unclaimed records, if those exist..update them
                providerMappings = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNoList(reportId, "-1");
                if (providerMappings != null) {
                    for (HRMDocumentToProvider unclaimedMapping : providerMappings) {
                        unclaimedMapping.setProviderNo(providerNo);
                    }
                }
            }

            if (providerMappings == null || providerMappings.isEmpty()) {
                // No row at all: sign-off creates one, and a row that never sat in anybody's inbox
                // is not a row that left it, so it is deliberately not counted (a standalone
                // report, or one viewed through another provider's inbox).
                HRMDocumentToProvider hrmDocumentToProvider = new HRMDocumentToProvider();
                hrmDocumentToProvider.setHrmDocumentId(reportId);
                hrmDocumentToProvider.setProviderNo(providerNo);
                hrmDocumentToProvider.setSignedOff(signedOff);
                hrmDocumentToProvider.setSignedOffTimestamp(signedOffAt);
                hrmDocumentToProviderDao.persist(hrmDocumentToProvider);
            } else {
                for (HRMDocumentToProvider providerMapping : providerMappings) {
                    // Read the previous state before writing. The inbox badge counts routing rows
                    // whose signedOff is EXACTLY 0 (HRMDocumentToProviderDao: "signedOff=0"), so
                    // only such a row can leave it. A row whose signedOff is NULL is not counted:
                    // the column is nullable (int(11) DEFAULT NULL) and "signedOff = 0" does not
                    // match NULL in SQL, so legacy rows were never in the badge either. Treating
                    // NULL as unsigned would walk the badge below the server's figure.
                    boolean previouslyUnsigned = Integer.valueOf(0).equals(providerMapping.getSignedOff());

                    providerMapping.setSignedOff(signedOff);
                    providerMapping.setSignedOffTimestamp(signedOffAt);
                    hrmDocumentToProviderDao.merge(providerMapping);

                    if (signedOff == 1 && previouslyUnsigned) {
                        clearedCount++;
                    }
                }
            }
            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to set signed off status on document but failed.", e);
            success = false;
            // Nothing is reported as cleared when the write did not land, so the viewer's
            // notification and the database can no longer disagree.
            clearedCount = 0;
        }

        return writeResult(success, success ? SUCCESS_MESSAGE : FAILURE_MESSAGE, clearedCount);
    }

    /**
     * Routes one HRM report to a provider.
     *
     * <p>Reads {@code reportId} and {@code providerNo}. Also applies that provider's
     * {@code IncomingLabRules} forwarding rules for HRM, adding a routing row for each forward
     * target that does not already have one, and drops the unclaimed placeholder row
     * ({@code providerNo} of {@code -1}) because a manual match supersedes it.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String assignProvider() throws IOException {
        boolean success = false;
        //Gets the Dao for incoming lab rules
        IncomingLabRulesDao incomingLabRulesDao = SpringUtils.getBean(IncomingLabRulesDao.class);
        String providerNo = request.getParameter("providerNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }


        // The same guard signOff carries, for the same reason: this handler also CREATES routing
        // rows — its own and one per forwarding rule — and HRMDocumentToProvider has no foreign
        // key to hold it to a real document. A row pointing at nothing makes HRMResultsData's
        // unguarded hrmDocumentDao.findById(id).get(0) throw on every later inbox load for each
        // provider routed, so one call denies several inboxes at once. I fixed this in signOff
        // and left the other door open.
        Integer hrmDocumentId;
        try {
            hrmDocumentId = Integer.valueOf(request.getParameter("reportId").trim());
        } catch (NumberFormatException | NullPointerException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return writeResult(false, FAILURE_MESSAGE);
        }
        boolean reportExists;
        try {
            // intValue(): AbstractDaoImpl declares both find(Object) and find(int), and an
            // Integer binds to the Object overload rather than the primary-key lookup.
            reportExists = hrmDocumentDao.find(hrmDocumentId.intValue()) != null;
        } catch (Exception e) {
            // A lookup failure is not a malformed request, so it is a 500 — and it stays inside
            // the JSON contract rather than escaping to the global HTML error result.
            MiscUtils.getLogger().error("Tried to resolve HRM document before assigning a provider but failed.", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return writeResult(false, FAILURE_MESSAGE);
        }
        if (!reportExists) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return writeResult(false, FAILURE_MESSAGE);
        }

        try {
            // Only if this provider is not already routed this report. merge() on an entity with
            // a null id INSERTS, and there is no unique constraint on (hrmDocumentId, providerNo),
            // so assigning the same provider twice used to add a second unsigned routing row. The
            // report then stayed in that provider's inbox after sign-off cleared one of them. The
            // forwarding branch just below, and HRMReportParser, both already check first; this
            // path was the one that did not.
            List<HRMDocumentToProvider> existingMappings =
                    hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNoList(hrmDocumentId, providerNo);
            if (existingMappings == null || existingMappings.isEmpty()) {
                HRMDocumentToProvider providerMapping = new HRMDocumentToProvider();
                providerMapping.setHrmDocumentId(hrmDocumentId);
                providerMapping.setProviderNo(providerNo);
                providerMapping.setSignedOff(0);

                hrmDocumentToProviderDao.merge(providerMapping);
            }

            //Gets the list of IncomingLabRules pertaining to the current providers
            List<IncomingLabRules> incomingLabRules = incomingLabRulesDao.findCurrentByProviderNo(providerNo);
            //If the list is not null
            if (incomingLabRules != null) {
                //For each labRule in the list
                for (IncomingLabRules labRule : incomingLabRules) {
                    if (labRule.getForwardTypeStrings().contains("HRM")) {
                        //Creates a string of the providers number that the lab will be forwarded to
                        String forwardProviderNumber = labRule.getFrwdProviderNo();
                        //Checks to see if this providers is already linked to this lab
                        HRMDocumentToProvider hrmDocumentToProvider = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(hrmDocumentId, forwardProviderNumber);
                        //If a record was not found
                        if (hrmDocumentToProvider == null) {
                            //Puts the information into the HRMDocumentToProvider object
                            hrmDocumentToProvider = new HRMDocumentToProvider();
                            hrmDocumentToProvider.setHrmDocumentId(hrmDocumentId);
                            hrmDocumentToProvider.setProviderNo(forwardProviderNumber);
                            hrmDocumentToProvider.setSignedOff(0);
                            //Stores it in the table
                            hrmDocumentToProviderDao.persist(hrmDocumentToProvider);
                        }
                    }
                }
            }


            //we want to remove any unmatched entries when we do a manual match like this. -1 means unclaimed in this table.
            HRMDocumentToProvider existingUnmatched = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(hrmDocumentId, "-1");
            if (existingUnmatched != null) {
                hrmDocumentToProviderDao.remove(existingUnmatched.getId());
            }

            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to assign HRM document to providers but failed.", e);
            success = false;
        }

        return writeResult(success);
    }

    /**
     * Unlinks one HRM report from every patient it is currently attached to.
     *
     * <p>Reads {@code reportId}. Removing the link is what re-opens the report for matching, so
     * the viewer re-enables the patient action buttons only on a successful reply.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String removeDemographic() throws IOException {
        boolean success = false;
        String hrmDocumentId = request.getParameter("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            List<HRMDocumentToDemographic> currentMappingList = hrmDocumentToDemographicDao.findByHrmDocumentId(Integer.parseInt(hrmDocumentId));

            if (currentMappingList != null) {
                for (HRMDocumentToDemographic currentMapping : currentMappingList) {
                    hrmDocumentToDemographicDao.remove(currentMapping.getId());
                }
            }

            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to remove HRM document from demographic but failed.", e);
            success = false;
        }

        return writeResult(success);

    }

    /**
     * Links one HRM report to a patient, replacing any existing link.
     *
     * <p>Reads {@code reportId} and {@code demographicNo}. Existing links are cleared first so a
     * report is never attached to two charts, and a failure to clear them fails the whole
     * operation: writing the new link anyway would leave the report on both patients' charts
     * while reporting success.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String assignDemographic() throws IOException {
        boolean success = false;
        String hrmDocumentId = request.getParameter("reportId");
        String demographicNo = request.getParameter("demographicNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            // No inner catch. Clearing the existing links is not a best-effort preliminary: if it
            // fails and the new link is written anyway, the report is attached to the old chart
            // AND the new one, and the JSON still says "Success". An HRM report showing on two
            // patients' charts is a worse outcome than a failed match the clinician can retry,
            // and the viewer only re-enables the patient buttons on success. Same class as the
            // swallowed catch removed from updateCategory.
            List<HRMDocumentToDemographic> currentMappingList = hrmDocumentToDemographicDao.findByHrmDocumentId(Integer.parseInt(hrmDocumentId));

            if (currentMappingList != null) {
                for (HRMDocumentToDemographic currentMapping : currentMappingList) {
                    hrmDocumentToDemographicDao.remove(currentMapping);
                }
            }

            HRMDocumentToDemographic demographicMapping = new HRMDocumentToDemographic();

            demographicMapping.setHrmDocumentId(Integer.valueOf(hrmDocumentId));
            demographicMapping.setDemographicNo(Integer.valueOf(demographicNo));
            demographicMapping.setTimeAssigned(new Date());

            hrmDocumentToDemographicDao.merge(demographicMapping);

            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to assign HRM document to demographic but failed.", e);
            success = false;
        }

        return writeResult(success);
    }

    /**
     * Makes one of a report's sub-classes the active one.
     *
     * <p>Reads {@code reportId} and {@code subClassId}. The target is resolved and checked to
     * belong to this report first; only then are the report's existing sub-classes deactivated and
     * the new one activated, so a bad id cannot leave the report with none active. The viewer
     * reloads the page afterwards, so this must not report success unless the switch persisted.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String makeActiveSubClass() throws IOException {
        boolean success = false;
        String hrmDocumentId = request.getParameter("reportId");
        String subClassId = request.getParameter("subClassId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            Integer documentId = Integer.parseInt(hrmDocumentId);

            // Resolve and validate the target BEFORE clearing the report's existing rows. Doing it
            // the other way round meant a stale id left the report with no active sub-class at all
            // while still reporting success, and an id belonging to a different report would have
            // been activated against this one.
            HRMDocumentSubClass newActiveSubClass = hrmDocumentSubClassDao.find(Integer.parseInt(subClassId));
            if (newActiveSubClass != null && documentId.equals(newActiveSubClass.getHrmDocumentId())) {
                hrmDocumentSubClassDao.setAllSubClassesForDocumentAsInactive(documentId);
                newActiveSubClass.setActive(true);
                hrmDocumentSubClassDao.merge(newActiveSubClass);
                success = true;
            } else {
                MiscUtils.getLogger().warn("Refused to activate an HRM sub-class that does not belong to the requested report");
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to change active subclass but failed.", e);
            success = false;
        }


        return writeResult(success);
    }

    /**
     * Removes one provider's routing row from an HRM report.
     *
     * <p>Reads {@code providerMappingId} — the routing row's own id, not a provider number.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String removeProvider() throws IOException {
        boolean success = false;
        String providerMappingId = request.getParameter("providerMappingId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            hrmDocumentToProviderDao.remove(Integer.parseInt(providerMappingId));

            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to remove providers from HRM document but failed.", e);
            success = false;
        }

        return writeResult(success);
    }

    /**
     * Files a comment against one HRM report.
     *
     * <p>Reads {@code reportId} and {@code comment}, and stamps the comment with the logged-in
     * provider and the current time. The comment text is stored raw and encoded at render time by
     * the viewer's {@code <carlos:encode>} output, never here.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String addComment() throws IOException {
        boolean success = false;
        String documentId = request.getParameter("reportId");
        String commentString = request.getParameter("comment");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            HRMDocumentComment comment = new HRMDocumentComment();

            comment.setHrmDocumentId(Integer.parseInt(documentId));
            comment.setComment(commentString);
            comment.setCommentTime(new Date());
            comment.setProviderNo(loggedInInfo.getLoggedInProviderNo());

            hrmDocumentCommentDao.merge(comment);
            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't add a comment for HRM document", e);
            success = false;
        }

        return writeResult(success);
    }

    /**
     * Deletes one comment from an HRM report.
     *
     * <p>Reads {@code commentId}.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String deleteComment() throws IOException {
        boolean success = false;
        String commentId = request.getParameter("commentId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            hrmDocumentCommentDao.deleteComment(Integer.parseInt(commentId));
            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't delete comment on HRM document", e);
            success = false;
        }

        return writeResult(success);
    }

    /**
     * Sets the free-text description shown for one HRM report.
     *
     * <p>Reads {@code reportId} and {@code description}. A report that cannot be found reports
     * failure rather than success, so the viewer does not tell the clinician a description was
     * saved when none was.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String setDescription() throws IOException {
        boolean success = false;
        String documentId = request.getParameter("reportId");
        String descriptionString = request.getParameter("description");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            boolean updated = false;
            HRMDocument document = hrmDocumentDao.find(Integer.parseInt(documentId));
            if (document != null) {
                document.setDescription(descriptionString);
                hrmDocumentDao.merge(document);
                updated = true;
            }
            success = updated;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't set description for HRM document", e);
            success = false;
        }

        return writeResult(success);
    }

    /**
     * Files one HRM report under a category.
     *
     * <p>Reads {@code reportId} and {@code categoryId}. Success means the document was found and
     * merged — an unparseable id, a missing report or a DAO failure all report failure. The
     * original code swallowed every one of those in a nested catch and then answered "Success",
     * so the viewer relabelled the category for a change the database never took.</p>
     *
     * @return {@link #NONE}; the JSON status body is written directly
     * @throws IOException if the response body cannot be written
     */
    public String updateCategory() throws IOException {
        boolean success = false;
        String hrmDocumentId = request.getParameter("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            Integer categoryId = Integer.valueOf(request.getParameter("categoryId"));
            HRMDocument document = hrmDocumentDao.find(Integer.parseInt(hrmDocumentId));
            if (document != null) {
                document.setHrmCategoryId(categoryId);
                hrmDocumentDao.merge(document);
                success = true;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to assign HRM document to category but failed.", e);
            success = false;
        }

        return writeResult(success);
    }
}
