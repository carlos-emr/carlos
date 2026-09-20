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
     * @return {@link #NONE}, because the response body is already written
     */
    private String writeResult(boolean success, String message) throws IOException {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("success", success);
        body.put("message", message);

        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body.toString());
        response.getWriter().flush();
        return NONE;
    }

    /** Convenience wrapper for handlers that report the standard success/failure wording. */
    private String writeResult(boolean success) throws IOException {
        return writeResult(success, success ? SUCCESS_MESSAGE : FAILURE_MESSAGE);
    }

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

    public String signOff() throws IOException {
        String[] reportIds = request.getParameterValues("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        // A sign-off with no report named is a malformed request, not an empty success: reporting
        // "Success" would tell the viewer to clear the report out of the inbox having filed nothing.
        if (reportIds == null || reportIds.length == 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return writeResult(false, FAILURE_MESSAGE);
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Bulk sign-off is all-or-nothing as far as the caller is concerned: one failed report
        // must not be masked by a later success, or the inbox drops a report still awaiting review.
        boolean success = true;
        for (int i = 0; i < reportIds.length; i++) {
            try {
                Integer reportId = Integer.parseInt(reportIds[i]);
                String signedOff = request.getParameterValues("signedOff")[i];
                HRMDocumentToProvider providerMapping = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(reportId, providerNo);
                if (providerMapping == null) {
                    //check for unclaimed record, if that exists..update that one
                    providerMapping = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(reportId, "-1");
                    if (providerMapping != null) {
                        providerMapping.setProviderNo(providerNo);
                    }
                }

                if (providerMapping != null) {
                    providerMapping.setSignedOff(Integer.parseInt(signedOff));
                    providerMapping.setSignedOffTimestamp(new Date());
                    hrmDocumentToProviderDao.merge(providerMapping);
                } else {
                    HRMDocumentToProvider hrmDocumentToProvider = new HRMDocumentToProvider();
                    hrmDocumentToProvider.setHrmDocumentId(reportId);
                    hrmDocumentToProvider.setProviderNo(providerNo);
                    hrmDocumentToProvider.setSignedOff(Integer.parseInt(signedOff));
                    hrmDocumentToProvider.setSignedOffTimestamp(new Date());
                    hrmDocumentToProviderDao.persist(hrmDocumentToProvider);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Tried to set signed off status on document but failed.", e);
                success = false;
            }
        }


        return writeResult(success);
    }

    public String assignProvider() throws IOException {
        boolean success = false;
        //Gets the Dao for incoming lab rules
        IncomingLabRulesDao incomingLabRulesDao = SpringUtils.getBean(IncomingLabRulesDao.class);
        String providerNo = request.getParameter("providerNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }


        try {
            HRMDocumentToProvider providerMapping = new HRMDocumentToProvider();
            Integer hrmDocumentId = Integer.valueOf(request.getParameter("reportId"));
            providerMapping.setHrmDocumentId(hrmDocumentId);
            providerMapping.setProviderNo(providerNo);
            providerMapping.setSignedOff(0);

            hrmDocumentToProviderDao.merge(providerMapping);

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

    public String assignDemographic() throws IOException {
        boolean success = false;
        String hrmDocumentId = request.getParameter("reportId");
        String demographicNo = request.getParameter("demographicNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            try {
                List<HRMDocumentToDemographic> currentMappingList = hrmDocumentToDemographicDao.findByHrmDocumentId(Integer.parseInt(hrmDocumentId));

                if (currentMappingList != null) {
                    for (HRMDocumentToDemographic currentMapping : currentMappingList) {
                        hrmDocumentToDemographicDao.remove(currentMapping);
                    }
                }
            } catch (Exception e) {
                // Do nothing
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

    public String makeActiveSubClass() throws IOException {
        boolean success = false;
        String hrmDocumentId = request.getParameter("reportId");
        String subClassId = request.getParameter("subClassId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            hrmDocumentSubClassDao.setAllSubClassesForDocumentAsInactive(Integer.parseInt(hrmDocumentId));

            HRMDocumentSubClass newActiveSubClass = hrmDocumentSubClassDao.find(Integer.parseInt(subClassId));
            if (newActiveSubClass != null) {
                newActiveSubClass.setActive(true);
                hrmDocumentSubClassDao.merge(newActiveSubClass);
            }

            success = true;

        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to change active subclass but failed.", e);
            success = false;
        }


        return writeResult(success);
    }

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

    public String updateCategory() throws IOException {
        boolean success = false;
        String hrmDocumentId = request.getParameter("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            try {
                Integer categoryId = Integer.valueOf(request.getParameter("categoryId"));
                HRMDocument document = hrmDocumentDao.find(Integer.parseInt(hrmDocumentId));
                if (document != null) {
                    document.setHrmCategoryId(categoryId);
                    hrmDocumentDao.merge(document);
                }
            } catch (Exception e) {
                // Do nothing
            }
            success = true;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to assign HRM document to category but failed.", e);
            success = false;
        }

        return writeResult(success);
    }
}
