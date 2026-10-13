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


/*
 * LabUpload2Action.java
 *
 * Created on June 12, 2007, 2:31 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.OscarKeyDao;
import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.model.OscarKey;
import io.github.carlos_emr.carlos.commn.model.OtherId;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.HHSEmrDownloadHandler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class LabUpload2Action extends ActionSupport implements UploadedFilesAware {
    private static final String REQUEST_ATTRIBUTE_AUDIT = "audit";
    private static final String REQUEST_ATTRIBUTE_OUTCOME = "outcome";
    private static final String OUTCOME_EXCEPTION = "exception";
    // Sender-visible DRAFT classification: actual sender retry behavior is not established.
    private static final String OUTCOME_REJECTED = "rejected";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    protected static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }
        if (uploadValidationError != null) {
            addActionError(uploadValidationError);
            return respond(OUTCOME_EXCEPTION, "", HttpServletResponse.SC_BAD_REQUEST);
        }

        String signature = request.getParameter("signature");
        String key = request.getParameter("key");
        String service = request.getParameter("service");
        String outcome = "";
        String audit = "";
        Integer httpCode = 200;

        // The decrypted copy this request wrote to DOCUMENT_DIR. Until it is handed to the store
        // step (which then owns its cleanup), a failure or rejection here removes it: nothing
        // references it, and each sender retry writes a new copy.
        File saved = null;
        boolean savedHandedOff = false;
        File staged = null;
        try {
            // Unknown/blank service and receiver key-store faults now take explicit responses.
            // This changes body-mode HTML500 versus XML200 behavior and needs sender approval.
            ArrayList<Object> clientInfo = getClientInfo(service);
            if (clientInfo.size() < 2) {
                return respond(OUTCOME_REJECTED, audit, HttpServletResponse.SC_BAD_REQUEST);
            }
            PublicKey clientKey = (PublicKey) clientInfo.get(0);
            String type = (String) clientInfo.get(1);

            // Validate the uploaded file to prevent path traversal attacks
            if (importFile == null) {
                logger.error("No file provided for upload");
                return respond(OUTCOME_EXCEPTION, audit, HttpServletResponse.SC_BAD_REQUEST);
            }

            // Validate file is from an allowed temp directory
            try {
                importFile = PathValidationUtils.validateUpload(importFile);
            } catch (SecurityException e) {
                logger.error("Invalid upload source - potential path traversal");
                return respond(OUTCOME_EXCEPTION, audit, HttpServletResponse.SC_FORBIDDEN);
            }

            String fileName = importFile.getName();
            // Authenticate a request-owned temporary copy before writing into DOCUMENT_DIR.
            // createSecureTempFile is owner-only on POSIX; its non-POSIX fallback uses the
            // filesystem defaults. Writing in place below retains the POSIX permissions.
            staged = PathValidationUtils.createSecureTempFile("LabUploadVerify", ".tmp");
            if (staged.getCanonicalFile().toPath().startsWith(
                    PathValidationUtils.getRequiredDocumentDirectory().getCanonicalFile().toPath())) {
                throw new IOException("Lab verification temporary file must be outside DOCUMENT_DIR");
            }
            try (InputStream encrypted = PathValidationUtils.openValidatedUploadInputStream(importFile)) {
                InputStream decrypted = decryptMessage(encrypted, key, clientKey);
                if (decrypted == null) {
                    return respond(OUTCOME_REJECTED, audit, HttpServletResponse.SC_BAD_REQUEST);
                }
                try {
                    stageDecrypted(decrypted, staged);
                } catch (IOException e) {
                    if (e.getCause() instanceof BadPaddingException
                            || e.getCause() instanceof IllegalBlockSizeException) {
                        return respond(OUTCOME_REJECTED, audit, HttpServletResponse.SC_BAD_REQUEST);
                    }
                    throw e;
                }
            }

            boolean signatureValid = validateSignature(clientKey, signature, staged);
            if (signatureValid) {
                String savedPath;
                try (InputStream verified = new BufferedInputStream(Files.newInputStream(staged.toPath()))) {
                    savedPath = type.equals("PDFDOC")
                            ? Utilities.savePdfFile(verified, fileName) : Utilities.saveFile(verified, fileName);
                }
                if (savedPath == null) {
                    throw new IOException("Lab file save returned no path");
                }
                saved = PathValidationUtils.validateExistingDocumentPath(savedPath);
                // DRAFT fault classification: compare the actual saved bytes before parsing.
                // The caller's existing pre-handoff cleanup owns this file on mismatch/read fault.
                if (Files.mismatch(staged.toPath(), saved.toPath()) != -1L) {
                    throw new IOException("Stored lab upload differs from the verified staged copy");
                }
                signatureValid = validateSignature(clientKey, signature, saved);
            }

            if (signatureValid) {
                File file = saved;
                String filePath = file.getPath();
                logger.debug("Validated Successfully");
                MessageHandler msgHandler = HandlerClassFactory.getHandler(type);

                if (type.equals("HHSEMR") && CarlosProperties.getInstance().getProperty("lab.hhsemr.filter_ordering_provider", "false").equals("true")) {
                    logger.info("Applying filter to HHS EMR lab");
                    String hl7Data = FileUtils.readFileToString(file, "UTF-8");
                    HHSEmrDownloadHandler filterHandler = new HHSEmrDownloadHandler();
                    filterHandler.init(hl7Data);
                    OtherId providerOtherId = OtherIdManager.searchTable(OtherIdManager.PROVIDER, "STAR", filterHandler.getClientRef());
                    if (providerOtherId == null) {
                        logger.info("Filtering out HHS EMR lab: client reference has no matching provider");
                        // Retain acknowledgement, absent audit and saved-file lifetime.
                        savedHandedOff = true;
                        return respond("uploaded", null, HttpServletResponse.SC_OK);
                    }
                }


                java.util.concurrent.atomic.AtomicReference<String> parsedAudit = new java.util.concurrent.atomic.AtomicReference<>();
                // A failed duplicate lookup throws LookupFailedException, answered 500 below: a
                // database fault must never be answered "uploaded previously", which senders treat
                // as delivered.
                File documentDir = PathValidationUtils.getRequiredDocumentDirectory();
                // From here storeSavedFileIfNew owns the file: it removes it unless a stored lab may use it.
                savedHandedOff = true;
                FileUploadCheck.StoreOutcome stored = FileUploadCheck.storeSavedFileIfNew(file,
                        documentDir, file.getName(), "0", checksumId -> {
                            if (msgHandler == null) {
                                return false;
                            }
                            parsedAudit.set(msgHandler.parse(loggedInInfo, service, file.getPath(),
                                    checksumId, request.getRemoteAddr()));
                            return parsedAudit.get() != null;
                        });
                if (stored == FileUploadCheck.StoreOutcome.STORED) {
                    audit = parsedAudit.get();
                    outcome = "uploaded";
                    httpCode = HttpServletResponse.SC_OK;
                } else if (stored == FileUploadCheck.StoreOutcome.ALREADY_RECORDED) {
                    outcome = "uploaded previously";
                    httpCode = HttpServletResponse.SC_CONFLICT;
                } else {
                    outcome = "upload failed";
                    httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                }
            } else {
                logger.info("failed to validate");
                outcome = "validation failed";
                httpCode = HttpServletResponse.SC_NOT_ACCEPTABLE;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            outcome = OUTCOME_EXCEPTION;
            httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        } finally {
            // This never owns the saved document. The current store/rollback logic does.
            deleteStaged(staged);
        }
        if (saved != null && !savedHandedOff) {
            discardSaved(saved);
        }
        return respond(outcome, audit, httpCode);
    }

    // Sender-visible DRAFT response policy. Preserve the release's normal successful view:
    // sendError(200) would dispatch to errorpage.jsp and become500 (#4086).
    private String respond(String outcome, String audit, int httpCode) {
        request.setAttribute(REQUEST_ATTRIBUTE_OUTCOME, outcome);
        request.setAttribute(REQUEST_ATTRIBUTE_AUDIT, audit);
        if (request.getParameter("use_http_response_code") != null) {
            if (httpCode < HttpServletResponse.SC_BAD_REQUEST) {
                response.setStatus(httpCode);
                return SUCCESS;
            }
            try {
                response.sendError(httpCode, outcome);
            } catch (IOException e) {
                logger.error("Error", e);
            }
            return null;
        }
        return SUCCESS;
    }

    public LabUpload2Action() {
    }

    /** Writes into the already-created temporary file without recreating its permissions. */
    static void stageDecrypted(InputStream decrypted, File staged) throws IOException {
        try (InputStream in = decrypted;
                OutputStream out = Files.newOutputStream(staged.toPath(),
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            in.transferTo(out);
        }
    }

    // A cleanup failure must not replace the existing response or delete a stored document.
    private static void deleteStaged(File staged) {
        if (staged == null) return;
        try {
            Files.deleteIfExists(staged.toPath());
        } catch (IOException | RuntimeException e) {
            logger.error("Could not remove the staged lab upload {}; remove this temporary copy: {}",
                    LogSafe.sanitize(staged.getAbsolutePath()), LogSafe.exceptionTrace(e));
        }
    }

    // Best effort: the outcome is already decided and a failed delete is logged by the helper.
    private static void discardSaved(File saved) {
        try {
            FileUploadCheck.discardUnreferenced(saved, PathValidationUtils.getRequiredDocumentDirectory());
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not resolve DOCUMENT_DIR to remove an unstored lab upload: {}",
                    LogSafe.exceptionTrace(e));
        }
    }

    /*
     * Decrypt the encrypted message and return the original version of the message as an InputStream
     */
    // ECB_MODE / CIPHER_INTEGRITY: the payload cipher below is the one the external lab
    // senders encrypt with, and this receiver only decrypts. Substituting an authenticated
    // mode here unilaterally would reject every message those senders produce, so the
    // migration to a versioned AES-GCM format is coordinated in issue #3413 (which names a
    // local replacement as an explicit non-goal). The finding is accepted and tracked there,
    // not dismissed: remove this suppression together with the legacy format.
    @SuppressFBWarnings(value = {"ECB_MODE", "CIPHER_INTEGRITY"}, justification = "legacy lab upload transport format dictated by external senders; decrypt-only receiver, authenticated-encryption migration tracked in issue #3413")
    public static InputStream decryptMessage(InputStream is, String skey, PublicKey pkey) {

        // Keep receiver-key unavailability separate from an undecryptable envelope.
        // This classification does not establish that any vendor will retry or reject it.
        PrivateKey key = getServerPrivate();
        if (key == null) {
            throw new IllegalStateException("Receiver private key is unavailable");
        }
        // Decode/unwrap errors caused by the envelope are sender failures. Factory,
        // configuration and valid-key initialization failures belong to the receiver.
        byte[] wrappedKey = Base64.decodeBase64(skey);
        if (wrappedKey == null || wrappedKey.length == 0) return null;
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); // NOPMD HardCodedCryptoKey — JCA name, not key material // nosemgrep: java.lang.security.audit.crypto.ecb-cipher.ecb-cipher -- "ECB" is JCA convention for RSA single-block, not AES-ECB mode; PKCS#1v1.5 constraint documented above
            cipher.init(Cipher.DECRYPT_MODE, key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Receiver wrapping cipher is unavailable", e);
        }

        byte[] newSecretKey;
        try {
            newSecretKey = cipher.doFinal(wrappedKey);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            return null;
        }
        // Legacy AES supports these encoded key sizes. A malformed unwrapped key is
        // rejected before a provider init fault could be mistaken for invalid sender input.
        if (newSecretKey == null || (newSecretKey.length != 16
                && newSecretKey.length != 24 && newSecretKey.length != 32)) return null;
        SecretKeySpec skeySpec = new SecretKeySpec(newSecretKey, "AES");
        Cipher msgCipher;
        try {
            msgCipher = Cipher.getInstance("AES");
            msgCipher.init(Cipher.DECRYPT_MODE, skeySpec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Receiver message cipher is unavailable", e);
        }
        // Unexpected provider RuntimeExceptions also propagate as receiver faults.
        return new BufferedInputStream(new CipherInputStream(is, msgCipher));
    }

    /*
     * Check that the signature 'sigString' matches the message InputStream 'msgIS' thus verifying that the message has not been altered.
     */
    public static boolean validateSignature(PublicKey key, String sigString, File input)
            throws IOException, GeneralSecurityException {
        if (sigString == null) return false;
        if (!(key instanceof RSAPublicKey rsaKey)) {
            throw new GeneralSecurityException("Stored sender key is not an RSA verification key");
        }
        byte[] signatureBytes = Base64.decodeBase64(sigString);
        int expectedLength = (rsaKey.getModulus().bitLength() + 7) / 8;
        // SunRsaSign throws SignatureException for the wrong RSA length. Reject malformed
        // bytes here, then preserve actual valid-input provider processing faults as500.
        if (signatureBytes == null || signatureBytes.length != expectedLength) return false;
        // Retain the legacy MD5WithRSA wire algorithm; no protocol migration is implemented.
        Signature sig = Signature.getInstance("MD5WithRSA"); // nosemgrep: java.lang.security.audit.crypto.weak-hash -- external lab protocol requirement
        sig.initVerify(key);
        byte[] buf = new byte[1024];
        try (InputStream msgIs = new FileInputStream(input)) {
            int numRead;
            while ((numRead = msgIs.read(buf)) >= 0) {
                sig.update(buf, 0, numRead);
            }
        }
        return sig.verify(signatureBytes);
    }

    /*
     * Retrieve the clients public key from the database
     */
    public static ArrayList<Object> getClientInfo(String service) {
        ArrayList<Object> info = new ArrayList<Object>();
        if (service == null || service.isBlank()) {
            return info;
        }

        // Not caught here: a DAO failure is a receiver fault and must reach the caller.
        PublicKeyDao publicKeyDao = (PublicKeyDao) SpringUtils.getBean(PublicKeyDao.class);
        io.github.carlos_emr.carlos.commn.model.PublicKey publicKeyObject = publicKeyDao.find(service);
        if (publicKeyObject == null) {
            return info;
        }

        try {
            byte[] publicKey = Base64.decodeBase64(publicKeyObject.getBase64EncodedPublicKey());
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(publicKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            info.add(keyFactory.generatePublic(pubKeySpec));
            info.add(publicKeyObject.getType());
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("The stored public key for this service cannot be parsed", e);
        }
        return info;
    }

    /*
     * Retrieve the servers private key from the database
     */
    private static PrivateKey getServerPrivate() {

        PrivateKey Key = null;
        byte[] privateKey;

        try {
            OscarKeyDao oscarKeyDao = (OscarKeyDao) SpringUtils.getBean(OscarKeyDao.class);
            OscarKey oscarKey = oscarKeyDao.find("oscar");
            privateKey = Base64.decodeBase64(oscarKey.getPrivateKey());
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privateKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            Key = keyFactory.generatePrivate(privKeySpec);
        } catch (Exception e) {
            logger.error("Could not retrieve private key: ", e);
        }
        return (Key);
    }

    private File importFile;
    private String uploadValidationError;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.importFile = PathValidationUtils.validateUploadContent(uploaded.getContent());
            try {
                PathValidationUtils.validateStrictFileName(uploaded.getOriginalName());
            } catch (FileValidationException e) {
                this.uploadValidationError = PathValidationUtils.INVALID_FILENAME_MESSAGE;
            }
        }
    }

    public File getImportFile() {
        return importFile;
    }

    public void setImportFile(File importFile) {
        this.importFile = importFile;
    }
}
