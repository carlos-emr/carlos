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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Recipient-bound encryption of an already validated private clinical package. Both complete
 * permission-scope reads and pinned policy/certificate checks bracket encryption. Only a
 * finished, authenticated staged ciphertext can mint evidence. This does not publish READY;
 * the publication gate must independently require a qualified installed clinical engine.
 */
@Service
public class CdsExportDeliveryService {
    private final CdsExportStagingFactory stores;
    private final CdsExportLedgerService ledger;
    private final CdsExportDisclosureScopeService scopes;
    private final CdsExportDisclosurePolicyService disclosure;
    public CdsExportDeliveryService(CdsExportStagingFactory stores, CdsExportLedgerService ledger,
            CdsExportDisclosureScopeService scopes, CdsExportDisclosurePolicyService disclosure) {
        this.stores = Objects.requireNonNull(stores); this.ledger = Objects.requireNonNull(ledger);
        this.scopes = Objects.requireNonNull(scopes); this.disclosure = Objects.requireNonNull(disclosure);
    }
    public Encrypted encrypt(CdsExportLeaseDto lease, CdsExportClinicalPackageService.Prepared prepared,
            long maximumCiphertextBytes, CdsExportRunControl control) throws IOException {
        Objects.requireNonNull(control); Objects.requireNonNull(prepared);
        if (maximumCiphertextBytes < 1 || maximumCiphertextBytes > (128L << 30)) throw new IllegalArgumentException("CDS_EXPORT_DELIVERY_LIMIT_INVALID");
        if (!lease.runId().equals(prepared.runId()) || lease.epoch() != prepared.epoch()) invalid();
        var policy = disclosure.resolveAccepted(lease.document(), Instant.now());
        var metadata = prepared.metadata();
        if (metadata.profile() != lease.document().command().profile() || !metadata.categories().equals(lease.document().command().categories())
                || !metadata.physicianGroup().equals(policy.physicianGroup()) || !metadata.groupContactInformation().equals(policy.groupContactInformation())
                || !metadata.auditAccessDescription().equals(policy.auditAccessDescription())) invalid();
        var first = scopes.verify(lease, prepared, control); requireScope(first, prepared);
        ledger.phase(lease, CdsExportState.ENCRYPTING);
        var store = stores.openExisting(lease.runId(), lease.epoch(), lease.document().storage(), control::checkpoint);
        CdsExportStagedFileDto ciphertext = null; Throwable primary = null; boolean complete = false;
        var proof = new CdsExportDeliveryDto[1];
        try {
            ciphertext = store.stage("DELIVERY_CMS", output -> {
                try (var input = store.open(prepared.archive().archive())) {
                    proof[0] = new CdsExportDeliveryComposer().compose(input, output, policy.certificate(), policy.recipientFingerprint(),
                            prepared.archive().archive().plaintextBytes(), prepared.archive().archive().plaintextSha256(), Instant.now(),
                            maximumCiphertextBytes, control::checkpoint);
                }
            });
            if (!CdsExportDeliveryComposer.PROFILE.equals(proof[0].encryptionProfile())
                    || !lease.document().recipientFingerprint().equals(proof[0].recipientFingerprint())
                    || !prepared.archive().archive().plaintextSha256().equals(proof[0].zipSha256())
                    || prepared.archive().archive().plaintextBytes() != proof[0].zipBytes()
                    || !ciphertext.plaintextSha256().equals(proof[0].ciphertextSha256()) || ciphertext.plaintextBytes() != proof[0].ciphertextBytes()) invalid();
            try (var input = store.open(ciphertext)) { input.transferTo(java.io.OutputStream.nullOutputStream()); }
            var last = scopes.verify(lease, prepared, control); requireScope(last, prepared);
            var finalPolicy = disclosure.resolveAccepted(lease.document(), Instant.now());
            if (!finalPolicy.recipientFingerprint().equals(proof[0].recipientFingerprint())) invalid(); control.checkpoint();
            var result = new Encrypted(prepared, ciphertext, proof[0], last); complete = true; return result;
        } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            if (!complete && ciphertext != null) try { store.discardUnpublished(ciphertext); }
            catch (IOException | RuntimeException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
        }
    }
    private static void requireScope(CdsExportDisclosureScopeService.Verified scope, CdsExportClinicalPackageService.Prepared prepared) throws IOException {
        if (scope == null || !scope.runId().equals(prepared.runId()) || scope.epoch() != prepared.epoch() || scope.patients() != prepared.patients()
                || !scope.cohortSha256().equals(prepared.cohortSha256()) || !scope.authorizationSha256().equals(prepared.authorization().plaintextSha256())) invalid();
    }
    /** Ciphertext byte evidence only; it conveys neither publication nor persistent disclosure permission. */
    public static final class Encrypted {
        private final CdsExportClinicalPackageService.Prepared prepared;
        private final CdsExportStagedFileDto ciphertext;
        private final CdsExportDeliveryDto proof;
        private final CdsExportDisclosureScopeService.Verified scope;
        private Encrypted(CdsExportClinicalPackageService.Prepared prepared, CdsExportStagedFileDto ciphertext,
                CdsExportDeliveryDto proof, CdsExportDisclosureScopeService.Verified scope) {
            this.prepared = prepared; this.ciphertext = ciphertext; this.proof = proof; this.scope = scope;
        }
        public CdsExportClinicalPackageService.Prepared prepared() { return prepared; }
        public CdsExportStagedFileDto ciphertext() { return ciphertext; }
        public CdsExportDeliveryDto proof() { return proof; }
        public CdsExportDisclosureScopeService.Verified scope() { return scope; }
        @Override public String toString() { return "CdsExportEncryptedDelivery[protected]"; }
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_DELIVERY_BINDING_INVALID"); }
}
