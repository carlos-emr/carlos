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
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Explicit administrative provisioning; no startup inference, HTTP route or publication. */
@Service
public class PortalMyCarlosKeyProvisioningService {
    private final PortalMyCarlosKeyInventoryService repository;
    private final SecurityInfoManager security;
    private final AuditSink audit;

    @FunctionalInterface
    interface AuditSink { void write(LoggedInInfo info, long generation, int keys); }
    private final Supplier<String> clinicId;
    private final Supplier<PatientPortalClinicKeysPublishRequest.Key> currentSigner;
    private final BiFunction<PDFSigningConfig, PatientPortalClinicKeysPublishRequest.Key, PrivateKey> retainedKey;

    @Autowired
    public PortalMyCarlosKeyProvisioningService(
            PortalMyCarlosKeyInventoryService repository, SecurityInfoManager security) {
        this(repository, security,
                () -> PatientPortalSettings.rawProperty(PatientPortalSettings.CLINIC_ID_KEY),
                PortalMyCarlosSigningIdentity::currentPublicKey, PortalMyCarlosRetainedSigningKeys::load);
    }

    PortalMyCarlosKeyProvisioningService(
            PortalMyCarlosKeyInventoryService repository, SecurityInfoManager security,
            Supplier<String> clinicId, Supplier<PatientPortalClinicKeysPublishRequest.Key> currentSigner,
            BiFunction<PDFSigningConfig, PatientPortalClinicKeysPublishRequest.Key, PrivateKey> retainedKey) {
        this(repository, security, clinicId, currentSigner, retainedKey,
                (info, generation, keys) -> LogAction.addLog(info, LogConst.UPDATE, "mycarlosKeyInventory",
                        null, null, "generation=" + generation + " keyCount=" + keys));
    }

    PortalMyCarlosKeyProvisioningService(
            PortalMyCarlosKeyInventoryService repository, SecurityInfoManager security,
            Supplier<String> clinicId, Supplier<PatientPortalClinicKeysPublishRequest.Key> currentSigner,
            BiFunction<PDFSigningConfig, PatientPortalClinicKeysPublishRequest.Key, PrivateKey> retainedKey,
            AuditSink audit) {
        this.repository = repository;
        this.security = security;
        this.audit = audit;
        this.clinicId = clinicId;
        this.currentSigner = currentSigner;
        this.retainedKey = retainedKey;
    }

    /**
     * The operator must explicitly declare that all historical public keys have been supplied.
     * A lost response requires rereading/ comparing the generation before another explicit CAS.
     */
    public PortalMyCarlosKeyInventory provision(
            LoggedInInfo loggedInInfo, long expectedGeneration,
            List<PatientPortalClinicKeysPublishRequest.Key> completeInventory, boolean declaredComplete) {
        repository.requireStandalone();
        var supplied = List.copyOf(completeInventory);
        return repository.withCurrentInventory(current -> {
            String clinic = configuredClinic(loggedInInfo, declaredComplete);
            PortalMyCarlosKeyInventory result = current.provision(
                    clinic, expectedGeneration, supplied, this::validateNewActiveKeys);
            auditIfChanged(loggedInInfo, expectedGeneration, result);
            return result;
        });
    }

    /**
     * Adds one explicitly selected replacement proof under the same durable generation lock.
     * The server-side retained configuration selects one alias; it is never accepted from a route.
     * A current-generation retry reuses an existing exact proof without loading private material.
     */
    public PortalMyCarlosKeyInventory provisionWithReplacement(
            LoggedInInfo loggedInInfo, long expectedGeneration,
            List<PatientPortalClinicKeysPublishRequest.Key> completeInventory, boolean declaredComplete,
            String replacementKid, String predecessorKid, PDFSigningConfig selectedRetainedKey) {
        repository.requireStandalone();
        var supplied = List.copyOf(completeInventory);
        PortalMyCarlosPreparedInventory saved = new PortalMyCarlosPreparedInventory();
        return repository.withCurrentInventory(current -> {
            String clinic = configuredClinic(loggedInInfo, declaredComplete);
            PortalMyCarlosKeyInventory result = current.provisionPrepared(clinic, expectedGeneration,
                    previous -> saved.get(clinic, previous, () -> prepareReplacement(clinic, previous,
                            supplied, replacementKid, predecessorKid, selectedRetainedKey)),
                    this::validateNewActiveKeys);
            auditIfChanged(loggedInInfo, expectedGeneration, result);
            return result;
        });
    }

