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
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    /**
     * Deliberately non-specific outcome for a message the receiver refuses before it can
     * attribute it to a sender. It must not distinguish "no such service" from "key unusable"
     * or "undecryptable", so a caller cannot probe which services are configured.
     */
    private static final String OUTCOME_REJECTED = "rejected";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    protected static Logger logger = MiscUtils.getLogger();

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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

        // getClientInfo() returns an empty list when the service is unknown or its stored key
        // cannot be parsed. Reading element 0 in that state threw out of execute(), and the lab
        // package maps java.lang.Exception to errorpage.jsp — a JSP forward, which renders HTTP
        // 200. Senders using use_http_response_code therefore read a misconfigured or retired
        // service as a successful delivery and silently drop results. Reject it explicitly.
        ArrayList<Object> clientInfo = getClientInfo(service);
        if (clientInfo.size() < 2) {
            logger.warn("Rejected lab upload: no usable sender public key for the requested service");
            return respond(OUTCOME_REJECTED, "", HttpServletResponse.SC_BAD_REQUEST);
        }
        PublicKey clientKey = (PublicKey) clientInfo.get(0);
        String type = (String) clientInfo.get(1);

        try {
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

            InputStream decrypted = decryptMessage(Files.newInputStream(importFile.toPath()), key, clientKey);
            if (decrypted == null) {
                // decryptMessage() logs the cause and returns null; do not tell the caller which
                // stage failed. Previously this NPE'd downstream and surfaced as a 500.
                logger.warn("Rejected lab upload: message could not be decrypted");
                return respond(OUTCOME_REJECTED, audit, HttpServletResponse.SC_BAD_REQUEST);
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
                try (InputStream in = decrypted) {
                    Files.copy(in, staged.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                if (!validateSignature(clientKey, signature, staged)) {
                    logger.info("failed to validate");
                    return respond("validation failed", audit, HttpServletResponse.SC_NOT_ACCEPTABLE);
                }
                logger.debug("Validated Successfully");

                // Verified: only now may the plaintext become a document. fileName is still
                // derived from the upload, so stored names are unchanged from before this fix.
                String filePath;
                try (InputStream verified = Files.newInputStream(staged.toPath())) {
                    filePath = type.equals("PDFDOC")
                            ? Utilities.savePdfFile(verified, fileName)
                            : Utilities.saveFile(verified, fileName);
                }
                File file = PathValidationUtils.validateExistingPath(new File(filePath), PathValidationUtils.resolveConfiguredDirectory(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR"));

                MessageHandler msgHandler = HandlerClassFactory.getHandler(type);

                if (type.equals("HHSEMR") && CarlosProperties.getInstance().getProperty("lab.hhsemr.filter_ordering_provider", "false").equals("true")) {
                    logger.info("Applying filter to HHS EMR lab");
                    String hl7Data = FileUtils.readFileToString(file, "UTF-8");
                    HHSEmrDownloadHandler filterHandler = new HHSEmrDownloadHandler();
                    filterHandler.init(hl7Data);
                    OtherId providerOtherId = OtherIdManager.searchTable(OtherIdManager.PROVIDER, "STAR", filterHandler.getClientRef());
                    if (providerOtherId == null) {
                        logger.info("Filtering out this message, as we don't have client ref " + filterHandler.getClientRef() + " in our database (" + file + ")");
                        return respond("uploaded", audit, HttpServletResponse.SC_OK);
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
                Files.deleteIfExists(staged.toPath());
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
     * @param audit    handler audit string; null is normalized to empty for the view
     * @param httpCode status to send when {@code use_http_response_code} is present
     * @return {@link #NONE} once the response has been written, otherwise {@link #SUCCESS}
     */
    private String respond(String outcome, String audit, int httpCode) {
        request.setAttribute(REQUEST_ATTRIBUTE_OUTCOME, outcome);
        request.setAttribute(REQUEST_ATTRIBUTE_AUDIT, audit == null ? "" : audit);

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

    /*
     * Decrypt the encrypted message and return the original version of the message as an InputStream
     */
    public static InputStream decryptMessage(InputStream is, String skey, PublicKey pkey) {

        // Decrypt the secret key and the message
        try {

            // retrieve the servers private key
            PrivateKey key = getServerPrivate();

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
            // so this path has no ciphertext integrity. It is deliberately left unsuppressed:
            // code scanning alerts 6904 and 5637 must stay open until the legacy format is
            // removed, because the senders — not this receiver — dictate the wire format.
            // Migration contract and sender coordination gates:
            // docs/security/lab-upload-authenticated-encryption-migration.md
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

    /*
     * Check that the signature 'sigString' matches the message InputStream 'msgIS' thus verifying that the message has not been altered.
     */
    public static boolean validateSignature(PublicKey key, String sigString, File input) {
        byte[] buf = new byte[1024];

        try {

            try (InputStream msgIs = new FileInputStream(input)) {
                // MD5WithRSA is required by the external lab upload protocol for signature
                // verification. Do not change without coordinating with all lab data senders.
                Signature sig = Signature.getInstance("MD5WithRSA"); // nosemgrep: java.lang.security.audit.crypto.weak-hash -- external lab protocol requirement
                sig.initVerify(key);

                // Read in the message bytes and update the signature
                int numRead = 0;
                while ((numRead = msgIs.read(buf)) >= 0) {
                    sig.update(buf, 0, numRead);
                }

                return (sig.verify(Base64.decodeBase64(sigString)));
            }

        } catch (Exception e) {
            logger.debug("Could not validate signature: " + e);
            MiscUtils.getLogger().error("Error", e);
            return (false);
        }
    }

    /*
     * Retrieve the clients public key from the database
     */
    public static ArrayList<Object> getClientInfo(String service) {

        PublicKey Key = null;
        String keyString = "";
        String type = "";
        byte[] publicKey;
        ArrayList<Object> info = new ArrayList<Object>();

        try {
            PublicKeyDao publicKeyDao = (PublicKeyDao) SpringUtils.getBean(PublicKeyDao.class);
            io.github.carlos_emr.carlos.commn.model.PublicKey publicKeyObject = publicKeyDao.find(service);

            if (publicKeyObject != null) {
                keyString = publicKeyObject.getBase64EncodedPublicKey();
                type = publicKeyObject.getType();
            }

            publicKey = Base64.decodeBase64(keyString);
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(publicKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            Key = keyFactory.generatePublic(pubKeySpec);

            info.add(Key);
            info.add(type);

        } catch (Exception e) {
            logger.error("Could not retrieve private key: ", e);
        }
        return (info);
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
