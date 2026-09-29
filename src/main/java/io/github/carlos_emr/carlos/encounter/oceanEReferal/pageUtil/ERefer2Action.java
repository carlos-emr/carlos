package io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDao;
import io.github.carlos_emr.carlos.commn.model.EReferAttachment;
import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action for managing Ocean eReferral consultation attachments.
 * <p>
 * This action handles the attachment and editing of medical documents (including clinical documents,
 * lab results, eforms, and hospital report manager records) to Ocean eReferral consultation requests.
 * Ocean eReferral is an integrated healthcare referral management system used in Ontario for
 * electronic specialist referrals. This action specifically manages the association of internal
 * OpenO EMR documents with outgoing referral requests.
 * </p>
 * <p>
 * The action supports two primary operations via method-based routing:
 * </p>
 * <ul>
 *   <li><b>attachOceanEReferralConsult</b> - Creates new eReferral attachment records linking
 *       internal documents to a demographic (patient) for referral purposes</li>
 *   <li><b>editOceanEReferralConsult</b> - Updates existing consultation requests by attaching
 *       additional documents organized by document type</li>
 * </ul>
 * <p>
 * Document attachments are categorized by type using single-character prefixes:
 * </p>
 * <ul>
 *   <li><b>D</b> - Clinical documents (Document Manager)</li>
 *   <li><b>L</b> - Laboratory results</li>
 *   <li><b>E</b> - Electronic forms (eForms)</li>
 *   <li><b>H</b> - Hospital Report Manager records</li>
 * </ul>
 * <p>
 * <b>Security boundary (issue #3867).</b> Attachment ids and {@code requestId} arrive from the
 * browser, and everything downstream (the Ocean attachment feed in
 * {@code ConsultationManager#getEReferAttachments}, consultation printing and faxing) resolves an
 * attachment by id alone. The server therefore verifies, before anything is stored, that every
 * D/L/E/H id and the consultation request belong to the {@code demographicNo} in the request, via
 * {@link AttachmentOwnershipService}. Any foreign, unknown or malformed id rejects the whole
 * request, and the response does not say which id failed. Before any of that, the caller must hold
 * patient-scoped {@code _con} write and be allowed to access the patient's record. The endpoint is
 * POST-only: it mutates attachment state, and CSRFGuard only validates unsafe methods.
 * </p>
 * <p>
 * This is a 2Action implementation following OpenO EMR's Struts2 migration pattern,
 * coexisting with legacy Struts 1.x actions during the framework transition.
 * </p>
 *
 * @see EReferAttachment
 * @see EReferAttachmentData
 * @see DocumentAttachmentManager
 * @see AttachmentOwnershipService
 * @see EReferAttachmentDao
 * @since 2026-01-24
 */
public class ERefer2Action extends ActionSupport {
    private static final Logger logger = MiscUtils.getLogger();


    /** One attachment token: a single type letter followed by a numeric id, e.g. {@code D123}. */
    private static final Pattern ATTACHMENT_TOKEN = Pattern.compile("([A-Za-z])(\\d{1,10})");
    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{1,10}");
    private static final DocumentType[] OCEAN_ATTACHMENT_TYPES =
            {DocumentType.DOC, DocumentType.LAB, DocumentType.EFORM, DocumentType.HRM};

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    // Actions are prototype-scoped and never actually serialized, but transient satisfies the contract.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient DocumentAttachmentManager documentAttachmentManager;
    private final transient EReferAttachmentDao eReferAttachmentDao;
    private final transient AttachmentOwnershipService attachmentOwnershipService;

    private transient HttpServletRequest request;
    private transient HttpServletResponse response;

    public ERefer2Action(SecurityInfoManager securityInfoManager,
                         DocumentAttachmentManager documentAttachmentManager,
                         EReferAttachmentDao eReferAttachmentDao,
                         AttachmentOwnershipService attachmentOwnershipService) {
        this.securityInfoManager = securityInfoManager;
        this.documentAttachmentManager = documentAttachmentManager;
        this.eReferAttachmentDao = eReferAttachmentDao;
        this.attachmentOwnershipService = attachmentOwnershipService;
    }

    /**
     * Main execution method for this Struts2 action.
     * <p>
     * Routes incoming requests to the appropriate handler method based on the "method" request parameter.
     * Supports method-based routing pattern common in OpenO EMR's 2Action implementations.
     * </p>
     * <p>
     * Supported method parameter values:
     * </p>
     * <ul>
     *   <li><code>attachOceanEReferralConsult</code> - Routes to {@link #attachOceanEReferralConsult()}</li>
     *   <li><code>editOceanEReferralConsult</code> - Routes to {@link #editOceanEReferralConsult()}</li>
     * </ul>
     * <p>
     * Non-POST requests are rejected with 405 before any authorization check or side effect. The
     * action writes its own response and always returns {@link ActionSupport#NONE}.
     * </p>
     *
     * @return {@link ActionSupport#NONE}
     * @throws IOException if the 405 response cannot be sent
     * @throws SecurityException if the user lacks {@code _con} write access
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws IOException {
        request = ServletActionContext.getRequest();
        response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String method = request.getParameter("method");

        if (method != null) {
            if (method.equalsIgnoreCase("attachOceanEReferralConsult"))
                attachOceanEReferralConsult();
            else if (method.equalsIgnoreCase("editOceanEReferralConsult"))
                editOceanEReferralConsult();
        }

        return NONE;
    }

    /**
     * Creates new eReferral attachment records for Ocean consultation requests.
     * <p>
     * This method processes document attachments selected from the consultation request window's
     * attachment GUI and queues them, for a specific patient (demographic), for inclusion in an
     * outgoing Ocean eReferral.
     * </p>
     * <p>
     * The method expects a pipe-delimited (|) string of document identifiers in the format
     * <code>{type}{id}</code>, where <b>type</b> is D/L/E/H (F tokens are ignored because Ocean
     * cannot receive forms) and <b>id</b> is the numeric identifier. Example:
     * <code>D123|L456|E789</code>.
     * </p>
     * <p>
     * <b>Request Parameters:</b> {@code demographicNo} and {@code documents} (both required; if
     * either is missing the method returns without doing anything).
     * </p>
     * <p>
     * <b>Response:</b> the new EReferAttachment id as text/plain; 400 for a malformed request;
     * 403 when any attachment does not belong to the patient (nothing is stored).
     * </p>
     */
    public void attachOceanEReferralConsult() {
        String demographicNoParam = request.getParameter("demographicNo");
        String documents = request.getParameter("documents");
        if (isBlank(documents) || isBlank(demographicNoParam)) {
            return;
        }

        Integer demographicNo = parseId(demographicNoParam);
        Map<DocumentType, Set<Integer>> attachmentsByType = parseAttachments(documents);
        if (demographicNo == null || attachmentsByType == null) {
            reject(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        requirePatientConsultWrite(demographicNo);
        if (attachmentsByType.isEmpty()) {
            // Only form tokens were selected; Ocean cannot receive those, so there is nothing to queue.
            return;
        }
        if (!attachmentOwnershipService.allBelongToDemographic(attachmentsByType, demographicNo)) {
            logger.warn("Rejected Ocean eReferral attachment request: attachments not owned by the target patient");
            reject(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        EReferAttachment eReferAttachment = new EReferAttachment(demographicNo);
        List<EReferAttachmentData> attachments = new ArrayList<>();
        for (Map.Entry<DocumentType, Set<Integer>> entry : attachmentsByType.entrySet()) {
            for (Integer id : entry.getValue()) {
                attachments.add(new EReferAttachmentData(eReferAttachment, id, entry.getKey().getType()));
            }
        }
        eReferAttachment.setAttachments(attachments);
        eReferAttachmentDao.persist(eReferAttachment);

        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(eReferAttachment.getId().toString()); // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss -- text/plain response writing numeric database ID
        } catch (IOException e) {
            logger.error("Failed to write the eReferAttachment ID to the response", e);
        }
    }

    /**
     * Updates an existing Ocean eReferral consultation request by attaching additional documents.
     * <p>
     * This method processes document attachments from the consultation request window's attachment GUI
     * and associates them with an existing consultation request. Documents are organized by type
     * (D/L/E/H) and attached using the {@link DocumentAttachmentManager} with Ocean synchronization
     * enabled, so new attachments are also queued for OceanMD.
     * </p>
     * <p>
     * <b>Request Parameters:</b> {@code demographicNo}, {@code requestId} and {@code documents}
     * (all required; if any is missing the method returns without doing anything).
     * </p>
     * <p>
     * The consultation request must belong to {@code demographicNo} and every newly added
     * attachment must belong to that patient; otherwise the request is rejected with 403 before
     * any attach or detach. Already-attached ids that no longer verify are detached, not sent.
     * A malformed request is rejected with 400.
     * </p>
     * <p>
     * <b>Session Requirements:</b> Requires valid {@link LoggedInInfo} in HTTP session to identify
     * the provider performing the attachment operation.
     * </p>
     */
    public void editOceanEReferralConsult() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String demographicNoParam = request.getParameter("demographicNo");
        String requestIdParam = request.getParameter("requestId");
        String documents = request.getParameter("documents");
        if (isBlank(documents) || isBlank(demographicNoParam) || isBlank(requestIdParam)) {
            return;
        }

        Integer demographicNo = parseId(demographicNoParam);
        Integer requestId = parseId(requestIdParam);
        Map<DocumentType, Set<Integer>> attachmentsByType = parseAttachments(documents);
        if (demographicNo == null || requestId == null || attachmentsByType == null) {
            reject(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        requirePatientConsultWrite(demographicNo);

        // The consultation must be this patient's, or the attach/detach below would rewrite another
        // patient's consultation and queue records against the wrong referral.
        if (!attachmentOwnershipService.consultationRequestBelongsToDemographic(requestId, demographicNo)) {
            logger.warn("Rejected Ocean eReferral edit: consultation request does not belong to the target patient");
            reject(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Map<DocumentType, String[]> idStringsByType = new EnumMap<>(DocumentType.class);
        for (DocumentType type : OCEAN_ATTACHMENT_TYPES) {
            idStringsByType.put(type, toIdStrings(attachmentsByType.getOrDefault(type, Collections.emptySet())));
        }
        // Verify every type up front: the attachToConsult calls below are separate writes, so a late
        // rejection would leave the consultation partially updated. Newly added ids must be this
        // patient's; ids already attached that no longer verify are detached by attachToConsult
        // rather than failing the edit (a legacy row the user cannot remove from the form).
        try {
            documentAttachmentManager.verifyConsultAttachments(loggedInInfo, requestId, demographicNo, idStringsByType);
        } catch (SecurityException e) {
            logger.warn("Rejected Ocean eReferral edit: attachments not owned by the target patient");
            reject(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        for (DocumentType type : OCEAN_ATTACHMENT_TYPES) {
            documentAttachmentManager.attachToConsult(loggedInInfo, type, idStringsByType.get(type),
                    providerNo, requestId, demographicNo, Boolean.TRUE);
        }
    }

    /**
     * Parses a pipe-delimited attachment list into ids grouped by type.
     *
     * <p>{@code F} (encounter form) tokens are skipped: Ocean cannot receive forms and the consult
     * page already asks the user before sending without them. Empty tokens are skipped.</p>
     *
     * @param documents raw {@code documents} parameter, e.g. {@code D12|L7|F3|}
     * @return ids grouped by DOC/LAB/EFORM/HRM (possibly empty), or {@code null} if any token,
     *         including a skipped F token, is malformed, overflows, or has an unknown type
     */
    static Map<DocumentType, Set<Integer>> parseAttachments(String documents) {
        Map<DocumentType, Set<Integer>> attachmentsByType = new EnumMap<>(DocumentType.class);
        if (documents == null) {
            return attachmentsByType;
        }
        for (String token : documents.split("\\|")) {
            if (token.isEmpty()) {
                continue;
            }
            Matcher matcher = ATTACHMENT_TOKEN.matcher(token);
            if (!matcher.matches()) {
                return null;
            }
            DocumentType type = toDocumentType(Character.toUpperCase(matcher.group(1).charAt(0)));
            if (type == null) {
                return null;
            }
            // Parse before the FORM skip so a form token gets the same strictness (400 on overflow).
            Integer id = parseId(matcher.group(2));
            if (id == null) {
                return null;
            }
            if (type == DocumentType.FORM) {
                continue;
            }
            attachmentsByType.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(id);
        }
        return attachmentsByType;
    }

    private static DocumentType toDocumentType(char typeCode) {
        for (DocumentType type : DocumentType.values()) {
            if (type.getType().charAt(0) == typeCode) {
                return type;
            }
        }
        return null;
    }

    /** Parses a non-negative database id; {@code null} for anything else, including overflow. */
    private static Integer parseId(String raw) {
        if (raw == null || !NUMERIC_ID.matcher(raw).matches()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    private static String[] toIdStrings(Set<Integer> ids) {
        return ids.stream().map(String::valueOf).toArray(String[]::new);
    }

    /**
     * Patient-scoped checks on top of the role-level check in {@link #execute()}, run before any
     * ownership lookup or write.
     *
     * <p>A patient-scoped {@code _con} write grant is not the circle-of-care check: a provider can
     * hold {@code _con$<demographicNo>} while being blocked from that patient's chart
     * ({@code _demographic$}/{@code _eChart$} "o"). Queued attachments are rendered and sent to
     * Ocean by {@code ConsultationManager#getEReferAttachments}, which requires chart access, so
     * the queueing side requires it too, as the consultation fax route does before sending PHI.</p>
     */
    private void requirePatientConsultWrite(Integer demographicNo) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo.intValue())
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("missing required sec object (_con)");
        }
    }

    /**
     * Ends the request with a bare status. The only caller is conreq.js, which reads the status
     * alone; sending no body means nothing request- or record-derived can be reflected here, and
     * the response does not say which id or which check failed.
     */
    private void reject(int status) {
        response.setStatus(status);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength(0);
    }
}
