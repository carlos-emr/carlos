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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transaction-local participating unit identity shared by the writer and JDBC admission guard. */
public final class CdsImportUnitContext {
    private record ActiveUnit(String runId, long epoch, String unitKey) { }
    private CdsImportUnitContext() { }

    public static void bind(String runId, long epoch, String unitKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new CdsImportException("PARTICIPATING_UNIT_REQUIRED");
        TransactionSynchronizationManager.bindResource(CdsImportUnitContext.class, new ActiveUnit(runId, epoch, unitKey));
    }
    public static void unbind() {
        TransactionSynchronizationManager.unbindResource(CdsImportUnitContext.class);
    }
    public static String requireActive(String runId, long epoch, String unitKey) {
        Object resource = TransactionSynchronizationManager.getResource(CdsImportUnitContext.class);
        if (!(resource instanceof ActiveUnit active) || !active.runId().equals(runId) || active.epoch() != epoch
                || (unitKey != null && !active.unitKey().equals(unitKey))
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CdsImportException("PARTICIPATING_UNIT_REQUIRED");
        }
        return active.unitKey();
    }
    public static boolean isActiveForMaintenance() {
        return TransactionSynchronizationManager.getResource(CdsImportUnitContext.class) instanceof ActiveUnit
                && TransactionSynchronizationManager.isActualTransactionActive();
    }
}
