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

import java.util.Date;

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONProcDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONProc;
/**
 * Side-effect service that emits one row to the {@code billing_on_proc}
 * audit-log table per significant ON-billing state change. Replaces the
 * legacy {@code JdbcBillingLog} shim — same write, but the class now lives
 * in the {@code service/} tier per the
 * {@code service = side effects (mutation, file I/O, audit, mutation)}
 * contract documented in {@code service/package-info.java}.
 *
 * <p>This is the only audit-emission seam for the ON billing module —
 * billing-correction status flips, RA imports, error-report mutations
 * all flow through {@link #addBillingLog} so the audit trail stays
 * uniform.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class BillingONAuditLogService {

    private final BillingONProcDao dao;

    BillingONAuditLogService(BillingONProcDao dao) {
        this.dao = dao;
    }

    /**
     * Emits one audit row.
     *
     * @param providerNo provider performing the action.
     * @param action short verb-phrase identifier ({@code "updateBillingStatus"}, etc.).
     * @param comment free-form context; may be empty.
     * @param object the target identifier (typically a billing-no or claim-no).
     */
    public void addBillingLog(String providerNo, String action, String comment, String object) {
        BillingONProc b = new BillingONProc();
        b.setCreator(providerNo);
        b.setAction(action);
        b.setComment(comment);
        b.setObject(object);
        b.setCreateDateTime(new Date());
        dao.persist(b);
    }
}
