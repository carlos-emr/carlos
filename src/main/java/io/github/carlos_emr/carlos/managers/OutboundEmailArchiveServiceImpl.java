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

package io.github.carlos_emr.carlos.managers;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDeletionDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveLegalHoldEventDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveAttachment;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveDeletion;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveLegalHoldEvent;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveAttachmentDto;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Stores finalized outbound email artifacts in eDoc and records archive/deletion audit metadata.
 *
 * @since 2026-07-07
 */
@SuppressWarnings("java:S2143") // CARLOS Hibernate models still use java.util.Date for DATETIME fields.
@Service
public class OutboundEmailArchiveServiceImpl implements OutboundEmailArchiveService {

    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int MAX_CONTENT_TYPE_LENGTH = 100;
    private static final String DOCUMENT_DOCTYPE = "email";
    private static final String DOCUMENT_SOURCE = "Outbound Email Archive";
    private static final String CTL_DOCUMENT_MODULE_DEMOGRAPHIC = "demographic";

    private final DocumentManager documentManager;

    private final EmailLogDao emailLogDao;

    private final OutboundEmailArchiveDao outboundEmailArchiveDao;

    private final OutboundEmailArchiveDeletionDao outboundEmailArchiveDeletionDao;

    private final OutboundEmailArchiveLegalHoldEventDao outboundEmailArchiveLegalHoldEventDao;

    private final CtlDocumentDao ctlDocumentDao;

    private final SecurityInfoManager securityInfoManager;

    @Autowired
    public OutboundEmailArchiveServiceImpl(
            DocumentManager documentManager,
            EmailLogDao emailLogDao,
            OutboundEmailArchiveDao outboundEmailArchiveDao,
            OutboundEmailArchiveDeletionDao outboundEmailArchiveDeletionDao,
            OutboundEmailArchiveLegalHoldEventDao outboundEmailArchiveLegalHoldEventDao,
            CtlDocumentDao ctlDocumentDao,
            SecurityInfoManager securityInfoManager) {
        this.documentManager = documentManager;
        this.emailLogDao = emailLogDao;
        this.outboundEmailArchiveDao = outboundEmailArchiveDao;
        this.outboundEmailArchiveDeletionDao = outboundEmailArchiveDeletionDao;
        this.outboundEmailArchiveLegalHoldEventDao = outboundEmailArchiveLegalHoldEventDao;
        this.ctlDocumentDao = ctlDocumentDao;
        this.securityInfoManager = securityInfoManager;
    }

    @SuppressWarnings("java:S6206") // A record would generate byte[] identity-based equals/hashCode/toString for artifactBytes.
    private static final class ArchiveBuildContext {

        private final Document savedDocument;
        private final byte[] artifactBytes;
        private final String contentType;
        private final String archiveFileName;
        private final List<OutboundEmailArchiveAttachment> attachments;
        private final String providerNo;

        private ArchiveBuildContext(
                Document savedDocument,
                byte[] artifactBytes,
                String contentType,
                String archiveFileName,
                List<OutboundEmailArchiveAttachment> attachments,
                String providerNo) {
            this.savedDocument = savedDocument;
            this.artifactBytes = artifactBytes;
            this.contentType = contentType;
            this.archiveFileName = archiveFileName;
            this.attachments = attachments;
            this.providerNo = providerNo;
        }

        private Document savedDocument() {
            return savedDocument;
        }

        private byte[] artifactBytes() {
            return artifactBytes;
        }

        private String contentType() {
            return contentType;
        }

        private String archiveFileName() {
            return archiveFileName;
        }

        private List<OutboundEmailArchiveAttachment> attachments() {
            return attachments;
        }

