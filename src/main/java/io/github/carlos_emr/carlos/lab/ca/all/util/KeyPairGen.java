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
}
