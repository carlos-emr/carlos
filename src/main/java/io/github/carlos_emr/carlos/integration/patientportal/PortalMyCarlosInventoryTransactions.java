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

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns the WHOLE operation; retry is possible only after a confirmed rollback and full cleanup. */
@Service
class PortalMyCarlosInventoryTransactions {
    static final int MAX_ATTEMPTS = 3;
    private static final ThreadLocal<Completion> OWNED = new ThreadLocal<>();
    private final TransactionTemplate transactions;
    private final EntityManagerFactory factory;
    private final ProviderLabRoutingDao coordination;

    PortalMyCarlosInventoryTransactions(PlatformTransactionManager manager,
            EntityManagerFactory factory, ProviderLabRoutingDao coordination) {
        this.factory = Objects.requireNonNull(factory);
        this.coordination = Objects.requireNonNull(coordination);
        transactions = new TransactionTemplate(Objects.requireNonNull(manager));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactions.setTimeout(15);
    }

    void requireStandalone() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(factory) || OWNED.get() != null) {
            throw new IllegalStateException("inventory mutation requires a standalone operation without an inherited persistence context");
        }
    }

    static void requireOwnedTransaction() {
        if (OWNED.get() == null || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("inventory worker requires the whole operation's owning transaction");
        }
    }

    /** A preparation which threw has no exact saved result and must never be automatically repeated. */
    static void preparationFailed() {
        Completion completion = OWNED.get();
        if (completion != null) completion.replaySafe = false;
    }

    <T> T execute(Supplier<T> operation) {
        requireStandalone();
        Objects.requireNonNull(operation);
        Set<EntityManager> previousManagers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int attempt = 1; ; attempt++) {
            requireStandalone();
            Completion completion = new Completion();
            try {
                return transactions.execute(status -> {
                    if (!status.isNewTransaction() || !TransactionSynchronizationManager.isActualTransactionActive()
                            || !TransactionSynchronizationManager.isSynchronizationActive()) {
                        throw new IllegalStateException("a genuinely new owning transaction is required");
                    }
                    TransactionSynchronizationManager.registerSynchronization(completion);
                    Object resource = TransactionSynchronizationManager.getResource(factory);
                    if (!(resource instanceof EntityManagerHolder holder)
                            || !holder.isSynchronizedWithTransaction() || !holder.getEntityManager().isOpen()
                            || !previousManagers.add(holder.getEntityManager())) {
                        throw new IllegalStateException("a fresh transaction-owned EntityManager is required");
                    }
                    OWNED.set(completion);
                    try {
                        // First database operation: an older security/catalogue snapshot cannot precede it.
                        coordination.lockRoutingReport(PortalMyCarlosKeyInventoryService.COORDINATION_KEY);
                        return operation.get();
                    } finally {
                        OWNED.remove();
                    }
                });
            } catch (RuntimeException failure) {
                // execute() can also throw AFTER a successful commit (for example from audit).
                // Exception type alone NEVER establishes that retry is safe.
                boolean cleaned = !TransactionSynchronizationManager.isActualTransactionActive()
                        && !TransactionSynchronizationManager.isSynchronizationActive()
                        && !TransactionSynchronizationManager.hasResource(factory) && OWNED.get() == null;
                if (attempt >= MAX_ATTEMPTS || !completion.rolledBackBeforeCommit() || !cleaned
                        || !isRollbackConflict(failure)) throw failure;
            }
        }
    }

    private static boolean isRollbackConflict(Throwable error) {
        if (error instanceof SecurityException || error instanceof IllegalArgumentException) return false;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                Set<SQLException> chain = Collections.newSetFromMap(new IdentityHashMap<>());
                for (SQLException item = sql; item != null && chain.add(item); item = item.getNextException()) {
                    String state = item.getSQLState();
                    if ((item.getErrorCode() == 1020 && "HY000".equals(state))
                            || (item.getErrorCode() == 1213 && "40001".equals(state))
                            || (item.getErrorCode() == 1205 && "HY000".equals(state))) return true;
                }
            }
        }
        return false;
    }

    private static final class Completion implements TransactionSynchronization {
        private int status = STATUS_UNKNOWN;
        private boolean callbackReceived;
        private boolean commitStarted;
        private boolean replaySafe = true;
        @Override public void beforeCommit(boolean readOnly) { commitStarted = true; }
        @Override public void afterCommit() { status = STATUS_COMMITTED; }
        @Override public void afterCompletion(int completed) { status = completed; callbackReceived = true; }
        boolean rolledBackBeforeCommit() {
            return callbackReceived && status == STATUS_ROLLED_BACK && !commitStarted && replaySafe;
        }
    }
}