        private String providerNo() {
            return providerNo;
        }
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public OutboundEmailArchive archive(LoggedInInfo loggedInInfo, OutboundEmailArchiveDto request) throws IOException {
        validateArchiveRequest(loggedInInfo, request);

        EmailLog requestedEmailLog = request.getEmailLog();
        byte[] artifactBytes = request.getArtifactBytes();
        String contentType = archiveContentType(request.getContentType());
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (providerNo == null || providerNo.isBlank()) {
            throw new IllegalArgumentException("Provider number is required for outbound email archive");
        }
        // Authority check precedes the EmailLog read: a caller with no eDoc write
        // right must not be able to probe emailLog ids through the "not found" message.
        requireArchiveWriteAuthority(loggedInInfo);
        EmailLog emailLog = loadEmailLog(requestedEmailLog.getId());
        String fileName = uniqueArchiveFileName(emailLog, contentType);
        Integer demographicNo = emailLog.getDemographic().getDemographicNo();
        requirePatientRecordAccess(loggedInInfo, demographicNo);
        List<OutboundEmailArchiveAttachment> attachments = buildAttachments(request, providerNo, demographicNo);

        Document document = buildDocument(emailLog, fileName, contentType, providerNo);
        Document savedDocument;
        try {
            savedDocument = documentManager.createDocument(
                    loggedInInfo,
                    document,
                    demographicNo,
                    providerNo,
                    artifactBytes);
        } catch (IOException | RuntimeException e) {
            deleteArchivedDocumentFile(document.getDocfilename() != null ? document.getDocfilename() : fileName);
            throw e;
        }
        registerRollbackCleanup(savedDocument);

        ArchiveBuildContext buildContext = new ArchiveBuildContext(savedDocument, artifactBytes, contentType, fileName, attachments, providerNo);
        OutboundEmailArchive archive = buildArchive(request, emailLog, buildContext);
        outboundEmailArchiveDao.persist(archive);

        registerAfterCommitLog(() -> LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.archive",
                "Outbound email archive",
                "archiveId=" + archive.getId() + " emailLogId=" + emailLog.getId() + " documentNo=" + savedDocument.getId(),
                String.valueOf(demographicNo),
                ""));

        return archive;
    }

    @Override
    @Transactional
    public OutboundEmailArchiveDeletion recordControlledDeletion(LoggedInInfo loggedInInfo, Integer archiveId, String deleteReason) {
        if (archiveId == null) {
            throw new IllegalArgumentException("Archive ID is required");
        }
        if (deleteReason == null || deleteReason.isBlank()) {
            throw new IllegalArgumentException("Delete reason is required");
        }
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null || loggedInInfo.getLoggedInProviderNo().isBlank()) {
            throw new IllegalArgumentException("Deleting provider number is required");
        }

        // Authority check precedes findForUpdate: an unauthorized caller must not be
        // able to take a FOR UPDATE row lock, nor learn which archive ids exist from
        // the "not found" message.
        requireArchiveAdminAuthority(loggedInInfo);

        OutboundEmailArchive archive = lockArchiveForAuthorizedCaller(loggedInInfo, archiveId);

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String truncatedDeleteReason = truncate(deleteReason.trim(), 1000);
        // Throws while the archive is still under legal hold, which is the default state
        // for every archive — an admin must have released it via releaseLegalHold first.
        archive.markDeleted(providerNo, truncatedDeleteReason);
        OutboundEmailArchiveDeletion deletion = OutboundEmailArchiveDeletion.fromArchive(archive, providerNo, truncatedDeleteReason);

        outboundEmailArchiveDao.merge(archive);
        outboundEmailArchiveDeletionDao.persist(deletion);

