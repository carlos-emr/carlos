/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import java.util.Objects;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.springframework.stereotype.Service;

/**
 * Applies the prescriber's configured signature stamp ({@code consult_sig_<providerNo>.png}) to a
 * newly written prescription as a stored {@link DigitalSignature}, the same way consultation
 * requests are stamped.
 *
 * <p>Why this exists: the Rx print/fax page only enables the Fax buttons when the script carries a
 * stored digital signature, and the fax PDF is signed from that stored image. The preview showed
 * the stamp, but nothing persisted it, so a prescriber with a stamp still had to draw a signature
 * in the pad (overwriting the stamp) before Fax became available. Persisting the stamp at the point
 * the script is written gives the PDF and the Fax gate the same immutable, point-in-time signature
 * record that consultations already get.</p>
 *
 * <p>Guards, in order: {@code rx_signature_enabled} (or {@code rx_fax_enabled}) must be on; the
 * session facility must allow digital signatures; the script must not already be signed; and the
 * stamp must belong to the logged-in provider (enforced again inside
 * {@link DigitalSignatureManager#saveStampSignature}). Reprints never call this: they render the
 * signature stored when the script was first printed.</p>
 *
 * <p>The applied stamp is a default, not a lock: the signature pad stays available on the page and
 * a drawn signature replaces the stamp on the prescription.</p>
 *
 * @since 2026-09-01
 */
@Service
public class PrescriptionSignatureStampService {

    /**
     * Request attribute the print/fax view reads to keep the signature pad visible after a stamp
     * was applied automatically, so the prescriber can still override the stamp by hand.
     */
    public static final String RX_STAMP_SIGNATURE_APPLIED = "rxStampSignatureApplied";

    private final DigitalSignatureManager digitalSignatureManager;
    private final PrescriptionManager prescriptionManager;

    public PrescriptionSignatureStampService(DigitalSignatureManager digitalSignatureManager,
                                             PrescriptionManager prescriptionManager) {
        this.digitalSignatureManager = digitalSignatureManager;
        this.prescriptionManager = prescriptionManager;
    }

    /**
     * Stamps the script just written into {@code bean} and links the stored signature to both the
     * persisted prescription and every stash item, so the view and the PDF see the same id.
     *
     * @param loggedInInfo the current session
     * @param bean         the Rx session bean whose stash was just saved under {@code scriptId}
     * @param scriptId     the script number returned by {@code RxPrescriptionData.saveScript}
     * @return the id of the stored stamp signature, or {@code null} when nothing was applied
     *         (feature off, facility disallows digital signatures, already signed, no stamp on
     *         file, or a persistence failure, which is logged and never propagated to the page)
     */
    public Integer applyStampToScript(LoggedInInfo loggedInInfo, RxSessionBean bean, String scriptId) {
        if (loggedInInfo == null || bean == null || bean.getStashSize() == 0) {
            return null;
        }
        if (Objects.nonNull(bean.getStashItem(0).getDigitalSignatureId())) {
            return null;
        }
        Integer scriptNo = parseScriptId(scriptId);
        if (scriptNo == null) {
            return null;
        }
        Integer signatureId = applyStamp(loggedInInfo, bean.getProviderNo(), bean.getDemographicNo(), scriptNo);
        if (signatureId != null) {
            for (int i = 0; i < bean.getStashSize(); i++) {
                RxPrescriptionData.Prescription rx = bean.getStashItem(i);
                if (rx != null) {
                    rx.setDigitalSignatureId(signatureId);
                }
            }
        }
        return signatureId;
    }

    /**
     * Persists the provider's stamp as a {@link ModuleType#PRESCRIPTION} signature and records it
     * on the prescription row.
     *
     * @return the stored signature id, or {@code null} when nothing was applied
     */
    public Integer applyStamp(LoggedInInfo loggedInInfo, String providerNo, Integer demographicNo, Integer scriptNo) {
        if (loggedInInfo == null || providerNo == null || scriptNo == null) {
            return null;
        }
        if (!CarlosProperties.getInstance().isRxSignatureEnabled()) {
            return null;
        }
        Facility facility = loggedInInfo.getCurrentFacility();
        if (facility == null || !facility.isEnableDigitalSignatures()) {
            MiscUtils.getLogger().debug("Rx stamp not applied: facility digital signatures unavailable");
            return null;
        }
        if (!Objects.equals(loggedInInfo.getLoggedInProviderNo(), providerNo)) {
            // Only the prescriber signs their own script; the manager refuses this too, but
            // deciding it here keeps the refusal out of the warn log for the common re-render case.
            MiscUtils.getLogger().debug("Rx stamp not applied: script provider is not the logged-in provider");
            return null;
        }

        try {
            DigitalSignature saved = digitalSignatureManager.saveStampSignature(
                    loggedInInfo, providerNo, demographicNo, ModuleType.PRESCRIPTION);
            if (saved == null || saved.getId() == null) {
                // No stamp configured for this provider (or unreadable): the pad remains the way to sign.
                return null;
            }
            prescriptionManager.setPrescriptionSignature(loggedInInfo, scriptNo, saved.getId());
            return saved.getId();
        } catch (RuntimeException e) {
            // A stamp failure must never take down the print/fax page; the pad still works.
            MiscUtils.getLogger().error("Error applying Rx stamp signature for provider {}",
                    LogSafe.sanitize(providerNo), e);
            return null;
        }
    }

    private static Integer parseScriptId(String scriptId) {
        if (scriptId == null || !scriptId.matches("\\d{1,9}")) {
            return null;
        }
        return Integer.valueOf(scriptId);
    }
}
