package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionContext;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionState;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeView;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeViewState;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.IssuedPreview;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.PreparedEmailComposeView;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.web.util.WebUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Struts2 action for composing and preparing email messages with patient-related attachments.
 *
 * This action handles the preparation of email composition screens for sending electronic forms (eForms),
 * documents, laboratory results, and other patient health information via email. It manages session-based
 * email composition data, prepares attachments with optional PDF encryption, retrieves patient email consent
 * status, and validates recipient information before presenting the compose interface.
 *
 * Key Features:
 * <ul>
 *   <li>Prepares email composition interface for eForms and patient documents</li>
 *   <li>Manages session-based email composition state (survives redirects)</li>
 *   <li>Handles multiple attachment types: eForms, eDocuments, lab results, forms, HRM documents</li>
 *   <li>Generates and manages PDF password encryption for patient privacy</li>
 *   <li>Validates patient email consent status before sending</li>
 *   <li>Retrieves and validates recipient email addresses</li>
 *   <li>Sanitizes attachment filenames for security</li>
 *   <li>Validates numeric form ID (fid) parameters to prevent injection attacks</li>
 * </ul>
 *
 * Healthcare Context:
 * This action is part of OpenO EMR's secure patient communication system, ensuring that Protected Health
 * Information (PHI) is transmitted with appropriate encryption, consent verification, and audit logging.
 * It supports PIPEDA/HIPAA compliance by enforcing patient consent for email communications and providing
 * password-protected PDF attachments with server-generated random passphrases.
 *
 * Request lifecycle (#3632):
 * <ol>
 *   <li><b>Prepare.</b> The eForm save stages the compose fields in the HTTP session and redirects
 *       here. The first GET takes those fields out of the session in one step, generates the
 *       attachment PDFs, stores the one-time submission state with the staged values under an opaque
 *       view id, and redirects to {@code ?composeView=<id>}. It runs once per staged compose.</li>
 *   <li><b>View.</b> A GET with {@code composeView} renders the stored state. It changes no session
 *       attribute, generates no file and consumes nothing, so a refresh or a repeated request shows
 *       the same compose screen. Consent, recipients and sender accounts are looked up again. The
 *       preview capabilities issued during preparation are reused, and one is re-issued once less
 *       than half of its two minutes remains. Once a send consumes the submission token the
 *       view reports the window as expired, so going back cannot resend.</li>
 * </ol>
 *
 * Security Considerations:
 * <ul>
 *   <li>Validates fid parameter to ensure numeric format (prevents injection)</li>
 *   <li>Uses log-safe sanitization for invalid fid values in logs</li>
 *   <li>Generates random PDF passphrases without using patient demographic information</li>
 *   <li>Sanitizes attachment filenames through EmailComposeManager</li>
 *   <li>Session cleanup prevents information leakage across requests</li>
 *   <li>A view id only resolves within the session that prepared it</li>
 * </ul>
 *
 * @see io.github.carlos_emr.carlos.managers.EmailComposeManager
 * @see io.github.carlos_emr.carlos.managers.DemographicManager
 * @see io.github.carlos_emr.carlos.commn.model.EmailAttachment
 * @see io.github.carlos_emr.carlos.commn.model.EmailConfig
 * @see io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType
 * @since 2026-01-24
 */
public class EmailCompose2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);
    private transient EmailPdfPasswordService emailPdfPasswordService = SpringUtils.getBean(EmailPdfPasswordService.class);
    private transient EmailComposeSubmissionStateService emailComposeSubmissionStateService =
            SpringUtils.getBean(EmailComposeSubmissionStateService.class);
    private PdfPreviewCapabilityService pdfPreviewCapabilityService =
            SpringUtils.getBean(PdfPreviewCapabilityService.class);
    private final transient EmailFooterService emailFooterService;

    public EmailCompose2Action() {
        this(SpringUtils.getBean(EmailFooterService.class));
    }

    // Package-private so tests can supply the footer service.
    EmailCompose2Action(EmailFooterService emailFooterService) {
        this.emailFooterService = emailFooterService;
    }

    public static final String EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE =
            "This email compose window has expired or is no longer valid. "
                    + "Please reopen the email compose window and try again.";
    public static final String EMAIL_COMPOSE_STATE_UNAVAILABLE_MESSAGE =
            "This email compose window could not be prepared. "
                    + "Please close other open email compose windows and try again.";
    /** Query parameter carrying the opaque id of a prepared compose view. */
    public static final String EMAIL_COMPOSE_VIEW_PARAM = "composeView";
    /**
     * Result for a compose window that can no longer be used. It renders without any eForm or
     * patient context; the eForm error page throws when it has neither.
     */
    public static final String COMPOSE_EXPIRED_RESULT = "composeExpired";
    /**
     * Re-issue a stored preview capability once less than this remains, so a refreshed page's
     * preview and its "open in new tab" link keep working for at least this long.
     */
    private static final Duration PREVIEW_REISSUE_MARGIN = PdfPreviewCapabilityService.TTL.dividedBy(2);
    private static final String DEMOGRAPHIC_ID_KEY = "demographicId";

    private static final String[] EMAIL_SESSION_KEYS = {
        "attachEFormItSelf", "fdid", DEMOGRAPHIC_ID_KEY, "emailAttachmentList",
        "emailPDFPassword", "emailPDFPasswordClue",
        "attachedDocuments", "attachedLabs", "attachedForms",
        "attachedEForms", "attachedHRMDocuments",
        "deleteEFormAfterEmail", "isEmailEncrypted",
        "isEmailAttachmentEncrypted", "isEmailAutoSend",
        "openEFormAfterEmail", "senderEmail", "subjectEmail",
        "bodyEmail", "encryptedMessageEmail",
        "emailPatientChartOption", "footerEmail"
    };


    /**
     * Routes a compose GET: renders a prepared view when {@code composeView} is present, otherwise
     * prepares the compose staged in the session and redirects to its view.
     *
     * @return String "compose" for a rendered view, {@code NONE} after the prepare redirect,
     *         "composeExpired" when there is no usable compose state, or "eFormError" when the
     *         attachments or the compose state cannot be prepared
     * @see #prepareComposeEFormMailer()
     */
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)) {
            throw new SecurityException("missing required sec object (_email)");
        }

        String viewId = request.getParameter(EMAIL_COMPOSE_VIEW_PARAM);
        if (viewId != null) {
            return renderPreparedCompose(viewId);
        }
        // Method names are case-sensitive tokens, so this is an exact match.
        if ("HEAD".equals(request.getMethod())) {
            // Preparing takes the staged compose. A HEAD must not take it from the window that
            // is about to ask for it.
            response.setHeader("Allow", "GET, POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        return prepareComposeEFormMailer();
    }

    /**
     * Prepares the compose staged in the session once, then redirects to a view that can be
     * requested any number of times.
     *
     * This method runs the state-changing half of the compose workflow:
     * <ol>
     *   <li>Takes the staged compose values out of the HTTP session in one step, so a duplicate
     *       request finds nothing to prepare instead of generating the attachments twice</li>
     *   <li>Validates form ID (fid) parameter for numeric format to prevent injection</li>
     *   <li>Prepares all attachment types: eForms, eDocuments, labs, forms, HRM documents</li>
     *   <li>Sanitizes attachment filenames for security</li>
     *   <li>Generates a server-assigned random PDF passphrase and stores the one-time submission
     *       state together with the staged values under an opaque view id</li>
     *   <li>Redirects to {@code ?composeView=<id>}, which {@link #execute()} renders</li>
     * </ol>
     *
     * Session Attributes Consumed:
     * <ul>
     *   <li>attachEFormItSelf (Boolean) - whether to attach the eForm itself</li>
     *   <li>fdid (String) - form data ID for the eForm</li>
     *   <li>demographicId (String) - patient demographic identifier (required)</li>
     *   <li>attachedDocuments, attachedLabs, attachedForms, attachedEForms, attachedHRMDocuments
     *       (String[]) - ids of the items to attach</li>
     *   <li>senderEmail, subjectEmail, bodyEmail, encryptedMessageEmail, emailPatientChartOption,
     *       footerEmail (String) - staged compose fields</li>
     *   <li>isEmailEncrypted, isEmailAttachmentEncrypted, isEmailAutoSend, openEFormAfterEmail,
     *       deleteEFormAfterEmail (Boolean) - staged compose options</li>
     * </ul>
     *
     * Request Parameters:
     * <ul>
     *   <li>fid (String, optional) - form identifier, validated for numeric format</li>
     * </ul>
     *
     * Server-Side State Stored:
     * <ul>
     *   <li>tokenized prepared compose state and its view, keyed by session id</li>
     * </ul>
     *
     * Error Handling:
     * If compose session state is missing or invalid, the method returns "composeExpired" with a
     * generic expired-state message. If PDF generation fails for any attachment (eForm, document,
     * lab, form, HRM), it closes the working directory and returns "eFormError" with a generic,
     * PHI-safe attachment message. If the one-time compose state cannot be stored because the cache
     * is unavailable, it returns "eFormError" with a generic unavailable-state message. Without read
     * access to the patient it throws {@code SecurityException} before generating anything. No
     * error path clears the session again: the staged values were already taken, and anything
     * there now belongs to another compose.
     *
     * @return String {@code NONE} after redirecting to the prepared view, "composeExpired" if no
     *         compose is staged, or "eFormError" if attachment generation fails or the compose
     *         state cannot be stored
     * @see io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService#generatePassphrase()
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#prepareEFormAttachments(LoggedInInfo, String, String[])
     * @see io.github.carlos_emr.carlos.managers.EmailComposeManager#sanitizeAttachments(List)
     * @see #cleanupEmailSessionAttributes(HttpServletRequest)
     * @see #emailComposeError(HttpServletRequest, String)
     */
    public String prepareComposeEFormMailer() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        StagedCompose staged = takeStagedCompose(request.getSession());
        String demographicId = staged.demographicId();
        String fid = request.getParameter("fid");

        if (demographicId == null || demographicId.isBlank()) {
            return composeExpired();
        }

        // Validate fid is numeric if provided
        if (fid != null && !fid.matches("\\d+")) {
            if (logger.isWarnEnabled()) {
                String sanitizedFid = LogSafe.sanitize(fid);
                logger.warn("Invalid fid parameter received: {}", sanitizedFid); // NOSONAR javasecurity:S5145 (SonarCloud alert #26207) — sanitized with LogSafe
            }
            fid = null;
        }

        int demographicNo;
        try {
            demographicNo = Integer.parseInt(demographicId);
        } catch (NumberFormatException e) {
            return composeExpired();
        }
        // Before any of the patient's PDFs are generated.
        requireDemographicRead(loggedInInfo, demographicNo);

        EmailComposeWorkingDirectory workingDirectory;
        try {
            workingDirectory = emailComposeSubmissionStateService.createWorkingDirectory();
        } catch (IllegalStateException e) {
            logger.warn("Unable to create email compose working directory", e);
            return emailComposeError(request, EMAIL_COMPOSE_STATE_UNAVAILABLE_MESSAGE);
        }

        List<EmailAttachment> emailAttachmentList = new ArrayList<>();
        Map<String, IssuedPreview> previews = new ConcurrentHashMap<>();
        try {
            emailAttachmentList.addAll(emailComposeManager.prepareEFormAttachments(
                    loggedInInfo, staged.attachEFormItSelf() ? staged.fdid() : "",
                    staged.attachedEForms(), workingDirectory));
            emailAttachmentList.addAll(emailComposeManager.prepareEDocAttachments(
                    loggedInInfo, staged.attachedDocuments(), workingDirectory));
            emailAttachmentList.addAll(emailComposeManager.prepareLabAttachments(
                    loggedInInfo, staged.attachedLabs(), workingDirectory));
            emailAttachmentList.addAll(emailComposeManager.prepareHRMAttachments(
                    loggedInInfo, staged.attachedHRMDocuments(), workingDirectory));
            emailAttachmentList.addAll(emailComposeManager.prepareFormAttachments(
                    request, response, staged.attachedForms(), demographicNo, workingDirectory));
            emailComposeManager.sanitizeAttachments(emailAttachmentList);
            for (EmailAttachment attachment : emailAttachmentList) {
                previews.put(attachment.getFilePath(), issuePreview(loggedInInfo, attachment.getFilePath()));
            }
        } catch (SecurityException e) {
            // A denial for one of the attachments is not a preparation failure to retry.
            workingDirectory.close();
            throw e;
        } catch (PDFGenerationException | RuntimeException e) {
            workingDirectory.close();
            logger.error("Unable to prepare email attachments; causeType={}", e.getClass().getName());
            return emailComposeError(request, "This eForm and its attachments could not be prepared for email. Please reopen the compose window and try again.");
        }

        // The compose screen now has a single "Message" field (issue #3118). Seed it from the
        // channel matching the encryption state. For encrypted drafts where both legacy channels
        // contain content, the protected channel deliberately wins: there is no reliable way to
        // distinguish a meaningful historical cleartext body from the fixed notice stored by the
        // unified workflow.
        // Fail closed when older entry points do not seed either session flag: only an explicit
        // Boolean false may open the composer with message or attachment encryption disabled.
        boolean isEmailEncrypted = !Boolean.FALSE.equals(staged.isEmailEncrypted());
        boolean isEmailAttachmentEncrypted = !Boolean.FALSE.equals(staged.isEmailAttachmentEncrypted());
        isEmailEncrypted = EmailData.resolveMergedMessageEncryption(
                isEmailEncrypted, staged.bodyEmail(), staged.encryptedMessageEmail());
        EmailComposeView view = new EmailComposeView(
                fid,
                staged.senderEmail(),
                staged.subjectEmail(),
                EmailData.mergeMessage(isEmailEncrypted, staged.bodyEmail(), staged.encryptedMessageEmail()),
                isEmailEncrypted,
                isEmailAttachmentEncrypted,
                shouldAutoSendEmail(staged.isEmailAutoSend(), isEmailEncrypted),
                staged.emailPatientChartOption(),
                previews,
                staged.footerEmail());

        PreparedEmailComposeView prepared;
        try {
            prepared = emailComposeSubmissionStateService.prepareComposeView(
                    request,
                    emailPdfPasswordService,
                    emailAttachmentList,
                    EmailComposeSubmissionContext.eform(
                            demographicId,
                            staged.fdid(),
                            isTrue(staged.openEFormAfterEmail()),
                            isTrue(staged.deleteEFormAfterEmail())),
                    workingDirectory,
                    view);
        } catch (RuntimeException e) {
            workingDirectory.close();
            logger.warn("Unable to prepare email compose submission state", e);
            return emailComposeError(request, EMAIL_COMPOSE_STATE_UNAVAILABLE_MESSAGE);
        }

        redirectToComposeView(prepared.viewId());
        return NONE;
    }

    /**
     * Renders a prepared compose view without changing any state (#3632).
     *
     * <p>Nothing here touches the session: a missing view must not clear a compose that another
     * window has just staged. Consent, recipients and sender accounts are read again so the page
     * reflects the chart as it is now. The preview capabilities issued during preparation are
     * reused while at least half of their two minutes remains; a page refreshed later gets one new
     * capability per file, never one per request.</p>
     *
     * Request Attributes Set:
     * <ul>
     *   <li>transactionType, emailConsentName, emailConsentStatus, emailConsentMessageKey</li>
     *   <li>receiverName, receiverEmailList, invalidReceiverEmailList, senderAccounts</li>
     *   <li>emailPDFPassword, emailPDFPasswordClue, emailPDFPasswordToken</li>
     *   <li>emailAttachmentList (display copies carrying each file's current preview token)</li>
     *   <li>senderEmail, subjectEmail, message, emailPatientChartOption, demographicId, fdid, fid</li>
     *   <li>footerEmail (see {@link #resolveComposeFooter}); footerClinicChanged and
     *       clinicChangeKeptOwnFooter while the user has not answered a clinic footer change</li>
     *   <li>openEFormAfterEmail, deleteEFormAfterEmail, isEmailEncrypted,
     *       isEmailAttachmentEncrypted, isEmailAutoSend</li>
     * </ul>
     *
     * @param viewId opaque id from the compose URL
     * @return "compose", or "composeExpired" when the view is unknown, belongs to another session,
     *         was already sent, or its files are gone
     */
    // Package-private so tests can drive prepare then view directly, as they do prepareComposeEFormMailer().
    String renderPreparedCompose(String viewId) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // The page carries the PDF password and a live submission token; never let a cache
        // replay it after the token has been used.
        response.setHeader("Cache-Control", "no-store");

        EmailComposeViewState prepared = emailComposeSubmissionStateService.findView(request, viewId);
        if (prepared == null) {
            return composeExpired();
        }
        EmailComposeSubmissionState state = prepared.state();
        EmailComposeView view = state.view();
        EmailComposeSubmissionContext context = state.context();
        int demographicNo = Integer.parseInt(context.demographicId());
        // Before a preview capability is issued or anything about the patient is rendered.
        requireDemographicRead(loggedInInfo, demographicNo);

        List<EmailAttachment> emailAttachmentList;
        try {
            emailAttachmentList = previewCopies(loggedInInfo, state.emailAttachmentList(), view.previews());
        } catch (SecurityException e) {
            // A denial is not a stale window; let it surface as one.
            throw e;
        } catch (PDFGenerationException | RuntimeException e) {
            // A prepared file can only disappear with its state (expiry, trim, or a send), so the
            // window is as stale as an unknown view.
            logger.warn("Prepared email compose attachments are no longer available; causeType={}",
                    e.getClass().getName());
            return composeExpired();
        }

        String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, demographicNo);
        String receiverName = demographicManager.getDemographicFormattedName(loggedInInfo, demographicNo);
        List<?>[] receiverEmailList = emailComposeManager.getRecipients(loggedInInfo, demographicNo);
        List<EmailConfig> senderAccounts = emailComposeManager.getAllSenderAccounts();

        request.setAttribute("transactionType", TransactionType.EFORM);
        request.setAttribute("emailConsentName", emailConsent[0]);
        request.setAttribute("emailConsentStatus", emailConsent[1]);
        request.setAttribute("emailConsentMessageKey", emailConsent[2]);
        request.setAttribute("receiverName", receiverName);
        request.setAttribute("receiverEmailList", receiverEmailList[0]);
        request.setAttribute("invalidReceiverEmailList", receiverEmailList[1]);
        request.setAttribute("senderAccounts", senderAccounts);
        request.setAttribute("emailPDFPassword", state.emailPDFPassword());
        request.setAttribute("emailPDFPasswordClue", state.emailPDFPasswordClue());
        request.setAttribute("emailAttachmentList", emailAttachmentList);
        request.setAttribute("senderEmail", view.senderEmail());
        request.setAttribute("subjectEmail", view.subjectEmail());
        request.setAttribute("message", view.message());
        // No session user means no footer of their own; the clinic default still applies.
        String providerNo = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
        request.setAttribute("footerEmail",
                resolveComposeFooter(view.footerEmail(), emailFooterService.composeFooter(providerNo)));
        if (emailFooterService.clinicChangeNotice(providerNo) != null) {
            request.setAttribute("footerClinicChanged", true);
            request.setAttribute("clinicChangeKeptOwnFooter", emailFooterService.clinicChangeKeptOwnFooter(providerNo));
        }
        request.setAttribute("emailPatientChartOption", view.emailPatientChartOption());
        request.setAttribute(DEMOGRAPHIC_ID_KEY, context.demographicId());
        request.setAttribute("fdid", context.fdid());
        request.setAttribute("fid", view.fid());
        request.setAttribute("openEFormAfterEmail", context.openEFormAfterEmail());
        request.setAttribute("deleteEFormAfterEmail", context.deleteEFormAfterEmail());
        request.setAttribute("isEmailEncrypted", view.emailEncrypted());
        request.setAttribute("isEmailAttachmentEncrypted", view.emailAttachmentEncrypted());
        request.setAttribute("isEmailAutoSend", view.emailAutoSend());
        request.setAttribute(
                EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM,
                prepared.emailPDFPasswordToken());

        return "compose";
    }

    /**
     * Picks the footer the compose screen opens with (issue #3981): the footer the eForm staged
     * (a blank one counts as none), otherwise the user's footer, which is their own or the clinic
     * default (see {@link EmailFooterService#composeFooter}), otherwise empty. Staff can type or
     * change it before sending; changing the sending account never changes it.
     *
     * @param stagedFooter footer the eForm posted, or null
     * @param userFooter the logged-in user's footer, empty when they have none
     * @return the footer text, never null
     */
    static String resolveComposeFooter(String stagedFooter, Optional<String> userFooter) {
        if (stagedFooter != null && !stagedFooter.isBlank()) {
            return stagedFooter;
        }
        return userFooter.orElse("");
    }

    /**
     * Copies the stored attachments for display with a preview capability each: the stored one
     * while it still resolves with at least {@link #PREVIEW_REISSUE_MARGIN} left, otherwise a new
     * one, which replaces it in the view.
     */
    private List<EmailAttachment> previewCopies(LoggedInInfo loggedInInfo, List<EmailAttachment> stored,
            Map<String, IssuedPreview> previews) throws PDFGenerationException {
        long reuseUntilAge = PdfPreviewCapabilityService.TTL.minus(PREVIEW_REISSUE_MARGIN).toMillis();
        List<EmailAttachment> copies = new ArrayList<>(stored.size());
        for (EmailAttachment attachment : stored) {
            IssuedPreview preview = previews.get(attachment.getFilePath());
            if (preview == null
                    || System.currentTimeMillis() - preview.issuedAtMillis() >= reuseUntilAge
                    || pdfPreviewCapabilityService.resolve(request, loggedInInfo, preview.token()) == null) {
                preview = issuePreview(loggedInInfo, attachment.getFilePath());
                previews.put(attachment.getFilePath(), preview);
            }
            EmailAttachment copy = new EmailAttachment(
                    attachment.getFileName(),
                    attachment.getFilePath(),
                    attachment.getDocumentType(),
                    attachment.getDocumentId(),
                    attachment.getFileSize());
            copy.setPreviewToken(preview.token());
            copies.add(copy);
        }
        return copies;
    }

    private IssuedPreview issuePreview(LoggedInInfo loggedInInfo, String filePath) throws PDFGenerationException {
        return new IssuedPreview(
                pdfPreviewCapabilityService.issue(request, loggedInInfo, java.nio.file.Path.of(filePath)),
                System.currentTimeMillis());
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is this action's own same-origin route with a server-generated view id.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is this action's own same-origin route with a server-generated view id")
    private void redirectToComposeView(String viewId) {
        String path = request.getContextPath() + "/email/emailComposeAction?" + EMAIL_COMPOSE_VIEW_PARAM + "="
                + URLEncoder.encode(viewId, StandardCharsets.UTF_8);
        try {
            response.sendRedirect(path);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to redirect to the prepared email compose view", e);
        }
    }

    /**
     * Snapshots the staged compose values and removes them from the session in one step.
     *
     * <p>Locking on the session object serializes two requests for the same staged compose where
     * the container hands every request the same session object, as Tomcat does; the second then
     * finds nothing to prepare. Without that guarantee the worst case is two preparations of the
     * same compose, which is what every request did before.</p>
     *
     * <p>It is atomic only against other prepare requests. AddEForm2Action writes the staged values
     * one attribute at a time without this lock, so two eForm saves in one session at the same
     * instant can still interleave, as they always could.</p>
     */
    private static StagedCompose takeStagedCompose(HttpSession session) {
        // The session itself unless HttpSessionMutexListener is registered; either way one lock
        // per session that every prepare request agrees on.
        synchronized (WebUtils.getSessionMutex(session)) {
            StagedCompose staged = new StagedCompose(
                    isTrue(session.getAttribute("attachEFormItSelf")),
                    (String) session.getAttribute("fdid"),
                    (String) session.getAttribute(DEMOGRAPHIC_ID_KEY),
                    (String[]) session.getAttribute("attachedDocuments"),
                    (String[]) session.getAttribute("attachedLabs"),
                    (String[]) session.getAttribute("attachedForms"),
                    (String[]) session.getAttribute("attachedEForms"),
                    (String[]) session.getAttribute("attachedHRMDocuments"),
                    (String) session.getAttribute("senderEmail"),
                    (String) session.getAttribute("subjectEmail"),
                    (String) session.getAttribute("bodyEmail"),
                    (String) session.getAttribute("encryptedMessageEmail"),
                    (String) session.getAttribute("emailPatientChartOption"),
                    (String) session.getAttribute("footerEmail"),
                    session.getAttribute("isEmailEncrypted"),
                    session.getAttribute("isEmailAttachmentEncrypted"),
                    session.getAttribute("isEmailAutoSend"),
                    session.getAttribute("openEFormAfterEmail"),
                    session.getAttribute("deleteEFormAfterEmail"));
            for (String key : EMAIL_SESSION_KEYS) {
                session.removeAttribute(key);
            }
            return staged;
        }
    }

    /** The compose values AddEForm2Action stages in the session before redirecting here. */
    private record StagedCompose(
            boolean attachEFormItSelf,
            String fdid,
            String demographicId,
            String[] attachedDocuments,
            String[] attachedLabs,
            String[] attachedForms,
            String[] attachedEForms,
            String[] attachedHRMDocuments,
            String senderEmail,
            String subjectEmail,
            String bodyEmail,
            String encryptedMessageEmail,
            String emailPatientChartOption,
            String footerEmail,
            Object isEmailEncrypted,
            Object isEmailAttachmentEncrypted,
            Object isEmailAutoSend,
            Object openEFormAfterEmail,
            Object deleteEFormAfterEmail
    ) {
        // A record compares and prints array components by identity; compare their contents.
        @Override
        public boolean equals(Object other) {
            return other instanceof StagedCompose that
                    && attachEFormItSelf == that.attachEFormItSelf
                    && Objects.equals(fdid, that.fdid)
                    && Objects.equals(demographicId, that.demographicId)
                    && Arrays.equals(attachedDocuments, that.attachedDocuments)
                    && Arrays.equals(attachedLabs, that.attachedLabs)
                    && Arrays.equals(attachedForms, that.attachedForms)
                    && Arrays.equals(attachedEForms, that.attachedEForms)
                    && Arrays.equals(attachedHRMDocuments, that.attachedHRMDocuments)
                    && Objects.equals(senderEmail, that.senderEmail)
                    && Objects.equals(subjectEmail, that.subjectEmail)
                    && Objects.equals(bodyEmail, that.bodyEmail)
                    && Objects.equals(encryptedMessageEmail, that.encryptedMessageEmail)
                    && Objects.equals(emailPatientChartOption, that.emailPatientChartOption)
                    && Objects.equals(footerEmail, that.footerEmail)
                    && Objects.equals(isEmailEncrypted, that.isEmailEncrypted)
                    && Objects.equals(isEmailAttachmentEncrypted, that.isEmailAttachmentEncrypted)
                    && Objects.equals(isEmailAutoSend, that.isEmailAutoSend)
                    && Objects.equals(openEFormAfterEmail, that.openEFormAfterEmail)
                    && Objects.equals(deleteEFormAfterEmail, that.deleteEFormAfterEmail);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(attachEFormItSelf, fdid, demographicId, senderEmail, subjectEmail, bodyEmail,
                    encryptedMessageEmail, emailPatientChartOption, footerEmail, isEmailEncrypted,
                    isEmailAttachmentEncrypted, isEmailAutoSend, openEFormAfterEmail, deleteEFormAfterEmail);
            result = 31 * result + Arrays.hashCode(attachedDocuments);
            result = 31 * result + Arrays.hashCode(attachedLabs);
            result = 31 * result + Arrays.hashCode(attachedForms);
            result = 31 * result + Arrays.hashCode(attachedEForms);
            return 31 * result + Arrays.hashCode(attachedHRMDocuments);
        }

        /** The attachment ids only: the subject, message, footer and addresses are patient information. */
        @Override
        public String toString() {
            return "StagedCompose[fdid=" + fdid
                    + ", attachedDocuments=" + Arrays.toString(attachedDocuments)
                    + ", attachedLabs=" + Arrays.toString(attachedLabs)
                    + ", attachedForms=" + Arrays.toString(attachedForms)
                    + ", attachedEForms=" + Arrays.toString(attachedEForms)
                    + ", attachedHRMDocuments=" + Arrays.toString(attachedHRMDocuments) + "]";
        }
    }

    private static boolean shouldAutoSendEmail(Object autoSendValue, Object encryptedValue) {
        return isTrue(autoSendValue) && !isTrue(encryptedValue);
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    /**
     * Cleans up email-related session attributes.
     * The prepare step takes these attributes itself. Any other caller removes whatever compose
     * an eForm save has staged in this session, which may belong to another open window.
     *
     * @param request the HTTP servlet request containing the session to clean up
     * @since 2025-01-18
     */
    public static void cleanupEmailSessionAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        for (String key : EMAIL_SESSION_KEYS) {
            session.removeAttribute(key);
        }
    }

    /**
     * Handles email composition errors by setting error message and returning error result.
     *
     * This method is called when attachment or compose-state preparation fails after an eForm save,
     * which returns the provider to that eForm. It sets a caller-provided,
     * user-safe error message as a request attribute for display on the error page. Attachment
     * preparation failures must pass generic messages here and keep any server diagnostics free of
     * PHI.
     *
     * Common Error Scenarios:
     * <ul>
     *   <li>PDF generation failure for eForms, documents, or forms</li>
     *   <li>Missing or inaccessible attachment files</li>
     *   <li>File I/O errors during attachment preparation</li>
     *   <li>Encryption errors for PDF password protection</li>
     * </ul>
     *
     * @param request HttpServletRequest the HTTP servlet request to store the error message
     * @param errorMessage String the PHI-safe error message to display to the user
     * @return String the Struts2 result name "eFormError" which maps to the error display page
     * @see io.github.carlos_emr.carlos.utility.PDFGenerationException
     */
    private String emailComposeError(HttpServletRequest request, String errorMessage) {
        // The staged values were taken before any error could occur. Clearing again here would
        // remove a compose another window staged meanwhile.
        request.setAttribute("errorMessage", errorMessage);
        return "eFormError";
    }

    /**
     * Requires read access to this patient's chart, honouring per-patient restrictions
     * ({@code _demographic$<no>} and {@code _eChart$<no>}), as the email views do. The attachment
     * preparers check their own objects without a patient, and saving an eForm checks only
     * {@code _eform}, so this is the patient-level check.
     */
    private void requireDemographicRead(LoggedInInfo loggedInInfo, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("Access to the email patient record is denied");
        }
    }

    /** Shows the expired page. It changes nothing, so it is safe from both prepare and view. */
    private String composeExpired() {
        request.setAttribute("errorMessage", EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        return COMPOSE_EXPIRED_RESULT;
    }
}
