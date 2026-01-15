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
 */

package ca.openosp.openo.managers;

import ca.openosp.openo.commn.dao.DigitalSignatureDao;
import ca.openosp.openo.commn.model.DigitalSignature;
import ca.openosp.openo.commn.model.enumerator.ModuleType;
import ca.openosp.openo.utility.DigitalSignatureUtils;
import ca.openosp.openo.utility.EncryptionUtils;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.PathValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Date;
import java.util.Objects;

@Service
@Transactional
public class DigitalSignatureManagerImpl implements DigitalSignatureManager {

    private final DigitalSignatureDao digitalSignatureDao;

    @Autowired
    public DigitalSignatureManagerImpl(DigitalSignatureDao digitalSignatureDao) {
        this.digitalSignatureDao = digitalSignatureDao;
    }


    @Override
    public DigitalSignature getDigitalSignature(int id) {
        DigitalSignature digitalSignature = this.digitalSignatureDao.findDetached(id);

        if (Objects.isNull(digitalSignature) || Objects.isNull(digitalSignature.getSignatureImage())) {
            return digitalSignature;
        }

        try {
            digitalSignature.setSignatureImage(EncryptionUtils.decrypt(digitalSignature.getSignatureImage()));
        } catch (Exception e) {

            // the data is not encrypted, fetching attached entity, encrypt and save it for future use
            try {
                digitalSignature.setSignatureImage(EncryptionUtils.encrypt(digitalSignature.getSignatureImage()));
                this.digitalSignatureDao.merge(digitalSignature);
                this.digitalSignatureDao.flush();

                this.digitalSignatureDao.detach(digitalSignature);
                digitalSignature.setSignatureImage(EncryptionUtils.decrypt(digitalSignature.getSignatureImage()));
            } catch (Exception ex) {
                return digitalSignature;
            }
        }

        return digitalSignature;
    }

    @Override
    public DigitalSignature saveDigitalSignature(Integer facilityId, String providerNo, Integer demographicNo, byte[] imageData, ModuleType moduleType) {
        DigitalSignature digitalSignature = new DigitalSignature();
        digitalSignature.setDateSigned(new Date());
        digitalSignature.setDemographicId(demographicNo);
        digitalSignature.setFacilityId(facilityId);
        digitalSignature.setProviderNo(providerNo);
        digitalSignature.setModuleType(moduleType);

        try {
            digitalSignature.setSignatureImage(EncryptionUtils.encrypt(imageData));
        } catch (Exception e) {
            throw new RuntimeException("Error while encrypting and saving digital signature.", e);
        }

        this.digitalSignatureDao.persist(digitalSignature);
        logger.debug("Signature saved to database with ID: {}", digitalSignature.getId());

        return digitalSignature;
    }

    @Override
    public DigitalSignature processAndSaveDigitalSignature(LoggedInInfo loggedInInfo, String signatureRequestId, Integer demographicNo, ModuleType moduleType) {
        if (!loggedInInfo.getCurrentFacility().isEnableDigitalSignatures()) {
            return null;
        }

        String filename = DigitalSignatureUtils.getTempFilePath(signatureRequestId);
        if (filename == null || filename.isEmpty()) {
            return null;
        }

        try {
            java.io.File baseDirFile = new java.io.File(System.getProperty("java.io.tmpdir"));
            java.io.File validatedFile = PathValidationUtils.validatePath(filename, baseDirFile);
            Path filePath = validatedFile.toPath();

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                logger.debug("Signature file not found or not a regular file: " + filePath);
                return null;
            }

            try (FileInputStream fileInputStream = new FileInputStream(filePath.toFile())) {
                byte[] image = new byte[1024 * 256];
                int readBytes = fileInputStream.read(image);
                if (readBytes <= 0) {
                    logger.debug("Signature file is empty: " + filePath);
                    return null;
                }

                return this.saveDigitalSignature(
                        loggedInInfo.getCurrentFacility().getId(),
                        loggedInInfo.getLoggedInProviderNo(),
                        demographicNo, 
                        image, 
                        moduleType
                );
            }
        } catch (FileNotFoundException e) {
            logger.debug("Signature file not found. User probably didn't collect a signature.", e);
        } catch (SecurityException e) {
            logger.warn("Blocked unsafe file access attempt.", e);
        } catch (Exception e) {
            logger.error("Unexpected error processing digital signature.", e);
        }

        return null;
    }

}
