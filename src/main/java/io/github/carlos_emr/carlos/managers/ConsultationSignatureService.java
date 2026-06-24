/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Consultation-only signature handling for provider stamp persistence and non-mutating PDF previews.
 */
@Service
@Transactional
public class ConsultationSignatureService {

    public static final String SIGNATURE_IMAGE_OVERRIDE_ATTRIBUTE = "consultationSignatureImageOverride";

    private final DigitalSignatureManager digitalSignatureManager;
    private final SecurityInfoManager securityInfoManager;

    @Autowired
    public ConsultationSignatureService(DigitalSignatureManager digitalSignatureManager, SecurityInfoManager securityInfoManager) {
        this.digitalSignatureManager = digitalSignatureManager;
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Reports whether {@code value} is a persisted {@code DigitalSignature} id (a 1-9 digit number)
     * rather than a manual signature-pad request id or stamp marker.
     *
     * @param value the candidate signature reference (nullable)
     * @return {@code true} when {@code value} is a stored signature id, {@code false} otherwise
     */
    public boolean isStoredSignatureId(String value) {
        return StringUtils.trimToEmpty(value).matches("\\d{1,9}");
    }

    /**
     * Resolves the provider whose stamp should sign the consultation, taking the first numeric value of
     * (submitted signature provider, submitted form provider, logged-in provider).
     *
     * @return the first numeric provider number, or an empty string when none is numeric (logged as a warning)
     */
    public String resolveSignatureProviderNo(String submittedSignatureProviderNo, String submittedProviderNo, String loggedInProviderNo) {
        String providerNo = firstNumeric(submittedSignatureProviderNo, submittedProviderNo, loggedInProviderNo);
        if (StringUtils.isBlank(providerNo)) {
            MiscUtils.getLogger().warn("Unable to resolve a numeric consultation signature provider");
        }
        return providerNo;
    }

    /**
     * Resolves the temp-file request id for a manual (signature-pad) signature: the submitted value when it
     * is a non-blank, non-stored marker, otherwise the freshly captured {@code newSignatureImg}.
     *
     * @return the manual signature request id, or an empty string when neither value supplies one
     */
    public String resolveManualSignatureRequestId(String submittedSignatureImg, String newSignatureImg) {
        String submitted = StringUtils.trimToEmpty(submittedSignatureImg);
        if (StringUtils.isNotBlank(submitted) && !isStoredSignatureId(submitted)) {
            return submitted;
        }
        return StringUtils.trimToEmpty(newSignatureImg);
    }

    public DigitalSignature saveConsultationStamp(LoggedInInfo loggedInInfo, String providerNo, Integer demographicNo) {
        if (loggedInInfo == null || loggedInInfo.getCurrentFacility() == null) {
            return null;
        }
        if (!loggedInInfo.getCurrentFacility().isEnableDigitalSignatures()) {
            MiscUtils.getLogger().debug("Digital signatures disabled for facility - consultation stamp not saved");
            return null;
        }
        if (!canUseProviderStamp(loggedInInfo, providerNo)) {
            return null;
        }

        byte[] imageData = readProviderStampImage(providerNo);
        if (imageData == null || imageData.length == 0) {
            return null;
        }

        try {
            return digitalSignatureManager.saveDigitalSignature(
                    loggedInInfo.getCurrentFacility().getId(),
                    providerNo,
                    demographicNo,
                    imageData,
                    ModuleType.CONSULTATION);
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Error persisting consultation stamp signature for provider {}", providerNo, e);
            return null;
        }
    }

    /**
     * Resolves the signature image bytes to embed in a non-mutating print preview, without persisting a
     * {@code DigitalSignature}.
     *
     * <p>Branching: a request that already references a stored signature id (stamp mode, not re-signing)
     * returns {@code null} so the normal PDF path renders the persisted signature; a manual re-sign returns
     * the captured temp-file bytes; a stamp re-sign returns the selected provider's stamp bytes.</p>
     *
     * @param newSignature          whether the form is supplying a freshly captured signature
     * @param submittedSignatureImg the current signature reference on the form (stored id or marker)
     * @param newSignatureImg       the manual signature-pad request id, when present
     * @param signatureProviderNo   the resolved provider whose stamp is used in stamp mode
     * @return the preview signature bytes, or {@code null} when the persisted-signature path should be used
     */
    public byte[] resolvePreviewSignatureImage(boolean newSignature, String submittedSignatureImg,
                                               String newSignatureImg, String signatureProviderNo) {
        if (!newSignature && isStoredSignatureId(submittedSignatureImg)) {
            return null;
        }

        if (newSignature) {
            byte[] manualSignature = readTempSignatureImage(resolveManualSignatureRequestId(submittedSignatureImg, newSignatureImg));
            if (manualSignature != null && manualSignature.length > 0) {
                return manualSignature;
            }
            if (isStoredSignatureId(submittedSignatureImg)) {
                return null;
            }
        } else {
            return readProviderStampImage(signatureProviderNo);
        }

        return null;
    }

    // FindSecBugs PATH_TRAVERSAL_IN: filename is constrained to a numeric provider stamp name and validated against the eForm image directory.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "filename is constrained to a numeric provider stamp name and validated against the eForm image directory")
    public byte[] readProviderStampImage(String providerNo) {
        if (!isNumericProviderNo(providerNo)) {
            MiscUtils.getLogger().warn("Rejected consultation signature stamp for non-numeric provider {}", providerNo);
            return null;
        }

        String stampFilename = UserProperty.CONSULT_SIGNATURE_PREFIX + providerNo.trim() + ".png";
        File imageFolder = new File(CarlosProperties.getInstance().getEformImageDirectory());

        try {
            File stampFile = PathValidationUtils.validatePath(stampFilename, imageFolder);
            Path stampPath = stampFile.toPath();
            if (!Files.isRegularFile(stampPath)) {
                MiscUtils.getLogger().debug("Consultation stamp signature file not found: {}", stampFilename);
                return null;
            }
            return Files.readAllBytes(stampPath);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Blocked unsafe consultation stamp signature path for provider {}", providerNo, e);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error reading consultation stamp signature for provider {}", providerNo, e);
        }

        return null;
    }

