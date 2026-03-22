/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.utility;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DigitalSignatureDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;

/**
 * Utility class for managing digital signatures in the EMR system.
 *
 * <p>Provides methods for generating signature request IDs, resolving temporary
 * file paths for signature images, and storing captured signatures to the database.
 *
 * @since 2026-03-17
 */
public class DigitalSignatureUtils {

    private static Logger logger = MiscUtils.getLogger();

    public static final String SIGNATURE_REQUEST_ID_KEY = "signatureRequestId";

    /**
     * Generates a unique signature request ID by combining the provider number
     * with the current system time in milliseconds.
     *
     * @param providerNo String the provider number
     * @return String a unique request identifier
     */
    public static String generateSignatureRequestId(String providerNo) {
        return (providerNo + System.currentTimeMillis());
    }

    /**
     * Returns the temporary file path where a signature image is stored during capture.
     *
     * @param signatureRequestId String the signature request identifier
     * @return String the absolute path to the temporary signature JPEG file
     */
    public static String getTempFilePath(String signatureRequestId) {
        String temppath = System.getProperty("java.io.tmpdir");
        Path path = Paths.get(temppath, "signature_" + signatureRequestId + ".jpg");
        return path.toString();
    }

    /**
     * This method will check if digital signatures is enabled or not. It will only attempt to save it if it's enabled.
     *
     * @param demographicId of the owner of this signature
     * @throws FileNotFoundException if missing or error in image when one was expected
     */
    public static DigitalSignature storeDigitalSignatureFromTempFileToDB(LoggedInInfo loggedInInfo, String signatureRequestId, int demographicId) {
        DigitalSignature digitalSignature = null;

        if (loggedInInfo.getCurrentFacility().isEnableDigitalSignatures()) {
            String filename = DigitalSignatureUtils.getTempFilePath(signatureRequestId);
            if (filename == null || filename.isEmpty()) {
                return digitalSignature;
            }
            try (FileInputStream fileInputStream = new FileInputStream(filename)) {
                byte[] image = new byte[1024 * 256];
                fileInputStream.read(image);

                digitalSignature = new DigitalSignature();
                digitalSignature.setDateSigned(new Date());
                digitalSignature.setDemographicId(demographicId);
                digitalSignature.setFacilityId(loggedInInfo.getCurrentFacility().getId());
                digitalSignature.setProviderNo(loggedInInfo.getLoggedInProviderNo());
                digitalSignature.setSignatureImage(image);

                DigitalSignatureDao digitalSignatureDao = SpringUtils.getBean(DigitalSignatureDao.class);
                digitalSignatureDao.persist(digitalSignature);
            } catch (FileNotFoundException e) {
                logger.debug("Signature file not found. User probably didn't collect a signature.", e);
            } catch (Exception e) {
                logger.error("UnexpectedError.", e);
            }
        }

        return digitalSignature;
    }

}
