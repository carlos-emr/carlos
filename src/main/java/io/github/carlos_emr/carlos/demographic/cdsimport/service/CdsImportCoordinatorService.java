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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsImportCoordinatorDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportControlDto;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** One physical unpooled coordinator excludes duplicate preflight and execution across nodes. */
@Service
public class CdsImportCoordinatorService {
    private final CdsImportCoordinatorDao coordinator;

    @Autowired
    public CdsImportCoordinatorService(CdsImportCoordinatorDao coordinator) {
        this.coordinator = coordinator;
    }

    /** Compatibility for existing explicitly constructed runtimes; Spring injects the DAO. */
    public CdsImportCoordinatorService(OscarTrackingBasicDataSource dataSource) {
        this(new CdsImportCoordinatorDao(dataSource));
    }

    public Owner acquire() {
        CdsImportLedgerService.requireNoTransaction();
        try {
            var lease = coordinator.acquire();
            if (lease == null) throw new CdsImportException("IMPORT_COORDINATOR_BUSY");
            try { return new Owner(lease); }
            catch (RuntimeException | Error failure) {
                try { lease.close(); }
                catch (SQLException | RuntimeException | Error cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            if (failure instanceof CdsImportException known) throw known;
            throw new CdsImportException("IMPORT_COORDINATOR_UNAVAILABLE", false, failure);
        }
    }

    public static final class Owner implements AutoCloseable {
        private final CdsImportCoordinatorDao.Lease lease;
        private boolean closed;

        private Owner(CdsImportCoordinatorDao.Lease lease) { this.lease = lease; }

        public void check() {
            requireOpen();
            try {
                if (!lease.physicalOwned()) throw new CdsImportException("IMPORT_COORDINATOR_LOST");
            } catch (SQLException failure) {
                throw new CdsImportException("IMPORT_COORDINATOR_LOST", false, failure);
            }
        }

        public CdsImportControlDto control(String runId) {
            requireOpen();
            try {
                return lease.control(runId);
            } catch (SQLException failure) {
                // A malformed/unavailable control observation on a healthy
                // physical owner is an operational failure that needs a report.
                // A genuinely lost owner retains the fenced handoff semantics.
                String code=connectionLost(failure) ? "IMPORT_COORDINATOR_LOST" : "IMPORT_CONTROL_UNAVAILABLE";
                try { code=lease.physicalOwned() ? "IMPORT_CONTROL_UNAVAILABLE" : "IMPORT_COORDINATOR_LOST"; }
                catch (SQLException secondary) {
                    if (secondary != failure) failure.addSuppressed(secondary);
                    if (connectionLost(secondary)) code="IMPORT_COORDINATOR_LOST";
                } catch (RuntimeException secondary) {
                    failure.addSuppressed(secondary);
                } catch (Error fatal) {
                    fatal.addSuppressed(failure);
                    throw fatal;
                }
                throw new CdsImportException(code, false, failure);
            }
        }

        private static boolean connectionLost(SQLException failure) {
            String state=failure.getSQLState();
            return state != null && state.startsWith("08");
        }

        private void requireOpen() {
            if (closed) throw new CdsImportException("IMPORT_COORDINATOR_LOST");
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                lease.close();
            } catch (SQLException failure) {
                throw new CdsImportException("IMPORT_COORDINATOR_RELEASE_FAILED", false, failure);
            }
        }
    }
}
