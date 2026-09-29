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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.HHSEmrDownloadHandler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.utility.LogSafe;
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
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class LabUpload2Action extends ActionSupport implements UploadedFilesAware {
    private static final String REQUEST_ATTRIBUTE_AUDIT = "audit";
    private static final String REQUEST_ATTRIBUTE_OUTCOME = "outcome";
    private static final String OUTCOME_EXCEPTION = "exception";

    /**
     * Deliberately non-specific outcome for a message the receiver refuses as the sender's
     * error: an unknown or missing service, or a message that does not decrypt. It must not
     * distinguish those from each other. A message that decrypts but fails its signature is
     * answered separately (406, "validation failed"), as the legacy protocol always has; see the
     * migration document. A receiver fault, including a stored sender key that
     * cannot be parsed, is not a rejection: it is answered as an exception (500) so the sender
     * retries.
     */
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

        try {
            // An unknown or missing service is the sender's error: getClientInfo() returns an
            // empty list and the upload is rejected (400). A failed lookup or a stored key that
            // cannot be parsed is the receiver's fault: getClientInfo() throws, and the catch
            // below answers 500 so the sender retries. The lookup sits inside this try so every
            // receiver fault takes that path through respond().
            ArrayList<Object> clientInfo = getClientInfo(service);
            if (clientInfo.size() < 2) {
                // The service name is sender-supplied, so it stays out of the log. Missing versus
                // unknown is enough to tell an incomplete request from a misconfigured sender.
                logger.warn("Rejected lab upload: {} service",
                        service == null || service.isBlank() ? "missing" : "unknown");
                return respond(OUTCOME_REJECTED, "", HttpServletResponse.SC_BAD_REQUEST);
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
                logger.error("Invalid upload source - potential path traversal: " + importFile.getPath());
                return respond(OUTCOME_EXCEPTION, audit, HttpServletResponse.SC_FORBIDDEN);
            }

            String fileName = importFile.getName();

            // Stage the decrypted message OUTSIDE DOCUMENT_DIR until the sender signature
            // verifies. The wrapping key is the receiver's public key, which every sender holds,
            // so anyone able to reach this action can produce a message that decrypts cleanly.
            // The signature is the only evidence the content is genuine, and persisting before
            // checking it let an unverified message become a stored clinical document that
            // nothing later removed. The staged copy is owner-only: it holds cleartext PHI.
            File staged = PathValidationUtils.createSecureTempFile("LabUploadVerify", ".tmp");
            try {
                // The upload stream is owned here so every exit, including a rejected envelope,
                // closes it; decryptMessage() only wraps it.
                try (InputStream encrypted = PathValidationUtils.openValidatedUploadInputStream(importFile)) {
                    InputStream decrypted = decryptMessage(encrypted, key, clientKey);
                    if (decrypted == null) {
                        // decryptMessage() logs the cause and returns null; do not tell the
                        // caller which stage failed. Previously this NPE'd downstream and
                        // surfaced as a 500.
                        logger.warn("Rejected lab upload: message could not be decrypted");
                        return respond(OUTCOME_REJECTED, audit, HttpServletResponse.SC_BAD_REQUEST);
                    }
                    stageDecrypted(decrypted, staged);
                } catch (IOException e) {
                    if (!(e.getCause() instanceof GeneralSecurityException)) {
                        throw e; // genuine receiver I/O fault: stays a 500 so the sender retries
                    }
                    // CipherInputStream reports a bad AES padding/block lazily, as an IOException
                    // raised mid-copy. It is the same "undecryptable" condition as a failed key
                    // unwrap and must produce the same outcome, not a distinguishable 500.
                    logger.warn("Rejected lab upload: message could not be decrypted");
                    return respond(OUTCOME_REJECTED, audit, HttpServletResponse.SC_BAD_REQUEST);
                }

                if (!validateSignature(clientKey, signature, staged)) {
                    logger.info("failed to validate");
                    return respond("validation failed", audit, HttpServletResponse.SC_NOT_ACCEPTABLE);
                }
                logger.debug("Validated Successfully");

                // Verified: only now may the plaintext become a document. fileName is still
                // derived from the upload, so stored names are unchanged from before this fix.
                // Buffered because Utilities.savePdfFile() reads one byte per call.
                String filePath;
                try (InputStream verified = new BufferedInputStream(Files.newInputStream(staged.toPath()))) {
                    filePath = type.equals("PDFDOC")
                            ? Utilities.savePdfFile(verified, fileName)
                            : Utilities.saveFile(verified, fileName);
                }
                if (filePath == null) {
                    // saveFile/savePdfFile return null when the write failed. Thrown rather than
                    // returned so the failure reaches respond() as a 500, which is what honours
                    // use_http_response_code.
                    throw new IOException("Lab file save returned no path");
                }
                File file = PathValidationUtils.validateExistingDocumentPath(filePath);
                filePath = file.getPath();

                // The signature covered the staged bytes, not this second copy. The save helpers
                // return null on a failed write (handled above), but only this comparison proves
                // that what will be parsed is exactly what was verified.
                if (Files.mismatch(staged.toPath(), file.toPath()) != -1L) {
                    Files.deleteIfExists(file.toPath());
                    throw new IOException("stored lab upload does not match the verified staged copy");
                }

                MessageHandler msgHandler = HandlerClassFactory.getHandler(type);

                if (type.equals("HHSEMR") && CarlosProperties.getInstance().getProperty("lab.hhsemr.filter_ordering_provider", "false").equals("true")) {
                    logger.info("Applying filter to HHS EMR lab");
                    String hl7Data = FileUtils.readFileToString(file, "UTF-8");
                    HHSEmrDownloadHandler filterHandler = new HHSEmrDownloadHandler();
                    filterHandler.init(hl7Data);
                    OtherId providerOtherId = OtherIdManager.searchTable(OtherIdManager.PROVIDER, "STAR", filterHandler.getClientRef());
                    if (providerOtherId == null) {
                        logger.info("Filtering out this message, as we don't have client ref " + filterHandler.getClientRef() + " in our database (" + file + ")");
                        // This path never set the audit attribute, so the view has always
                        // rendered its "failure" default here; keep that wire behavior.
                        return respond("uploaded", null, HttpServletResponse.SC_OK);
                    }
                }

                try (InputStream stored = new FileInputStream(file)) {
                    int check = FileUploadCheck.addFile(file.getName(), stored, "0");
                    if (check != FileUploadCheck.UNSUCCESSFUL_SAVE) {
                        if ((audit = msgHandler.parse(loggedInInfo, service, filePath, check, request.getRemoteAddr())) != null) {
                            outcome = "uploaded";
                            httpCode = HttpServletResponse.SC_OK;
                        } else {
                            outcome = "upload failed";
                            httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                        }
                    } else {
                        outcome = "uploaded previously";
                        httpCode = HttpServletResponse.SC_CONFLICT;
                    }
                }
            } finally {
                deleteStaged(staged);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            outcome = OUTCOME_EXCEPTION;
            httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return respond(outcome, audit, httpCode);
    }

    /**
     * Single exit point for {@link #execute()}.
     *
     * <p>Every terminating path routes through here so that a sender passing
     * {@code use_http_response_code} observes the status the receiver actually recorded.
     * Several early returns previously assigned a status code and then returned SUCCESS,
     * which rendered a 200 page and hid the failure from the sender.
     *
     * @param outcome  short outcome token, also sent as the error message when the caller
     *                 requested HTTP status codes
     * @param audit    handler audit string; passed through unchanged, because the view renders
     *                 a null audit as {@code failure} and senders may read that element
     * @param httpCode status to send when {@code use_http_response_code} is present
     * @return {@link #NONE} once the response has been written, otherwise {@link #SUCCESS}
     */
    private String respond(String outcome, String audit, int httpCode) {
        request.setAttribute(REQUEST_ATTRIBUTE_OUTCOME, outcome);
        request.setAttribute(REQUEST_ATTRIBUTE_AUDIT, audit);

        if (request.getParameter("use_http_response_code") != null) {
            try {
                response.sendError(httpCode, outcome);
            } catch (IOException e) {
                logger.error("Error", e);
            }
            return NONE;
        }
        return SUCCESS;
    }

    public LabUpload2Action() {
    }

    /**
     * Writes the decrypted message into the already-created owner-only staging file.
     *
     * <p>The bytes are written into the existing file on purpose. {@code Files.copy(in, path,
     * REPLACE_EXISTING)} deletes the target and recreates it with default (umask) permissions,
     * which would expose the cleartext PHI to other local users for the life of the request.
     *
     * @param decrypted decrypted message stream; closed by this method
     * @param staged    file from {@link PathValidationUtils#createSecureTempFile(String, String)}
     * @throws IOException on an I/O failure, or wrapping a {@link GeneralSecurityException}
     *                     when the cipher stream rejects the final block
     */
    static void stageDecrypted(InputStream decrypted, File staged) throws IOException {
        try (InputStream in = decrypted;
             OutputStream out = Files.newOutputStream(staged.toPath(),
                     StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            in.transferTo(out);
        }
    }

    /*
     * Decrypt the encrypted message and return the original version of the message as an InputStream.
     * Returns null when the sender's envelope cannot be decrypted. Throws IllegalStateException when
     * the receiver's own private key is unavailable: that is a receiver fault (DB outage, missing
     * oscarKeys row), and reporting it as a sender rejection would stop senders from retrying.
     */
    // ECB_MODE / CIPHER_INTEGRITY: the payload cipher below is the one the external lab
    // senders encrypt with, and this receiver only decrypts. Substituting an authenticated
    // mode here unilaterally would reject every message those senders produce, so the
    // migration to a versioned AES-GCM format is coordinated in issue #3413 (which names a
    // local replacement as an explicit non-goal). The finding is accepted and tracked there,
    // not dismissed: remove this suppression together with the legacy format.
    @SuppressFBWarnings(value = {"ECB_MODE", "CIPHER_INTEGRITY"}, justification = "legacy lab upload transport format dictated by external senders; decrypt-only receiver, authenticated-encryption migration tracked in issue #3413")
    public static InputStream decryptMessage(InputStream is, String skey, PublicKey pkey) {

        // retrieve the servers private key
        PrivateKey key = getServerPrivate();
        if (key == null) {
            throw new IllegalStateException("receiver private key is unavailable");
        }

        // Decrypt the secret key and the message
        try {

            // Decrypt the secret key using the servers private key
            // NOTE: PKCS1Padding (PKCS#1 v1.5) is theoretically vulnerable to Bleichenbacher
            // padding oracle attacks. OAEP padding (RSA/ECB/OAEPWithSHA-256AndMGF1Padding)
            // would be more secure, but this protocol is dictated by the external lab system
            // sender which encrypts with PKCS#1 v1.5. Changing the padding here would break
            // decryption of incoming lab uploads. This is decrypt-only (not encrypt), which
            // limits the attack surface. If the external protocol is ever updated, migrate to OAEP.
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); // NOPMD HardCodedCryptoKey — JCA name, not key material // nosemgrep: java.lang.security.audit.crypto.ecb-cipher.ecb-cipher -- "ECB" is JCA convention for RSA single-block, not AES-ECB mode; PKCS#1v1.5 constraint documented above
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] newSecretKey = cipher.doFinal(Base64.decodeBase64(skey));

            // Decrypt the message using the secret key.
            // The bare "AES" transformation resolves to AES/ECB/PKCS5Padding under SunJCE,
            // so this path has no ciphertext integrity. The senders, not this receiver, dictate
            // the wire format. SpotBugs' ECB_MODE/CIPHER_INTEGRITY findings are suppressed on
            // this method with that reason; SonarCloud's (code scanning alert 5637) stays open
            // until the legacy format is removed. Migration contract and sender coordination
            // gates: docs/security/lab-upload-authenticated-encryption-migration.md
            SecretKeySpec skeySpec = new SecretKeySpec(newSecretKey, "AES");
            Cipher msgCipher = Cipher.getInstance("AES");
            msgCipher.init(Cipher.DECRYPT_MODE, skeySpec);

            is = new CipherInputStream(is, msgCipher);

            // Return the decrypted message
            return (new BufferedInputStream(is));

        } catch (Exception e) {
            logger.error("Could not decrypt the message", e);
            return (null);
        }
    }

    /**
     * Checks that {@code sigString} is the sender's signature over the staged message, so the
     * message has not been altered.
     *
     * <p>False means the sender's error: a missing, malformed or wrong signature. A fault on the
     * receiver's side, such as the staged file failing to read or the signature algorithm being
     * unavailable, is thrown instead, so it is answered 500 and the sender retries rather than
     * treating a valid lab as rejected.</p>
     *
     * @throws IOException if the staged message cannot be read
     * @throws GeneralSecurityException if the signature algorithm is unavailable
     */
    public static boolean validateSignature(PublicKey key, String sigString, File input)
            throws IOException, GeneralSecurityException {
        if (sigString == null) {
            return false;
        }
        // MD5WithRSA is required by the external lab upload protocol for signature
        // verification. Do not change without coordinating with all lab data senders.
        Signature sig = Signature.getInstance("MD5WithRSA"); // nosemgrep: java.lang.security.audit.crypto.weak-hash -- external lab protocol requirement
        try {
            sig.initVerify(key);
        } catch (InvalidKeyException e) {
            throw new GeneralSecurityException("The stored sender key cannot verify a signature", e);
        }
        byte[] buf = new byte[1024];
        try (InputStream msgIs = new FileInputStream(input)) {
            int numRead = 0;
            while ((numRead = msgIs.read(buf)) >= 0) {
                sig.update(buf, 0, numRead);
            }
        }
        try {
            return sig.verify(Base64.decodeBase64(sigString));
        } catch (SignatureException | IllegalArgumentException e) {
            // A signature of the wrong length or encoding: the sender's, so not worth a stack trace.
            logger.warn("Lab upload signature could not be checked ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Removes the staged cleartext. A failure is logged, not thrown: it must not replace the
     * outcome already decided, but a leftover file holds PHI and needs an operator.
     */
    private static void deleteStaged(File staged) {
        try {
            Files.deleteIfExists(staged.toPath());
        } catch (IOException | RuntimeException e) {
            // The staged name is random and holds no patient data, so it is safe to log.
            logger.error("Could not delete the staged lab upload {}; remove it by hand, it holds cleartext ({})",
                    LogSafe.sanitize(staged.getAbsolutePath()), e.getClass().getSimpleName());
        }
    }

    /*
     * Retrieve the sender's public key and message type for a service. An unknown or blank
     * service yields an empty list, which the caller rejects as the sender's error. A lookup
     * failure, or a stored key that cannot be parsed, throws: those are the receiver's faults.
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
            logger.info("oscar key: " + oscarKey);

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
