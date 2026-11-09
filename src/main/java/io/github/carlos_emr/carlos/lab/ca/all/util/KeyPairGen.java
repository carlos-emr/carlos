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


package io.github.carlos_emr.carlos.lab.ca.all.util;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.OscarKeyDao;
import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.model.OscarKey;
import io.github.carlos_emr.carlos.commn.model.PublicKey;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public final class KeyPairGen {

    private static Logger logger = MiscUtils.getLogger();

    /**
     * Creates a new instance of KeyPairGen
     */
    private KeyPairGen() {
        // utility class needs no instantiation
    }

    /**
     * Create a key pair for the specified service and store it in the database
     */
    public static String createKeys(String name, String type) {
        byte[] pubKey;
        byte[] privKey;
        Base64 base64 = new Base64();

        // Generate an RSA key (2048-bit minimum per NIST SP 800-131A)
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair key = keyGen.generateKeyPair();

            pubKey = base64.encode(key.getPublic().getEncoded());
            privKey = base64.encode(key.getPrivate().getEncoded());

            if (name.equals("oscar")) {
                // the primary key is name, therefore this statement will only
                // be able to run once and the oscar key pair will not be overwritten

                OscarKeyDao oscarKeyDao = (OscarKeyDao) SpringUtils.getBean(OscarKeyDao.class);
                OscarKey oscarKey = new OscarKey();
                oscarKey.setName("oscar");
                oscarKey.setPublicKey(new String(pubKey, MiscUtils.DEFAULT_UTF8_ENCODING));
                oscarKey.setPrivateKey(new String(privKey, MiscUtils.DEFAULT_UTF8_ENCODING));

                oscarKeyDao.persist(oscarKey);

                // no keys need to be returned so return "success" instead to 
                // indicate the operation completed successfully
                return ("success");
            } else {

                PublicKeyDao publicKeyDao = (PublicKeyDao) SpringUtils.getBean(PublicKeyDao.class);
                PublicKey publicKeyObject = new PublicKey();
                publicKeyObject.setService(name);
                publicKeyObject.setType(type);
                publicKeyObject.setBase64EncodedPublicKey(new String(pubKey, MiscUtils.DEFAULT_UTF8_ENCODING));
                publicKeyObject.setBase64EncodedPrivateKey(new String(privKey, MiscUtils.DEFAULT_UTF8_ENCODING));

                publicKeyDao.persist(publicKeyObject);

                return (publicKeyObject.getBase64EncodedPrivateKey());
            }

        } catch (NoSuchAlgorithmException e) {
            logger.error("Could not create RSA key", e);
            return null;
        } catch (Exception e) {
            logger.error("Could not save keys", e);
            return null;
        }

    }


    /**
     * Retrieve oscars public key and return it as a string
     */
    public static String getPublic() {
        try {
            OscarKeyDao oscarKeyDao = (OscarKeyDao) SpringUtils.getBean(OscarKeyDao.class);
            OscarKey oscarKey = oscarKeyDao.find("oscar");

            return (oscarKey.getPublicKey());
        } catch (Exception e) {
            logger.error("Could not get public key", e);
            return null;
        }

    }

    /**
     * Check if the specified service name has already been used to create a key
     * pair, return 'true' if it has and 'false' otherwise.
     */
    public static boolean checkName(String name) {
        if (name.equals("oscar")) {
            OscarKeyDao oscarKeyDao = (OscarKeyDao) SpringUtils.getBean(OscarKeyDao.class);
            OscarKey oscarKey = oscarKeyDao.find("oscar");
            return (oscarKey != null);
        } else {
            PublicKeyDao publicKeyDao = (PublicKeyDao) SpringUtils.getBean(PublicKeyDao.class);
            PublicKey publicKeyObject = publicKeyDao.find(name);
            return (publicKeyObject != null);
        }
    }

    /**
     * Outcome of {@link #createClientKeys(String, String)}.
     *
     * @since 2026-11-09
     */
    public enum ClientKeyStatus {
        /** The client key pair was stored, and both keys the client needs are in the result. */
        CREATED,
        /** The CARLOS ("oscar") key pair has not been created yet. Nothing was stored. */
        CARLOS_KEY_MISSING,
        /** The CARLOS key could not be read, or the client key pair could not be generated or stored. */
        FAILED
    }

    /**
     * Result of {@link #createClientKeys(String, String)}. The two keys are set only when
     * {@code status} is {@link ClientKeyStatus#CREATED}.
     *
     * @param status           what happened
     * @param clientPrivateKey the client service's base64 private key, or {@code null}
     * @param carlosPublicKey  the CARLOS base64 public key the client encrypts uploads with, or {@code null}
     * @since 2026-11-09
     */
    public record ClientKeys(ClientKeyStatus status, String clientPrivateKey, String carlosPublicKey) {

        /** Leaves the private key out, so a logged or debugged result never prints it. */
        @Override
        public String toString() {
            return "ClientKeys[status=" + status + "]";
        }
    }

    /**
     * Create and store the key pair for a client service (a lab or eData uploader), but only when
     * the CARLOS ("oscar") key pair already exists.
     *
     * <p>The client needs two keys: its own private key and the CARLOS public key. The CARLOS key
     * is read first, and nothing is generated or stored without it. A stored client key takes its
     * service name for good: a retry with the same name is refused as a duplicate, and the Key
     * Manager cannot delete a key. Storing the client key before finding the CARLOS key missing
     * therefore left a name that could be neither retried nor removed (issue #6009).</p>
     *
     * @param name the client service name; must not be {@code "oscar"}, which names the CARLOS key pair
     * @param type the lab type the service uploads
     * @return the outcome, never {@code null}; on {@link ClientKeyStatus#CREATED} it carries the client's
     *         private key and the CARLOS public key
     * @throws IllegalArgumentException if {@code name} is {@code "oscar"}
     * @since 2026-11-09
     */
    public static ClientKeys createClientKeys(String name, String type) {
        if ("oscar".equals(name)) {
            throw new IllegalArgumentException("The 'oscar' service name is the CARLOS key pair, not a client service");
        }

        OscarKey carlosKey;
        try {
            OscarKeyDao oscarKeyDao = (OscarKeyDao) SpringUtils.getBean(OscarKeyDao.class);
            carlosKey = oscarKeyDao.find("oscar");
        } catch (RuntimeException e) {
            // A lookup failure is not a missing key: telling the admin to create a key pair that
            // may already exist would send them the wrong way.
            logger.error("Could not read the CARLOS key pair", e);
            return new ClientKeys(ClientKeyStatus.FAILED, null, null);
        }
        if (carlosKey == null) {
            return new ClientKeys(ClientKeyStatus.CARLOS_KEY_MISSING, null, null);
        }
        String carlosPublicKey = carlosKey.getPublicKey();
        if (carlosPublicKey == null || carlosPublicKey.isBlank()) {
            // The row exists, so it cannot be created again from the page; handing the client an
            // empty public key would only move the failure to its first upload.
            logger.error("The CARLOS key pair is stored without a public key");
            return new ClientKeys(ClientKeyStatus.FAILED, null, null);
        }

        String clientPrivateKey = createKeys(name, type);
        if (clientPrivateKey == null) {
            return new ClientKeys(ClientKeyStatus.FAILED, null, null);
        }
        return new ClientKeys(ClientKeyStatus.CREATED, clientPrivateKey, carlosPublicKey);
    }
}