    // FindSecBugs PATH_TRAVERSAL_IN: DigitalSignatureUtils and PathValidationUtils constrain the temp signature path to java.io.tmpdir.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "DigitalSignatureUtils and PathValidationUtils constrain the temp signature path to java.io.tmpdir")
    public byte[] readTempSignatureImage(String signatureRequestId) {
        if (StringUtils.isBlank(signatureRequestId)) {
            return null;
        }

        try {
            String filename = DigitalSignatureUtils.getTempFilePath(signatureRequestId);
            if (StringUtils.isBlank(filename)) {
                return null;
            }

            File baseDirFile = new File(System.getProperty("java.io.tmpdir"));
            File validatedFile = PathValidationUtils.validatePath(filename, baseDirFile);
            Path filePath = validatedFile.toPath();
            if (!Files.isRegularFile(filePath)) {
                return null;
            }

            return Files.readAllBytes(filePath);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Blocked unsafe temporary consultation signature access", e);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error reading temporary consultation signature", e);
        }

        return null;
    }

    private boolean canUseProviderStamp(LoggedInInfo loggedInInfo, String providerNo) {
        if (!isNumericProviderNo(providerNo)) {
            MiscUtils.getLogger().warn("Rejected consultation stamp save for non-numeric provider {}", providerNo);
            return false;
        }

        String loggedInProviderNo = loggedInInfo.getLoggedInProviderNo();
        if (providerNo.equals(loggedInProviderNo)) {
            return true;
        }

        boolean allowed = securityInfoManager.hasPrivilege(loggedInInfo, "_con", "w", null);
        if (!allowed) {
            MiscUtils.getLogger().warn("Provider {} attempted to save consultation stamp for provider {} without _con write access",
                    loggedInProviderNo, providerNo);
        }
        return allowed;
    }

    private boolean isNumericProviderNo(String providerNo) {
        return StringUtils.trimToEmpty(providerNo).matches("\\d+");
    }

    private String firstNumeric(String... values) {
        for (String value : values) {
            String trimmed = StringUtils.trimToEmpty(value);
            if (trimmed.matches("\\d+")) {
                return trimmed;
            }
        }
        return "";
    }
}
