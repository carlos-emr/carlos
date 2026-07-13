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

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDeletionDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveAttachment;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveDeletion;
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
    private static final String DOCUMENT_DOCTYPE = "email";
    private static final String DOCUMENT_SOURCE = "Outbound Email Archive";
    private static final String CTL_DOCUMENT_MODULE_DEMOGRAPHIC = "demographic";

    private final DocumentManager documentManager;

    private final EmailLogDao emailLogDao;

    private final OutboundEmailArchiveDao outboundEmailArchiveDao;

    private final OutboundEmailArchiveDeletionDao outboundEmailArchiveDeletionDao;

    private final CtlDocumentDao ctlDocumentDao;

    private final SecurityInfoManager securityInfoManager;

    @Autowired
    public OutboundEmailArchiveServiceImpl(
            DocumentManager documentManager,
            EmailLogDao emailLogDao,
            OutboundEmailArchiveDao outboundEmailArchiveDao,
            OutboundEmailArchiveDeletionDao outboundEmailArchiveDeletionDao,
            CtlDocumentDao ctlDocumentDao,
            SecurityInfoManager securityInfoManager) {
        this.documentManager = documentManager;
        this.emailLogDao = emailLogDao;
        this.outboundEmailArchiveDao = outboundEmailArchiveDao;
        this.outboundEmailArchiveDeletionDao = outboundEmailArchiveDeletionDao;
        this.ctlDocumentDao = ctlDocumentDao;
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public OutboundEmailArchive archive(LoggedInInfo loggedInInfo, OutboundEmailArchiveDto request) throws IOException {
        validateArchiveRequest(loggedInInfo, request);

        EmailLog requestedEmailLog = request.getEmailLog();
        byte[] artifactBytes = request.getArtifactBytes();
        String contentType = defaultIfBlank(request.getContentType(), DEFAULT_CONTENT_TYPE);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (providerNo == null || providerNo.isBlank()) {
            throw new IllegalArgumentException("Provider number is required for outbound email archive");
        }
        EmailLog emailLog = loadEmailLog(requestedEmailLog.getId());
        String fileName = uniqueArchiveFileName(emailLog, contentType);
        Integer demographicNo = emailLog.getDemographic().getDemographicNo();
        authorizeArchiveAccess(loggedInInfo, demographicNo);
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
            deleteCreatedDocumentFile(document, fileName);
            throw e;
        }
        registerRollbackCleanup(savedDocument);

        OutboundEmailArchive archive = buildArchive(request, emailLog, savedDocument, artifactBytes, contentType, fileName, attachments, providerNo);
        outboundEmailArchiveDao.persist(archive);

        LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.archive",
                "Outbound email archive",
                "archiveId=" + archive.getId() + " emailLogId=" + emailLog.getId() + " documentNo=" + savedDocument.getId(),
                String.valueOf(demographicNo),
                "");

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

        OutboundEmailArchive archive = outboundEmailArchiveDao.find(archiveId);
        if (archive == null) {
            throw new IllegalArgumentException("Outbound email archive not found: " + archiveId);
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        authorizeControlledDeletion(loggedInInfo, archive);
        String truncatedDeleteReason = truncate(deleteReason.trim(), 1000);
        archive.markDeleted(providerNo, truncatedDeleteReason);
        OutboundEmailArchiveDeletion deletion = OutboundEmailArchiveDeletion.fromArchive(archive, providerNo, truncatedDeleteReason);

        outboundEmailArchiveDao.merge(archive);
        outboundEmailArchiveDeletionDao.persist(deletion);

        LogAction.addLog(loggedInInfo,
                "OutboundEmailArchiveService.recordControlledDeletion",
                "Outbound email archive tombstone",
                "archiveId=" + archive.getId() + " documentNo=" + documentId(archive),
                demographicNo(archive),
                "");

        return deletion;
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
        EmailLog emailLog = emailLogDao.find((Object) emailLogId);
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
            Document savedDocument,
            byte[] artifactBytes,
            String contentType,
            String archiveFileName,
            List<OutboundEmailArchiveAttachment> attachments,
            String providerNo) {

        OutboundEmailArchive archive = new OutboundEmailArchive();
        EmailConfig emailConfig = emailLog.getEmailConfig();
        String originalFileName = defaultIfBlank(request.getFileName(), archiveFileName);

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
        archive.setContentType(truncate(contentType, 100));
        archive.setFileName(truncate(savedDocument.getDocfilename(), 255));
        archive.setOriginalFileName(truncate(originalFileName, 255));
        archive.setSha256Hash(sha256Hex(artifactBytes));
        archive.setByteSize((long) artifactBytes.length);
        archive.setStorageType(OutboundEmailArchive.STORAGE_TYPE_EDOC);
        archive.setRetentionPolicy(OutboundEmailArchive.RETENTION_POLICY_PERMANENT);
        archive.setSendStatus(OutboundEmailArchive.SEND_STATUS_ARCHIVED);
        archive.setLastUpdateUser(providerNo);

        for (OutboundEmailArchiveAttachment attachment : safeArchiveAttachmentList(attachments)) {
            archive.addAttachment(attachment);
        }

        return archive;
    }

    private List<OutboundEmailArchiveAttachment> buildAttachments(OutboundEmailArchiveDto request, String providerNo, Integer demographicNo) {
        List<OutboundEmailArchiveAttachmentDto> attachmentRequests = safeAttachmentList(request.getAttachments());
        if (attachmentRequests.isEmpty()) {
            return List.of();
        }

        List<OutboundEmailArchiveAttachment> attachments = new ArrayList<OutboundEmailArchiveAttachment>();
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

        OutboundEmailArchiveAttachment attachment = new OutboundEmailArchiveAttachment();
        attachment.setFileName(truncate(defaultIfBlank(request.getFileName(), "attachment"), 255));
        attachment.setContentType(truncate(request.getContentType(), 100));
        attachment.setSha256Hash(sha256Hash);
        attachment.setByteSize(byteSize);
        attachment.setSourceDocumentType(truncate(request.getSourceDocumentType(), 50));
        attachment.setSourceDocumentId(request.getSourceDocumentId());
        attachment.setDocument(attachmentDocument);
        attachment.setLastUpdateUser(providerNo);
        return attachment;
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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

    private void authorizeArchiveAccess(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("not authorized for outbound email archive demographic");
        }
    }

    private void authorizeControlledDeletion(LoggedInInfo loggedInInfo, OutboundEmailArchive archive) {
        Integer demographicNo = archive.getDemographic() != null ? archive.getDemographic().getDemographicNo() : null;
        if (demographicNo == null) {
            throw new IllegalStateException("Outbound email archive demographic is required");
        }

        boolean canDeleteEdoc = securityInfoManager.hasPrivilege(loggedInInfo, "_admin.edocdelete", SecurityInfoManager.WRITE, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null);
        if (!canDeleteEdoc) {
            throw new SecurityException("missing required sec object (_admin.edocdelete w or _edoc w)");
        }
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("not authorized for outbound email archive demographic");
        }
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

    private void deleteCreatedDocumentFile(Document document, String originalFileName) {
        if (document == null || document.getDocfilename() == null || document.getDocfilename().equals(originalFileName)) {
            return;
        }
        deleteArchivedDocumentFile(document.getDocfilename());
    }

    private void deleteArchivedDocumentFile(String fileName) {
        try {
            File documentDirectory = PathValidationUtils.resolveConfiguredDirectory(
                    CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"),
                    "DOCUMENT_DIR");
            String safeFileName = PathValidationUtils.validateStrictFileName(fileName);
            File archiveFile = PathValidationUtils.validatePath(safeFileName, documentDirectory);
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