        registerAfterCommitLog(() -> LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.recordControlledDeletion",
                "Outbound email archive tombstone",
                "archiveId=" + archive.getId() + " documentNo=" + documentId(archive),
                demographicNo(archive),
                ""));

        return deletion;
    }

    @Override
    @Transactional
    public OutboundEmailArchiveLegalHoldEvent releaseLegalHold(LoggedInInfo loggedInInfo, Integer archiveId, String reason) {
        return changeLegalHold(loggedInInfo, archiveId, reason, OutboundEmailArchiveLegalHoldEvent.ACTION_RELEASED);
    }

    @Override
    @Transactional
    public OutboundEmailArchiveLegalHoldEvent placeLegalHold(LoggedInInfo loggedInInfo, Integer archiveId, String reason) {
        return changeLegalHold(loggedInInfo, archiveId, reason, OutboundEmailArchiveLegalHoldEvent.ACTION_PLACED);
    }

    /**
     * Shared transition for both legal hold directions.
     *
     * <p>Kept as one method because the authority gate, patient-record check, row lock,
     * event record, and audit entry are identical; only the entity transition differs.
     * The {@code FOR UPDATE} lock matters here for the same reason it does on deletion:
     * two concurrent releases must not both succeed and write two RELEASED events.</p>
     */
    private OutboundEmailArchiveLegalHoldEvent changeLegalHold(
            LoggedInInfo loggedInInfo, Integer archiveId, String reason, String action) {
        if (archiveId == null) {
            throw new IllegalArgumentException("Archive ID is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Legal hold reason is required");
        }
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null || loggedInInfo.getLoggedInProviderNo().isBlank()) {
            throw new IllegalArgumentException("Legal hold provider number is required");
        }

        requireArchiveAdminAuthority(loggedInInfo);

        OutboundEmailArchive archive = lockArchiveForAuthorizedCaller(loggedInInfo, archiveId);

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String truncatedReason = truncate(reason.trim(), 1000);

        if (OutboundEmailArchiveLegalHoldEvent.ACTION_RELEASED.equals(action)) {
            archive.releaseLegalHold(providerNo);
        } else {
            archive.placeLegalHold(providerNo);
        }

        OutboundEmailArchiveLegalHoldEvent event =
                OutboundEmailArchiveLegalHoldEvent.of(archive, action, providerNo, truncatedReason);

        outboundEmailArchiveDao.merge(archive);
        outboundEmailArchiveLegalHoldEventDao.persist(event);

        registerAfterCommitLog(() -> LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.changeLegalHold",
                "Outbound email archive legal hold " + action,
                "archiveId=" + archive.getId() + " documentNo=" + documentId(archive),
                demographicNo(archive),
                ""));

        return event;
    }

    private void validateArchiveRequest(LoggedInInfo loggedInInfo, OutboundEmailArchiveDto request) {
        if (loggedInInfo == null) {
            throw new IllegalArgumentException("Logged-in user context is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Archive request is required");
        }
        EmailLog emailLog = request.getEmailLog();
        if (emailLog == null || emailLog.getId() == null) {
            throw new IllegalArgumentException("Persisted EmailLog is required");
        }
        byte[] artifactBytes = request.getArtifactBytes();
        if (artifactBytes == null || artifactBytes.length == 0) {
            throw new IllegalArgumentException("Outbound artifact bytes are required");
        }
        if (request.getArtifactType() == null || request.getArtifactType().isBlank()) {
            throw new IllegalArgumentException("Artifact type is required");
        }
    }

    private Document buildDocument(EmailLog emailLog, String fileName, String contentType, String providerNo) {
        Date now = new Date();
        Document document = new Document();
        document.setDocfilename(fileName);
        document.setDocdesc("Outbound email archive " + emailLog.getId());
        document.setDoctype(DOCUMENT_DOCTYPE);
        document.setDocClass("EMAIL");
        document.setDocSubClass("OUTBOUND");
        document.setDoccreator(providerNo);
        document.setResponsible(providerNo);
        document.setSource(DOCUMENT_SOURCE);
        document.setStatus(Document.STATUS_ACTIVE);
        document.setContenttype(contentType);
        document.setContentdatetime(now);
        document.setObservationdate(now);
        document.setPublic1(0);
        return document;
    }

    private EmailLog loadEmailLog(Integer emailLogId) {
        EmailLog emailLog = emailLogDao.find(emailLogId);
        if (emailLog == null) {
            throw new IllegalArgumentException("EmailLog not found: " + emailLogId);
        }
        Demographic demographic = emailLog.getDemographic();
        if (demographic == null || demographic.getDemographicNo() == null) {
            throw new IllegalArgumentException("EmailLog demographic is required");
        }
        return emailLog;
    }

    private OutboundEmailArchive buildArchive(
            OutboundEmailArchiveDto request,
            EmailLog emailLog,
            ArchiveBuildContext buildContext) {

        OutboundEmailArchive archive = new OutboundEmailArchive();
        EmailConfig emailConfig = emailLog.getEmailConfig();
        String originalFileName = defaultIfBlank(request.getFileName(), buildContext.archiveFileName());
        Document savedDocument = buildContext.savedDocument();
        byte[] artifactBytes = buildContext.artifactBytes();

        archive.setEmailLog(emailLog);
        archive.setDemographic(emailLog.getDemographic());
        archive.setProvider(emailLog.getProvider());
        archive.setEmailConfig(emailConfig);
        archive.setDocument(savedDocument);
        archive.setArtifactType(truncate(request.getArtifactType(), 50));
        archive.setTransportType(truncate(defaultTransportType(request, emailConfig), 50));
        archive.setProviderName(truncate(defaultProviderName(request, emailConfig), 100));
        archive.setProviderMessageId(truncate(request.getProviderMessageId(), 255));
        archive.setProviderResponse(truncate(request.getProviderResponse(), 1000));
        archive.setContentType(truncate(buildContext.contentType(), 100));
        archive.setFileName(truncate(savedDocument.getDocfilename(), 255));
        archive.setOriginalFileName(truncate(originalFileName, 255));
        archive.setSha256Hash(sha256Hex(artifactBytes));
        archive.setByteSize((long) artifactBytes.length);
        archive.setStorageType(OutboundEmailArchive.STORAGE_TYPE_EDOC);
        archive.setRetentionPolicy(OutboundEmailArchive.RETENTION_POLICY_PERMANENT);
        archive.setSendStatus(OutboundEmailArchive.SEND_STATUS_ARCHIVED);
        archive.setLastUpdateUser(buildContext.providerNo());

        for (OutboundEmailArchiveAttachment attachment : safeArchiveAttachmentList(buildContext.attachments())) {
            archive.addAttachment(attachment);
        }

        return archive;
    }

    private List<OutboundEmailArchiveAttachment> buildAttachments(OutboundEmailArchiveDto request, String providerNo, Integer demographicNo) {
        List<OutboundEmailArchiveAttachmentDto> attachmentRequests = safeAttachmentList(request.getAttachments());
        if (attachmentRequests.isEmpty()) {
            return List.of();
        }

        List<OutboundEmailArchiveAttachment> attachments = new ArrayList<>();
        for (OutboundEmailArchiveAttachmentDto attachmentRequest : attachmentRequests) {
            attachments.add(buildAttachment(attachmentRequest, providerNo, demographicNo));
        }
        return attachments;
    }

    private OutboundEmailArchiveAttachment buildAttachment(OutboundEmailArchiveAttachmentDto request, String providerNo, Integer demographicNo) {
        if (request == null) {
            throw new IllegalArgumentException("Attachment request is required");
        }

        byte[] attachmentBytes = request.getArtifactBytes();
        Document attachmentDocument = request.getDocument();
        if (attachmentBytes != null && attachmentDocument == null) {
            throw new IllegalArgumentException("Persisted attachment document is required when attachment bytes are supplied");
        }
        validateAttachmentDocumentDemographic(attachmentDocument, demographicNo);
        String sha256Hash = request.getSha256Hash();
        Long byteSize = request.getByteSize();
        if (attachmentBytes != null) {
            sha256Hash = sha256Hex(attachmentBytes);
            byteSize = (long) attachmentBytes.length;
        }
        sha256Hash = normalizeSha256Hex(sha256Hash);
        if (byteSize == null || byteSize < 0) {
            throw new IllegalArgumentException("Attachment byte size is required");
        }

        validateSourceDocumentId(request.getSourceDocumentId(), attachmentDocument);

        OutboundEmailArchiveAttachment attachment = new OutboundEmailArchiveAttachment();
        attachment.setFileName(truncate(defaultIfBlank(request.getFileName(), "attachment"), 255));
        // Same normalisation as the main artifact: strip MIME parameters before the
        // length cap, so a long "type/subtype; name=..." is not cut mid-parameter into
        // a malformed content type.
        attachment.setContentType(archiveContentType(request.getContentType()));
        attachment.setSha256Hash(sha256Hash);
        attachment.setByteSize(byteSize);
        attachment.setSourceDocumentType(truncate(request.getSourceDocumentType(), 50));
        attachment.setSourceDocumentId(request.getSourceDocumentId());
        attachment.setDocument(attachmentDocument);
        attachment.setLastUpdateUser(providerNo);
        return attachment;
    }

    /**
     * Keeps {@code sourceDocumentId} consistent with the linked eDoc {@code Document}.
     *
     * <p>{@code documentNo} is demographic-checked in
     * {@link #validateAttachmentDocumentDemographic}; {@code sourceDocumentId} is not,
     * and it has no foreign key. Left unchecked, an attachment could be stored claiming
     * provenance from one patient's document while linking to another's, so when both
     * are present they must agree.</p>
     *
     * <p><b>Contract for future readers:</b> when no {@code Document} is supplied,
     * {@code sourceDocumentId} is caller-asserted provenance metadata for an artifact
     * that lives outside the eDoc store. It is <em>not</em> demographic-checked and MUST
     * NOT be used as a fetch key to resolve and display a CARLOS document — doing so
     * would reintroduce a cross-patient read. Any viewer must read through
     * {@code documentNo} instead.</p>
     */
    private void validateSourceDocumentId(Integer sourceDocumentId, Document attachmentDocument) {
        if (sourceDocumentId == null || attachmentDocument == null) {
            return;
        }
        if (!sourceDocumentId.equals(attachmentDocument.getId())) {
            throw new IllegalArgumentException(
                    "Attachment sourceDocumentId does not match the linked attachment document");
        }
    }

    private void validateAttachmentDocumentDemographic(Document attachmentDocument, Integer demographicNo) {
        if (attachmentDocument == null) {
            return;
        }
        Integer documentNo = attachmentDocument.getId();
        if (documentNo == null) {
            throw new IllegalArgumentException("Persisted attachment document is required when attachment document metadata is supplied");
        }

        List<CtlDocument> ctlDocuments = ctlDocumentDao.findByDocumentNoAndModule(documentNo, CTL_DOCUMENT_MODULE_DEMOGRAPHIC);
        for (CtlDocument ctlDocument : safeCtlDocumentList(ctlDocuments)) {
            if (ctlDocument != null
                    && ctlDocument.getId() != null
                    && demographicNo.equals(ctlDocument.getId().getModuleId())) {
                return;
            }
        }
        throw new SecurityException("attachment document is not linked to outbound email archive demographic");
    }

    /**
     * Reduces a caller-supplied Content-Type to the bare media type stored on the
     * eDoc row and the archive metadata.
     *
     * <p>MIME parameters are dropped rather than truncated. A blind
     * {@code substring(0, 100)} on something like
     * {@code message/rfc822; name="<200 char filename>"} persists a syntactically
     * broken Content-Type — an unterminated quoted string — into
     * {@code document.contenttype}, which is what every eDoc viewer and download
     * handler reads back. Nothing is lost: the filename hint is already retained in
     * {@code originalFileName}, and an RFC822 artifact carries its own charset in
     * its MIME headers. The length bound stays as a backstop for absurd media
     * types.</p>
     */
    private String archiveContentType(String requestedContentType) {
        String mediaType = normalizeMediaType(requestedContentType);
        return truncate(defaultIfBlank(mediaType, DEFAULT_CONTENT_TYPE), MAX_CONTENT_TYPE_LENGTH);
    }

    private String uniqueArchiveFileName(EmailLog emailLog, String contentType) {
        return "outbound-email-" + emailLog.getId() + "-" + UUID.randomUUID().toString() + extensionForContentType(contentType);
    }

    private String extensionForContentType(String contentType) {
        String mediaType = normalizeMediaType(contentType);
        if (asciiEqualsIgnoreCase(mediaType, "message/rfc822")) {
            return ".eml";
        }
        if (asciiEqualsIgnoreCase(mediaType, "application/json")) {
            return ".json";
        }
        return ".bin";
    }

    private String normalizeMediaType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameterStart = contentType.indexOf(';');
        String mediaType = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
        return mediaType.trim();
    }

    private String defaultTransportType(OutboundEmailArchiveDto request, EmailConfig emailConfig) {
        if (request.getTransportType() != null && !request.getTransportType().isBlank()) {
            return request.getTransportType();
        }
        return emailConfig != null && emailConfig.getEmailType() != null ? emailConfig.getEmailType().name() : "UNKNOWN";
    }

    private String defaultProviderName(OutboundEmailArchiveDto request, EmailConfig emailConfig) {
        if (request.getProviderName() != null && !request.getProviderName().isBlank()) {
            return request.getProviderName();
        }
        return emailConfig != null && emailConfig.getEmailProvider() != null ? emailConfig.getEmailProvider().name() : "UNKNOWN";
    }

    private String documentId(OutboundEmailArchive archive) {
        return archive.getDocument() != null ? String.valueOf(archive.getDocument().getId()) : "";
    }

    private String demographicNo(OutboundEmailArchive archive) {
        return archive.getDemographic() != null ? String.valueOf(archive.getDemographic().getDemographicNo()) : "";
    }

    private List<OutboundEmailArchiveAttachmentDto> safeAttachmentList(List<OutboundEmailArchiveAttachmentDto> attachments) {
        return attachments != null ? attachments : List.of();
    }

    private List<OutboundEmailArchiveAttachment> safeArchiveAttachmentList(List<OutboundEmailArchiveAttachment> attachments) {
        return attachments != null ? attachments : List.of();
    }

    private List<CtlDocument> safeCtlDocumentList(List<CtlDocument> ctlDocuments) {
        return ctlDocuments != null ? ctlDocuments : List.of();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String truncate(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private String normalizeSha256Hex(String sha256Hash) {
        if (sha256Hash == null) {
            throw new IllegalArgumentException("Attachment SHA-256 hash is required");
        }
        String normalizedHash = sha256Hash.trim();
        if (normalizedHash.length() != 64) {
            throw new IllegalArgumentException("Attachment SHA-256 hash must be 64 hex characters");
        }
        StringBuilder lowerCaseHash = new StringBuilder(64);
        for (int i = 0; i < normalizedHash.length(); i++) {
            char value = normalizedHash.charAt(i);
            if (!isAsciiHexDigit(value)) {
                throw new IllegalArgumentException("Attachment SHA-256 hash must be 64 hex characters");
            }
            lowerCaseHash.append(toLowerAscii(value));
        }
        return lowerCaseHash.toString();
    }

    private boolean asciiEqualsIgnoreCase(String value, String expected) {
        if (value.length() != expected.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (toLowerAscii(value.charAt(i)) != toLowerAscii(expected.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isAsciiHexDigit(char value) {
        return value >= '0' && value <= '9' || value >= 'a' && value <= 'f' || value >= 'A' && value <= 'F';
    }

    private char toLowerAscii(char value) {
        if (value >= 'A' && value <= 'Z') {
            return (char) (value + ('a' - 'A'));
        }
        return value;
    }

    /**
     * Rejects callers without eDoc write authority.
     *
     * <p>Deliberately split from the per-patient check so it can run before any
     * lookup. Loading rows for a caller that has no business calling the service
     * at all turns "not found" messages into an existence oracle and, in the
     * deletion path, lets an unauthorized caller take a {@code FOR UPDATE} row
     * lock.</p>
     */
    private void requireArchiveWriteAuthority(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_edoc w)");
        }
    }

    /**
     * Rejects callers without admin eDoc delete authority.
     *
     * <p>Unlike {@code DocumentUndelete2Action}, plain {@code _edoc w} is NOT
     * accepted here. {@code _edoc w} is the right needed to <em>create</em> an
     * archive, so admitting it would make retiring an evidentiary record no
     * harder than writing one. The same gate guards legal hold changes, since
     * releasing a hold is what makes deletion reachable at all.</p>
     */
    private void requireArchiveAdminAuthority(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.edocdelete", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin.edocdelete w)");
        }
    }

    /**
     * Resolves and write-locks an archive for a privileged state change, refusing a
     * caller who may not see the archive's patient.
     *
     * <p>The unlocked read comes first for the same reason
     * {@link #requireArchiveAdminAuthority} precedes the lookup: {@code findForUpdate}
     * issues {@code SELECT ... FOR UPDATE}, and a caller holding
     * {@code _admin.edocdelete} but no access to this patient must not be able to take
     * a lock on that patient's row. Checking access only after the lock left exactly
     * the side effect moving the authority gate up was meant to remove.</p>
     *
     * <p>The demographic is compared again after locking so the row that gets mutated
     * is provably the row that was authorised, rather than trusting that the two reads
     * described the same patient.</p>
     *
     * @param loggedInInfo current user context
     * @param archiveId persisted archive identifier
     * @return the write-locked archive
     * @throws IllegalArgumentException when no archive has that identifier
     * @throws SecurityException when the caller may not access the archive's patient
     */
    private OutboundEmailArchive lockArchiveForAuthorizedCaller(LoggedInInfo loggedInInfo, Integer archiveId) {
        OutboundEmailArchive unlocked = outboundEmailArchiveDao.find(archiveId);
        if (unlocked == null) {
            throw new IllegalArgumentException("Outbound email archive not found: " + archiveId);
        }
        Integer authorizedDemographicNo = requireArchiveDemographicNo(unlocked);
        requirePatientRecordAccess(loggedInInfo, authorizedDemographicNo);

        OutboundEmailArchive archive = outboundEmailArchiveDao.findForUpdate(archiveId);
        if (archive == null) {
            throw new IllegalArgumentException("Outbound email archive not found: " + archiveId);
        }
        if (!authorizedDemographicNo.equals(requireArchiveDemographicNo(archive))) {
            throw new SecurityException("not authorized for outbound email archive demographic");
        }
        return archive;
    }

    private void requirePatientRecordAccess(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("not authorized for outbound email archive demographic");
        }
    }

    private Integer requireArchiveDemographicNo(OutboundEmailArchive archive) {
        Integer demographicNo = archive.getDemographic() != null ? archive.getDemographic().getDemographicNo() : null;
        if (demographicNo == null) {
            throw new IllegalStateException("Outbound email archive demographic is required");
        }
        return demographicNo;
    }

    private void registerRollbackCleanup(Document savedDocument) {
        if (savedDocument == null || savedDocument.getDocfilename() == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        String fileName = savedDocument.getDocfilename();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteArchivedDocumentFile(fileName);
                }
            }
        });
    }

    private void registerAfterCommitLog(Runnable logAction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            logAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logAction.run();
            }
        });
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "Archive eDoc cleanup validates the generated filename as a single path component and revalidates DOCUMENT_DIR containment before deletion.")
    private void deleteArchivedDocumentFile(String fileName) {
        try {
            File documentDirectory = PathValidationUtils.resolveConfiguredDirectory(
                    CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"),
                    "DOCUMENT_DIR");
            String safeFileName = PathValidationUtils.validatePathComponent(fileName, "archive eDoc filename");
            File archiveFile = PathValidationUtils.validateExistingPath(new File(documentDirectory, safeFileName), documentDirectory);
            Files.deleteIfExists(archiveFile.toPath());
        } catch (IOException | SecurityException e) {
            MiscUtils.getLogger().warn("Failed to delete rolled back outbound email archive eDoc file: {}", e.getClass().getSimpleName());
        }
    }

    private String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX_FORMAT.formatHex(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
