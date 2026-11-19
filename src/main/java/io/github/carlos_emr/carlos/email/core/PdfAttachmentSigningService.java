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
package io.github.carlos_emr.carlos.email.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Cryptographically signs the PDF attachments of an outgoing email when PDF signing is
 * configured (carlos-emr/carlos#3216).
 *
 * <p>Owns the whole per-send sequence that used to live in {@code EmailManager}: read the
 * configuration, build each attachment's document manifest, sign, adopt the signed copy into the
 * send's working directory, audit it, and delete a signed copy nothing took ownership of
 * (carlos-emr/carlos#3851). {@code EmailManager} calls {@link #signAttachments} once per send,
 * after optional password encryption and before the sender is built, so the signature covers
 * the exact bytes that are archived and dispatched.</p>
 *
 * <p>Signing is fail-closed. If it is enabled and any attachment cannot be signed, the whole send
 * is refused with {@link EmailSendingException} rather than delivering a partially signed or
 * unsigned set. Every failure, checked or not, is reported that way, so the caller can record a
 * definite failure instead of leaving the email log pending.</p>
 *
 * @since 2026-11-19
 */
@Service
public class PdfAttachmentSigningService {

    /**
     * The audit action recorded for each signed attachment. Kept from when signing lived in
     * {@code EmailManager}: the operations guide documents it and audit searches rely on it.
     */
    static final String AUDIT_ACTION = "EmailManager.signAttachment";
    /** The content type of the audit entry, shared with the other email audit entries. */
    static final String AUDIT_CONTENT = "Email";
    /** The only failure text a send records, whatever went wrong while signing. */
    static final String SIGNING_FAILURE_MESSAGE = "Failed to sign email PDF attachment";

    private static final Logger logger = MiscUtils.getLogger();

    private final EmailDocumentManifestComposer documentManifestComposer;
    private final PdfSignatureService pdfSignatureService;

    /**
     * @param documentManifestComposer builds the document manifest embedded with each signature
     * @param pdfSignatureService reads the signing configuration and applies the signature
     */
    public PdfAttachmentSigningService(EmailDocumentManifestComposer documentManifestComposer,
            PdfSignatureService pdfSignatureService) {
        this.documentManifestComposer = documentManifestComposer;
        this.pdfSignatureService = pdfSignatureService;
    }

    /**
     * Signs every attachment on {@code emailData} when PDF signing is enabled, and does nothing
     * otherwise.
     *
     * <p>Each attachment is expected to be a signable PDF. An encrypted send's attachments are
     * opened with the send password, the only password that can modify them. Each signed PDF
     * replaces its attachment's path and is adopted into the send's working directory, which owns
     * its cleanup. The compose flow always supplies that directory and the send decides who closes
     * it; only a caller that builds {@code EmailData} directly arrives without one, and then it is
     * created here, never closed here.</p>
     *
     * <p>With a send context, each attachment that comes from a CARLOS record gets that record's
     * document manifest in the same update as its signature when
     * {@code pdf.signing.document_id.secret} is set (carlos-emr/carlos#4691), and each signed
     * attachment gets an audit entry naming the record, the signing key and the document ID.
     * Without one, no manifest is embedded and nothing is audited.</p>
     *
     * @param emailData the send's final attachment list, password and working directory
     * @param loggedInInfo the sending provider's session, or {@code null} for no manifests or audit
     * @param emailLog the send's email log, holding the patient and the sending provider, or
     *        {@code null} for no manifests or audit
     * @throws EmailSendingException if signing is enabled and any attachment cannot be signed,
     *         its manifest cannot be built, or the signing key cannot be read for the audit
     */
    // FindSecBugs PATH_TRAVERSAL_IN: attachment paths are server-generated or server-resolved, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "attachment paths are server-generated or server-resolved, not user-controllable input")
    public void signAttachments(EmailData emailData, LoggedInInfo loggedInInfo, EmailLog emailLog)
            throws EmailSendingException {
        List<EmailAttachment> attachments = emailData.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        PDFSigningConfig signingConfig = pdfSignatureService.currentConfiguration();
        if (signingConfig == null || !signingConfig.isEnabled()) {
            return;
        }

        ensureWorkingDirectory(emailData);
        // An encrypted PDF can only be modified with its owner password.
        String ownerPassword = emailData.getIsEncrypted() ? emailData.getPassword() : null;
        EmailDocumentManifestComposer.AttachmentManifests manifests = documentManifests(loggedInInfo, emailLog);
        String keyId = signingKeyIdForAudit(signingConfig, loggedInInfo, emailLog);
        for (EmailAttachment attachment : attachments) {
            Path signedPDFPath = null;
            try {
                Path attachmentPDFPath = PathValidationUtils.resolveTrustedPath(new File(attachment.getFilePath())).toPath();
                DocumentManifest manifest = manifests.forAttachment(attachment);
                signedPDFPath = pdfSignatureService.sign(attachmentPDFPath, signingConfig, ownerPassword, manifest);
                boolean attachmentEncrypted = false;
                if (hasSendContext(loggedInInfo, emailLog)) {
                    // A protected message PDF can share a send with clear document attachments.
                    // Read the signed bytes, not either send-level intent flag, for this audit row.
                    try (PDDocument signed = Loader.loadPDF(signedPDFPath.toFile(), ownerPassword == null ? "" : ownerPassword)) {
                        attachmentEncrypted = signed.isEncrypted();
                    }
                }
                attachment.setFilePath(emailData.getWorkingDirectory().adoptGeneratedPdf(signedPDFPath).toString());
                auditSignedAttachment(loggedInInfo, emailLog, attachment, keyId, manifest, attachmentEncrypted);
            } catch (IOException | RuntimeException e) {
                // Any RuntimeException, not a chosen few: one that escaped would skip the send's
                // failure handling and strand the EmailLog at PENDING behind a 500.
                // Holds the patient's document, and nothing else would ever delete it.
                EmailComposeWorkingDirectory.discardUnadoptedPdf(signedPDFPath);
                // The cause chain is what tells an operator whether the keystore, its password or
                // the certificate is at fault. It names server paths, never the attachment: the
                // attachment's own file name can identify a patient, so it is left out.
                logger.error("Failed to sign an email PDF attachment", e);
                throw new EmailSendingException(SIGNING_FAILURE_MESSAGE, e);
            }
        }
    }

    private static boolean hasSendContext(LoggedInInfo loggedInInfo, EmailLog emailLog) {
        return loggedInInfo != null && emailLog != null;
    }

    private EmailDocumentManifestComposer.AttachmentManifests documentManifests(LoggedInInfo loggedInInfo,
            EmailLog emailLog) throws EmailSendingException {
        if (!hasSendContext(loggedInInfo, emailLog)) {
            return EmailDocumentManifestComposer.AttachmentManifests.NONE;
        }
        try {
            return documentManifestComposer.forSend(loggedInInfo, emailLog);
        } catch (RuntimeException e) {
            // Usually an invalid pdf.signing.document_id.secret. The message names the setting,
            // never the patient or the document.
            logger.error("Failed to prepare the document manifests for an email's signed PDFs", e);
            throw new EmailSendingException(SIGNING_FAILURE_MESSAGE, e);
        }
    }

    /**
     * The signing key's ID for the audit entries, or {@code null} when the send has no context to
     * audit under. Read once per send; an unreadable keystore fails the send here, as signing would.
     */
    private String signingKeyIdForAudit(PDFSigningConfig signingConfig, LoggedInInfo loggedInInfo,
            EmailLog emailLog) throws EmailSendingException {
        if (!hasSendContext(loggedInInfo, emailLog)) {
            return null;
        }
        try {
            return pdfSignatureService.signingKeyId(signingConfig);
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to read the PDF signing key for an email's signed PDFs", e);
            throw new EmailSendingException(SIGNING_FAILURE_MESSAGE, e);
        }
    }

    /**
     * Records that CARLOS signed one attachment: the email, the record it came from, the signing
     * key and the embedded document ID and version, if any. The document ID is an opaque HMAC, so
     * the entry is how a document ID is traced back to its record. No names or content. Like
     * other audit entries it is written in the background ({@link LogAction#addLog}): a failure to
     * store it is logged and does not stop the send.
     */
    private static void auditSignedAttachment(LoggedInInfo loggedInInfo, EmailLog emailLog, EmailAttachment attachment,
            String keyId, DocumentManifest manifest, boolean encrypted) {
        if (!hasSendContext(loggedInInfo, emailLog)) {
            return;
        }
        String data = "emailLogId=" + emailLog.getId()
                + "&source=" + (attachment.getDocumentType() == null ? "" : attachment.getDocumentType().getName())
                + "&recordId=" + attachment.getDocumentId()
                + "&kid=" + keyId
                + "&documentId=" + (manifest == null ? "" : manifest.documentId())
                + "&version=" + (manifest == null ? "" : String.valueOf(manifest.version()))
                + "&encrypted=" + encrypted;
        String demographicNo = emailLog.getDemographic() == null ? null
                : String.valueOf(emailLog.getDemographic().getDemographicNo());
        // contentId is limited to 80 characters; keep the full tracing payload in log.data.
        LogAction.addLog(loggedInInfo, AUDIT_ACTION, AUDIT_CONTENT,
                String.valueOf(emailLog.getId()), demographicNo, data);
    }

    private static void ensureWorkingDirectory(EmailData emailData) throws EmailSendingException {
        if (emailData.getWorkingDirectory() != null) {
            return;
        }
        try {
            emailData.setWorkingDirectory(EmailComposeWorkingDirectory.create());
        } catch (IOException e) {
            throw new EmailSendingException("Unable to create secure email working directory", e);
        }
    }
}
