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

package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.DigitalSignatureDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
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
            // Decryption failed. The record is NOT mutated here (the old code destructively re-encrypted
            // and persisted the bytes, permanently double-encrypting a merely-undecryptable record).
            // setSignatureImage was not reached, so the entity still holds its original stored bytes.
            //
            // Those bytes are only safe to return when they really are a plaintext image (the legacy
            // case). For a genuinely encrypted record whose key is currently unavailable (rotation,
            // outage), the ciphertext is undecodable — returning it would stream garbage as image/jpeg
            // with HTTP 200, and a signed eForm would fax/archive with a blank signature block while the
            // render gate (which only trips on >= 400) sees success. So sniff the bytes: keep them only
            // if they look like a real image; otherwise null the image so the signature servlet 404s and
            // the render fails honestly.
            byte[] stored = digitalSignature.getSignatureImage();
            if (looksLikeImage(stored)) {
                logger.warn("Could not decrypt signature ID {}; stored bytes look like a legacy plaintext image, returning as-is", id);
            } else {
                logger.error("Could not decrypt signature ID {} and the stored bytes are not a recognizable image "
                        + "(likely encrypted with a currently-unavailable key); returning no image", id, e);
                digitalSignature.setSignatureImage(null);
            }
        }

        return digitalSignature;
    }

    /**
     * True when the bytes begin with a known raster-image magic number (JPEG, PNG, GIF, or BMP).
     * Used to distinguish a legacy plaintext signature image from undecryptable ciphertext on the
     * decrypt-failure path so a broken signature is never streamed as a valid image.
     */
    private static boolean looksLikeImage(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        boolean jpeg = b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF;
        boolean png = b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47;
        boolean gif = b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38;
        boolean bmp = b0 == 0x42 && b1 == 0x4D;
        return jpeg || png || gif || bmp;
    }

    @Override
    public DigitalSignature getDigitalSignatureMetadata(int id) {
        return this.digitalSignatureDao.findMetadataById(id);
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
                logger.debug("Signature file not found or not a regular file: {}", filePath);
                return null;
            }

            byte[] image = Files.readAllBytes(filePath);
            if (image.length == 0) {
                logger.debug("Signature file is empty: {}", filePath);
                return null;
            }

            return this.saveDigitalSignature(
                    loggedInInfo.getCurrentFacility().getId(),
                    loggedInInfo.getLoggedInProviderNo(),
                    demographicNo,
                    image,
                    moduleType
            );
        } catch (FileNotFoundException e) {
            logger.debug("Signature file not found. User probably didn't collect a signature.", e);
        } catch (SecurityException e) {
            logger.warn("Blocked unsafe file access attempt.", e);
        } catch (Exception e) {
            logger.error("Unexpected error processing digital signature.", e);
        }

        return null;
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Override
    public DigitalSignature saveStampSignature(LoggedInInfo loggedInInfo, String providerNo, Integer demographicNo, ModuleType moduleType) {
        if (!loggedInInfo.getCurrentFacility().isEnableDigitalSignatures()) {
            logger.debug("Digital signatures disabled for facility — stamp not saved");
            return null;
        }

        // Prevent provider impersonation: the stamp must belong to the logged-in user.
        // Objects.equals, not loggedInProvider.equals(...): getLoggedInProviderNo() is nullable, and
        // a null receiver threw an NPE out of this check rather than denying. On a security gate
        // that is the wrong failure — an unattributable session must be refused, not crash the
        // caller. Null never equals a real providerNo, so this now falls through to the deny below.
        String loggedInProvider = loggedInInfo.getLoggedInProviderNo();
        if (!Objects.equals(loggedInProvider, providerNo)) {
            logger.warn("Provider {} attempted to use stamp signature of provider {} — denied",
                    loggedInProvider, providerNo);
            return null;
        }

        String stampFilename = UserProperty.CONSULT_SIGNATURE_PREFIX + providerNo + ".png";
        File imageFolder = new File(CarlosProperties.getInstance().getEformImageDirectory());

        try {
            File stampFile = PathValidationUtils.validatePath(stampFilename, imageFolder);

            if (!stampFile.exists()) {
                logger.debug("Stamp signature file not found: {}", stampFilename);
                return null;
            }

            byte[] imageData = Files.readAllBytes(stampFile.toPath());
            // Emptiness guard, matching saveDigitalSignatureFromTempFile above. Without it a
            // zero-byte stamp file is encrypted and stored as a valid-looking row, and the signature
            // servlet then streams it as a 200 with Content-Length: 0 — a blank signature block on a
            // rendered consult that no gate can see, because the decrypt-failure defence below only
            // inspects bytes that failed to decrypt. Refuse to store what can only render blank.
            if (imageData.length == 0) {
                logger.warn("Stamp signature file is empty; refusing to store it: {}", stampFilename);
                return null;
            }
            return this.saveDigitalSignature(
                    loggedInInfo.getCurrentFacility().getId(),
                    providerNo, demographicNo, imageData, moduleType
            );
        } catch (SecurityException e) {
            logger.warn("Blocked unsafe file access attempt for stamp signature.", e);
        } catch (IOException e) {
            logger.error("Error reading stamp signature file: {}", stampFilename, e);
        } catch (RuntimeException e) {
            logger.error("Error persisting stamp signature for provider {}: {}", providerNo, e.getMessage(), e);
        }

        return null;
    }

}
