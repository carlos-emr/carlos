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

/** Qualified clinical engine, current scope and pinned recipient policy must all agree before atomic READY. */
@Service
public class CdsExportPublicationService {
    private final CdsExportEngineRegistry engines;
    private final CdsExportLedgerService ledger;
    private final CdsExportDisclosureScopeService scopes;
    private final CdsExportDisclosurePolicyService disclosure;

    public CdsExportPublicationService(
            CdsExportEngineRegistry engines,
            CdsExportLedgerService ledger,
            CdsExportDisclosureScopeService scopes,
            CdsExportDisclosurePolicyService disclosure) {
        this.engines = Objects.requireNonNull(engines);
        this.ledger = Objects.requireNonNull(ledger);
        this.scopes = Objects.requireNonNull(scopes);
        this.disclosure = Objects.requireNonNull(disclosure);
    }

    /** No new file is deleted on a failed/unknown acknowledgement; recovery arbitrates under the same run lock. */
    public CdsExportJobDto publish(
            CdsExportLeaseDto lease,
            CdsExportDeliveryService.Encrypted encrypted,
            long retentionSeconds,
            CdsExportRunControl control)
            throws IOException {
        Objects.requireNonNull(control);
        Objects.requireNonNull(encrypted);
        if (retentionSeconds < 60 || retentionSeconds > 30 * 86_400L)
            throw new IllegalArgumentException("CDS_EXPORT_RETENTION_LIMIT_INVALID");
        qualified(lease);
        control.checkpoint();
        var context = ledger.retainEncrypted(lease, encrypted);
        var verified = scopes.verify(lease, encrypted.prepared(), control);
        if (!context.runId().equals(verified.runId())
                || context.epoch() != verified.epoch()
                || context.patients() != verified.patients()
                || !context.cohort().plaintextSha256().equals(verified.cohortSha256())
                || !context.authorization()
                        .plaintextSha256()
                        .equals(verified.authorizationSha256())) invalid();
        disclosure.resolveAccepted(lease.document(), Instant.now());
        qualified(lease);
        control.checkpoint();
        return ledger.publish(new Approved(lease, context, retentionSeconds));
    }

    private void qualified(CdsExportLeaseDto lease) {
        var engine = engines.require(lease.document().command().profile());
        if (!lease.document().compatibilityDigest().equals(engine.compatibilityDigest()))
            throw new IllegalStateException("CDS_EXPORT_RUNTIME_CHANGED");
    }

    /** Constructible only after this service's full publication gate; never serialized or accepted from HTTP/CLI. */
    public static final class Approved {
        private final CdsExportLeaseDto lease;
        private final CdsExportArtifactContextDto context;
        private final long retentionSeconds;

        private Approved(
                CdsExportLeaseDto lease,
                CdsExportArtifactContextDto context,
                long retentionSeconds) {
            this.lease = lease;
            this.context = context;
            this.retentionSeconds = retentionSeconds;
        }

        public CdsExportLeaseDto lease() {
            return lease;
        }

        public CdsExportArtifactContextDto context() {
            return context;
        }

        public long retentionSeconds() {
            return retentionSeconds;
        }

        @Override
        public String toString() {
            return "CdsExportPublicationApproval[protected]";
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PUBLICATION_BINDING_INVALID");
    }
}
