//CHECKSTYLE:OFF
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
 */


/*
 * LabUpload2Action.java
 *
 * Created on June 12, 2007, 2:31 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.lab.ca.all.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.OtherIdManager;
import ca.openosp.openo.commn.dao.OscarKeyDao;
import ca.openosp.openo.commn.dao.PublicKeyDao;
import ca.openosp.openo.commn.model.OscarKey;
import ca.openosp.openo.commn.model.OtherId;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.ca.all.parsers.HHSEmrDownloadHandler;
import ca.openosp.openo.lab.ca.all.upload.HandlerClassFactory;
import ca.openosp.openo.lab.ca.all.upload.handlers.MessageHandler;
import ca.openosp.openo.lab.ca.all.util.Utilities;
import ca.openosp.openo.utility.PathValidationUtils;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;

public class LabUpload2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    protected static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String signature = request.getParameter("signature");
        String key = request.getParameter("key");
        String service = request.getParameter("service");
        String outcome = "";
        String audit = "";
        Integer httpCode = 200;

        ArrayList<Object> clientInfo = getClientInfo(service);
        PublicKey clientKey = (PublicKey) clientInfo.get(0);
        String type = (String) clientInfo.get(1);

        try {
            // Validate the uploaded file to prevent path traversal attacks
            if (importFile == null) {
                logger.error("No file provided for upload");
                outcome = "exception";
                httpCode = HttpServletResponse.SC_BAD_REQUEST;
                request.setAttribute("outcome", outcome);
                request.setAttribute("audit", audit);
                return SUCCESS;
            }

            // Validate file is from an allowed temp directory
            try {
                importFile = PathValidationUtils.validateUpload(importFile);
            } catch (SecurityException e) {
                logger.error("Invalid upload source - potential path traversal: " + importFile.getPath());
                outcome = "exception";
                httpCode = HttpServletResponse.SC_FORBIDDEN;
                request.setAttribute("outcome", outcome);
                request.setAttribute("audit", audit);
                return SUCCESS;
            }

            InputStream is = decryptMessage(Files.newInputStream(importFile.toPath()), key, clientKey);
            String fileName = importFile.getName();
            String filePath = null;
            if (type.equals("PDFDOC")) {
                filePath = Utilities.savePdfFile(is, fileName);
            } else {
                filePath = Utilities.saveFile(is, fileName);
            }
            File file = new File(filePath);

            if (validateSignature(clientKey, signature, file)) {
                logger.debug("Validated Successfully");
                MessageHandler msgHandler = HandlerClassFactory.getHandler(type);

                if (type.equals("HHSEMR") && OscarProperties.getInstance().getProperty("lab.hhsemr.filter_ordering_provider", "false").equals("true")) {
                    logger.info("Applying filter to HHS EMR lab");
                    String hl7Data = FileUtils.readFileToString(file, "UTF-8");
                    HHSEmrDownloadHandler filterHandler = new HHSEmrDownloadHandler();
                    filterHandler.init(hl7Data);
                    OtherId providerOtherId = OtherIdManager.searchTable(OtherIdManager.PROVIDER, "STAR", filterHandler.getClientRef());
                    if (providerOtherId == null) {
                        logger.info("Filtering out this message, as we don't have client ref " + filterHandler.getClientRef() + " in our database (" + file + ")");
                        outcome = "uploaded";
                        request.setAttribute("outcome", outcome);
                        return SUCCESS;
                    }
                }


                is = new FileInputStream(file);
                try {
                    int check = FileUploadCheck.addFile(file.getName(), is, "0");
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
                } finally {
                    is.close();
                }
            } else {
                logger.info("failed to validate");
                outcome = "validation failed";
                httpCode = HttpServletResponse.SC_NOT_ACCEPTABLE;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            outcome = "exception";
            httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        request.setAttribute("outcome", outcome);
        request.setAttribute("audit", audit);

        if (request.getParameter("use_http_response_code") != null) {
            try {
                response.sendError(httpCode, outcome);
            } catch (IOException e) {
                logger.error("Error", e);
            }
            return (null);
        } else return SUCCESS;
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
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] newSecretKey = cipher.doFinal(Base64.decodeBase64(skey));

            // Decrypt the message using the secret key
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

            InputStream msgIs = new FileInputStream(input);
            Signature sig = Signature.getInstance("MD5WithRSA");
            sig.initVerify(key);

            // Read in the message bytes and update the signature
            int numRead = 0;
            while ((numRead = msgIs.read(buf)) >= 0) {
                sig.update(buf, 0, numRead);
            }
            msgIs.close();

            return (sig.verify(Base64.decodeBase64(sigString)));

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
            ca.openosp.openo.commn.model.PublicKey publicKeyObject = publicKeyDao.find(service);

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

    public File getImportFile() {
        return importFile;
    }

    public void setImportFile(File importFile) {
        this.importFile = importFile;
    }
}
