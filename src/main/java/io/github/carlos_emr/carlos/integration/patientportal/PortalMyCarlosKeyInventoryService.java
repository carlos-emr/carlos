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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Standalone inventory operations own fresh transactions. Larger atomic operations use the worker. */
@Service
public class PortalMyCarlosKeyInventoryService {
    static final String PROPERTY_NAME = PortalMyCarlosKeyInventoryWorker.PROPERTY_NAME;
    static final int COORDINATION_KEY = PortalMyCarlosKeyInventoryWorker.COORDINATION_KEY;
    static final int MAX_STORED_BYTES = PortalMyCarlosKeyInventoryWorker.MAX_STORED_BYTES;
    private final PortalMyCarlosKeyInventoryWorker worker;
    private final PortalMyCarlosInventoryTransactions transactions;

    PortalMyCarlosKeyInventoryService(PortalMyCarlosKeyInventoryWorker worker,
            PortalMyCarlosInventoryTransactions transactions) {
        this.worker = Objects.requireNonNull(worker);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Transactional(readOnly = true)
    public Optional<PortalMyCarlosKeyInventory> read() { return worker.read(); }

    @Transactional(readOnly = true)
    public boolean isPublished(PortalMyCarlosPublicationState.Target target) { return worker.isPublished(target); }

    void requireStandalone() { transactions.requireStandalone(); }

    /** Package-internal whole-operation boundary for authorization/audit and future combined M3 work. */
    <T> T withCurrentInventory(Function<PortalMyCarlosKeyInventoryWorker,T> operation) {
        return transactions.execute(() -> operation.apply(worker));
    }

    public PortalMyCarlosKeyInventory provision(String clinicId, long expectedGeneration,
            List<PatientPortalClinicKeysPublishRequest.Key> completeInventory,
            BiConsumer<PortalMyCarlosKeyInventory,PortalMyCarlosKeyInventory> validator) {
        requireStandalone();
        var saved = List.copyOf(completeInventory);
        return withCurrentInventory(current -> current.provision(clinicId, expectedGeneration, saved, validator));
    }

    public PortalMyCarlosKeyInventory provisionPrepared(String clinicId, long expectedGeneration,
            Function<PortalMyCarlosKeyInventory,List<PatientPortalClinicKeysPublishRequest.Key>> preparation,
            BiConsumer<PortalMyCarlosKeyInventory,PortalMyCarlosKeyInventory> validator) {
        PortalMyCarlosPreparedInventory saved = new PortalMyCarlosPreparedInventory();
        return withCurrentInventory(current -> current.provisionPrepared(clinicId, expectedGeneration,
                previous -> saved.get(clinicId, previous, () -> preparation.apply(previous)), validator));
    }

    public Optional<PortalMyCarlosPublicationState.Claim> claim(PortalMyCarlosPublicationState.Target target,
            String token, Instant now, Duration lease) {
        return claimCurrent(() -> target, token, () -> now, lease);
    }

    Optional<PortalMyCarlosPublicationState.Claim> claimCurrent(Supplier<PortalMyCarlosPublicationState.Target> target,
            String token, Supplier<Instant> now, Duration lease) {
        return withCurrentInventory(current -> current.claimCurrent(target, token, now, lease));
    }

    public boolean acknowledge(PortalMyCarlosPublicationState.Claim claim,
            PortalMyCarlosPublicationState.Target target, PatientPortalClinicKeysPublishResult response, Instant now) {
        return acknowledgeCurrent(claim, () -> target, response, () -> now);
    }

    boolean acknowledgeCurrent(PortalMyCarlosPublicationState.Claim claim,
            Supplier<PortalMyCarlosPublicationState.Target> target,
            PatientPortalClinicKeysPublishResult response, Supplier<Instant> now) {
        return withCurrentInventory(current -> current.acknowledgeCurrent(claim, target, response, now));
    }

    public boolean failed(PortalMyCarlosPublicationState.Claim claim,
            PortalMyCarlosPublicationState.Target target, Instant now, Duration delay) {
        return failedCurrent(claim, () -> target, () -> now, delay);
    }

    boolean failedCurrent(PortalMyCarlosPublicationState.Claim claim,
            Supplier<PortalMyCarlosPublicationState.Target> target, Supplier<Instant> now, Duration delay) {
        return withCurrentInventory(current -> current.failedCurrent(claim, target, now, delay));
    }
}
