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

import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Concrete clinical job orchestration shared by qualified engine adapters. It does not
 * advertise or qualify a profile: source-catalogue/fact conformance and installed CLI/browser
 * qualification remain mandatory. No uncertainty path erases possibly retained assets.
 */
@Service
public class CdsExportClinicalJobService {
    private final CdsExportImplementationIdentityService identity;
    private final CdsExportActivePlanService plans;
    private final CdsExportConfigurationService configuration;
    private final CdsExportAccessService access;
    private final CdsExportDisclosurePolicyService disclosure;
    private final CdsExportStagingFactory stores;
    private final CdsExportSelectionService selections;
    private final CdsExportCohortService cohorts;
    private final CdsExportLedgerService ledger;
    private final CdsExportClinicalPackageService packages;
    private final CdsExportDeliveryService delivery;
    private final ObjectProvider<CdsExportPublicationService> publication;
    public CdsExportClinicalJobService(CdsExportImplementationIdentityService identity, CdsExportActivePlanService plans,
            CdsExportConfigurationService configuration, CdsExportAccessService access, CdsExportDisclosurePolicyService disclosure,
            CdsExportStagingFactory stores, CdsExportSelectionService selections, CdsExportCohortService cohorts,
            CdsExportLedgerService ledger, CdsExportClinicalPackageService packages, CdsExportDeliveryService delivery,
            ObjectProvider<CdsExportPublicationService> publication) {
        this.identity = identity; this.plans = plans; this.configuration = configuration; this.access = access; this.disclosure = disclosure;
        this.stores = stores; this.selections = selections; this.cohorts = cohorts; this.ledger = ledger; this.packages = packages;
        this.delivery = delivery; this.publication = publication;
    }
    public String compatibilityDigest() throws IOException { return digest(identity.current(), plans.resolve().zoneSha256()); }
    public CdsExportJobDocumentDto bind(CdsExportActorDto actor, CdsExportCommand command) throws IOException {
        outside(); configuration.requireEnabled(); access.requireScope(actor, command); var plan = plans.resolve();
        var policy = disclosure.resolve(command, Instant.now()); var storage = stores.bind();
        return new CdsExportJobDocumentDto(2, actor, command, digest(identity.current(), plan.zoneSha256()), policy.senderNamespace(),
                policy.recipientFingerprint(), policy.auditPlanDigest(), storage);
    }
    /** Caller owns the durable worker lease; this method neither fabricates a lease nor treats a returned package as success. */
    public CdsExportJobDto execute(CdsExportLeaseDto lease, CdsExportRunControl control) throws IOException {
        outside(); Objects.requireNonNull(control); Objects.requireNonNull(lease); configuration.requireEnabled(); var plan = plans.resolve();
        if (lease.document().protocol() != 2 || lease.document().storage() == null) throw new CdsExportReviewException(CdsExportReviewException.Reason.RUNTIME_CHANGED);
        compatible(lease, plan); access.requireScope(lease.document().actor(), lease.document().command()); disclosure.resolveAccepted(lease.document(), Instant.now()); control.checkpoint();
        Runnable io = () -> { if (TransactionSynchronizationManager.isActualTransactionActive()) control.captureCheckpoint(); else control.checkpoint(); };
        var store = stores.create(lease, io);
        var selection = selections.capture(lease.document().command(), store, plan.archive().patients(), control::captureCheckpoint);
        if (selection.patients() == 0) throw new CdsExportReviewException(CdsExportReviewException.Reason.NO_PATIENTS_SELECTED);
        var cohort = cohorts.prepare(lease, selection, control); ledger.sealSelection(lease, cohort);
        var prepared = packages.compose(lease, plan.applicationVersion(), plan.clinicalZone(), plan.source(), plan.xml(), plan.archive(), control);
        ledger.retainPrepared(lease, prepared);
        var encrypted = delivery.encrypt(lease, prepared, plan.ciphertextBytes(), control);
        compatible(lease, plan); access.requireScope(lease.document().actor(), lease.document().command()); control.checkpoint();
        var published = publication.getObject().publish(lease, encrypted, plan.retentionSeconds(), control);
        if (published == null || !lease.runId().equals(published.id()) || published.epoch() != lease.epoch() || !published.complete()) throw new IllegalStateException("CDS_EXPORT_PUBLICATION_INCOMPLETE");
        return published;
    }
    private static void outside() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
    }
    private void compatible(CdsExportLeaseDto lease, CdsExportActivePlanService.Plan plan) {
        if (!lease.document().compatibilityDigest().equals(digest(identity.current(), plan.zoneSha256()))) throw new CdsExportReviewException(CdsExportReviewException.Reason.RUNTIME_CHANGED);
    }
    private static String digest(String implementation, String zone) {
        if (implementation == null || !implementation.matches("[0-9a-f]{64}") || zone == null || !zone.matches("[0-9a-f]{64}")) throw new IllegalStateException("CDS_EXPORT_IMPLEMENTATION_IDENTITY_UNAVAILABLE");
        return CdsImportKeys.hash("CARLOS_CDS_EXPORT_JOB_1", implementation, zone);
    }
}
