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
 * {@code TransactionTemplate}. {@link #failBegin} and {@link #failCommit} simulate a transaction that
 * cannot start and a commit whose outcome is unknown.</p>
 *
 * @since 2026-09-24
 */
public final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    public int begun;
    public int commits;
    public int rollbacks;
    public Integer lastIsolationLevel;
    public boolean failBegin;
    public boolean failCommit;

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        if (failBegin) {
            throw new CannotCreateTransactionException("database unavailable");
        }
        begun++;
        lastIsolationLevel = definition.getIsolationLevel();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        if (failCommit) {
            throw new TransactionSystemException("commit acknowledgement lost");
        }
        commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbacks++;
    }
}