    private String configuredClinic(LoggedInInfo loggedInInfo, boolean declaredComplete) {
        if (!security.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        if (!declaredComplete) {
            throw new IllegalArgumentException("a complete historical public key catalogue must be declared");
        }
        String configured = clinicId.get();
        if (configured == null || configured.isBlank()) {
            throw new PatientPortalConfigurationException("patient_portal.clinic_id is required");
        }
        String clinic = configured.trim();
        PatientPortalSettings.validateClinicId(clinic);
        return clinic;
    }

    private void auditIfChanged(
            LoggedInInfo info, long expectedGeneration, PortalMyCarlosKeyInventory result) {
        if (result.generation() != expectedGeneration) {
            auditAfterCommit(info, result.generation(), result.publication().keys().size());
        }
    }

    private List<PatientPortalClinicKeysPublishRequest.Key> prepareReplacement(
            String clinic, PortalMyCarlosKeyInventory previous,
            List<PatientPortalClinicKeysPublishRequest.Key> supplied,
            String replacementKid, String predecessorKid, PDFSigningConfig selectedRetainedKey) {
        var keys = new ArrayList<>(new PatientPortalClinicKeysPublishRequest(supplied).keys());
        var replacement = keys.stream().filter(key -> key.kid().equals(replacementKid)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("the replacement public key is required"));
        var existing = previous == null ? null : previous.publication().keys().stream()
                .filter(key -> key.kid().equals(replacementKid)).findFirst().orElse(null);
        PatientPortalClinicKeysPublishRequest.Key prepared;
        if (existing != null) {
            if (!existing.spki().equals(replacement.spki())
                    || !Objects.equals(existing.replacesKid(), predecessorKid)
                    || existing.replacementSignature() == null
                    || (replacement.replacesKid() != null
                            && (!Objects.equals(existing.replacesKid(), replacement.replacesKid())
                            || !Objects.equals(existing.replacementSignature(), replacement.replacementSignature())))) {
                throw new IllegalArgumentException("the stored replacement proof cannot be changed");
            }
            prepared = new PatientPortalClinicKeysPublishRequest.Key(existing.kid(), existing.spki(),
                    replacement.status(), existing.replacesKid(), existing.replacementSignature());
        } else {
            var predecessor = (previous == null ? keys : previous.publication().keys()).stream()
                    .filter(key -> key.kid().equals(predecessorKid)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("the retained predecessor is required"));
            if (!"active".equals(predecessor.status()) || replacement.replacesKid() != null) {
                throw new IllegalArgumentException("an active retained predecessor and unsigned new key are required");
            }
            prepared = PortalMyCarlosKeyReplacementProofs.mintOnce(
                    clinic, predecessor, replacement, retainedKey.apply(selectedRetainedKey, predecessor));
        }
        keys.set(keys.indexOf(replacement), prepared);
        return List.copyOf(keys);
    }

    private void validateNewActiveKeys(
            PortalMyCarlosKeyInventory previous, PortalMyCarlosKeyInventory next) {
        Set<String> known = previous == null ? Set.of() : previous.publication().keys().stream()
                .map(PatientPortalClinicKeysPublishRequest.Key::kid).collect(Collectors.toSet());
        boolean newActive = next.publication().keys().stream()
                .anyMatch(key -> "active".equals(key.status()) && !known.contains(key.kid()));
        if (!newActive) {
            // Expired/disabled/unavailable signing material must not prevent revocation or retry.
            return;
        }
        var signer = currentSigner.get();
        if (signer == null || next.publication().keys().stream().noneMatch(key ->
                "active".equals(key.status()) && key.kid().equals(signer.kid())
                        && key.spki().equals(signer.spki()))) {
            throw new IllegalArgumentException("the configured myCarlos signer is missing from the active catalogue");
        }
    }

    private void auditAfterCommit(LoggedInInfo info, long generation, int keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("key provisioning requires transaction synchronization");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                audit.write(info, generation, keys);
            }
        });
    }
}
