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
 * (demographic, service-code) pairs. {@code @Transactional} so a
 * mid-loop {@link RuntimeException} (stale row, FK constraint, concurrent
 * edit) rolls back every prior remove rather than silently desyncing
 * the queue from the operator's UI.
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

    /** One row per (demographicNo, serviceCode). */
    public record Row(int demographicNo, String serviceCode) { }

    /**
     * Thrown by {@link #removeAll(List)} when a {@code (demographicNo,
     * serviceCode)} lookup returns an empty list. Carries the offending
     * {@link Row} so the action layer can surface it to the operator
     * instead of NPEing on {@code .get(0)}.
     */
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
