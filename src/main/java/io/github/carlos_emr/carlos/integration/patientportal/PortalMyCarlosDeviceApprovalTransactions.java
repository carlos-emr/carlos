/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** One existing transaction owner; no acceptance authority or independently committed work. */
@Service
class PortalMyCarlosDeviceApprovalTransactions {
    static final int COORDINATION_KEY = Integer.MIN_VALUE + 2;
    private final PortalMyCarlosInventoryTransactions transactions;
    private final PortalMyCarlosKeyInventoryWorker inventory;

    PortalMyCarlosDeviceApprovalTransactions(PortalMyCarlosInventoryTransactions transactions,
            PortalMyCarlosKeyInventoryWorker inventory) {
        this.transactions = Objects.requireNonNull(transactions);
        this.inventory = Objects.requireNonNull(inventory);
    }

    /** All reads and caller work share one +1 then +2 owner, EntityManager and completion. */
    <T> T withSigningInventory(Function<Optional<PortalMyCarlosKeyInventory>, T> operation) {
        Objects.requireNonNull(operation);
        return transactions.executeSigningApproval(() -> {
            PortalMyCarlosInventoryTransactions.requireApprovalOwnedTransaction();
            return operation.apply(inventory.currentForUpdate());
        });
    }

    /** Pure approval bookkeeping cannot later enter or mutate an inventory-owning scope. */
    <T> T withApprovalState(Supplier<T> operation) {
        Objects.requireNonNull(operation);
        return transactions.executeApprovalOnly(() -> {
            PortalMyCarlosInventoryTransactions.requireApprovalOwnedTransaction();
            return operation.get();
        });
    }
}
