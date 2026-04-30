/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;

/**
 * Atomic per-row removal of {@code batch_billing} rows for a list of
 * (demographic, service-code) pairs. Pre-fix this loop ran inline in
 * {@code BatchBill2Action} with no {@code @Transactional} boundary —
 * mid-loop {@link RuntimeException} (stale row, FK constraint, concurrent
 * edit) committed every prior remove and left the rest unprocessed,
 * silently desyncing the queue from the operator's UI.
 *
 * @since 2026-04-30
 */
@Service
@Transactional
public class BatchBillingRemovalService {

    private final BatchBillingDAO batchBillingDAO;

    public BatchBillingRemovalService(BatchBillingDAO batchBillingDAO) {
        this.batchBillingDAO = batchBillingDAO;
    }

    /**
     * One row per (demographicNo, serviceCode). Throws
     * {@link RemovalRowMissingException} when a lookup returns an empty
     * list — pre-fix this NPE'd inside {@code .get(0)}.
     */
    public record Row(int demographicNo, String serviceCode) { }

    public static class RemovalRowMissingException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Row row;
        public RemovalRowMissingException(Row row) {
            super("BatchBilling row not found for demographicNo=" + row.demographicNo()
                    + " serviceCode=" + row.serviceCode());
            this.row = row;
        }
        public Row row() {
            return row;
        }
    }

    /**
     * Atomically remove every BatchBilling row matched by the given
     * (demographicNo, serviceCode) lookups. Any throw rolls back the
     * entire batch.
     */
    public void removeAll(List<Row> rows) {
        for (Row row : rows) {
            List<BatchBilling> matches = batchBillingDAO.find(row.demographicNo(), row.serviceCode());
            if (matches == null || matches.isEmpty()) {
                throw new RemovalRowMissingException(row);
            }
            BatchBilling target = matches.get(0);
            batchBillingDAO.remove(target.getId());
        }
    }
}
