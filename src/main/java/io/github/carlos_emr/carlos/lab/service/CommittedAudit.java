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
package io.github.carlos_emr.carlos.lab.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Writes an audit entry only once the change it describes has committed.
 *
 * <p>{@code LogAction} writes asynchronously and outside the caller's transaction, so an entry
 * written inside a transaction survives that transaction's rollback. An HRM patient match that
 * routed the report to the MRP and then rolled back would still leave "route to MRP" in the audit
 * log, telling a privacy officer that a provider received a report they never did. Inside a
 * transaction the entry is deferred to {@code afterCommit}; outside one it is written at once.</p>
 *
 * @since 2026-09-26
 */
final class CommittedAudit {

    private CommittedAudit() {
    }

    static void write(Runnable entry) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    entry.run();
                }
            });
        } else {
            entry.run();
        }
    }
}
