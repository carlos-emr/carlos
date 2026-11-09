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

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.sql.SQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Holds the existing nontransactional legacy form stable only for the bounded patient source transaction. */
@Service
public class CdsExportSourceSnapshotService {
    private final OscarTrackingBasicDataSource source;

    public CdsExportSourceSnapshotService(OscarTrackingBasicDataSource source) {
        this.source = source;
    }

    public CdsExportSourceReadLock acquire(Runnable checkpoint) throws SQLException {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        checkpoint.run();
        var lease =
                CdsExportSourceReadLock.acquireUnpooled(
                        source.openUnpooledCdsMaintenanceConnection());
        try {
            checkpoint.run();
            return lease;
        } catch (RuntimeException | Error failed) {
            try {
                lease.close();
            } catch (SQLException cleanup) {
                failed.addSuppressed(cleanup);
            }
            throw failed;
        }
    }
}
