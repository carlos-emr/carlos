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
package io.github.carlos_emr.carlos.test.unit;

import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Runs Spring's real transaction lifecycle, including synchronization and the "actual transaction
 * active" flag, with no database, and counts what happened.
 *
 * <p>Register it as the {@code PlatformTransactionManager} bean for code that opens its own
 * {@code TransactionTemplate}. {@link #failBegin}, {@link #failCommit} and {@link #rollBackOnCommit} simulate
 * a transaction that cannot start, a commit whose outcome is unknown, and a commit that fails with
 * a confirmed rollback.</p>
 *
 * @since 2026-09-24
 */
public final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    // Volatile, with synchronized updates below: concurrency tests run transactions on worker threads.
    public volatile int begun;
    public volatile int commits;
    public volatile int rollbacks;
    public volatile Integer lastIsolationLevel;
    public volatile boolean failBegin;
    public volatile boolean failCommit;
    public volatile boolean rollBackOnCommit;

    private final ThreadLocal<Resource> current = new ThreadLocal<>();

    private static final class Transaction implements org.springframework.transaction.support.SmartTransactionObject {
        private Resource resource;

        @Override
        public boolean isRollbackOnly() {
            return resource != null && resource.rollbackOnly;
        }
    }

    private static final class Resource {
        private boolean rollbackOnly;
    }

    @Override
    protected Object doGetTransaction() {
        Transaction transaction = new Transaction();
        transaction.resource = current.get();
        return transaction;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((Transaction) transaction).resource != null;
    }

    @Override
    protected Object doSuspend(Object transaction) {
        Resource suspended = current.get();
        ((Transaction) transaction).resource = null;
        current.remove();
        return suspended;
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        current.set((Resource) suspendedResources);
        if (transaction != null) {
            ((Transaction) transaction).resource = (Resource) suspendedResources;
        }
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        ((Transaction) status.getTransaction()).resource.rollbackOnly = true;
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        ((Transaction) transaction).resource = null;
        current.remove();
    }

    @Override
    protected synchronized void doBegin(Object transaction, TransactionDefinition definition) {
        if (failBegin) {
            throw new CannotCreateTransactionException("database unavailable");
        }
        Resource resource = new Resource();
        ((Transaction) transaction).resource = resource;
        current.set(resource);
        begun++;
        lastIsolationLevel = definition.getIsolationLevel();
    }

    @Override
    protected synchronized void doCommit(DefaultTransactionStatus status) {
        if (failCommit) {
            throw new TransactionSystemException("commit acknowledgement lost");
        }
        if (rollBackOnCommit) {
            // A non-transaction exception from doCommit makes Spring roll back and report
            // STATUS_ROLLED_BACK, i.e. a commit that failed with a confirmed rollback.
            throw new IllegalStateException("commit refused; rolled back");
        }
        commits++;
    }

    @Override
    protected synchronized void doRollback(DefaultTransactionStatus status) {
        rollbacks++;
    }
}
