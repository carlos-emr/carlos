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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;

/**
 * Wraps the {@link Billing} + {@link BillingDetail} persist pair under a
 * single {@code @Transactional} boundary so detail-persist failure rolls back
 * the parent {@code Billing} insert. Pre-fix the two persists ran inline in
 * {@code FluBillingAdd2Action.execute()} with no tx boundary — a detail
 * insert failure left an orphan parented-only Billing row.
 *
 * @since 2026-04-30
 */
@Service
@Transactional
public class FluBillingPersistenceService {

    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;

    public FluBillingPersistenceService(BillingDao billingDao, BillingDetailDao billingDetailDao) {
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
    }

    /**
     * Atomically persist the {@code Billing} + paired {@code BillingDetail}.
     * Throwing from either persist rolls both back.
     *
     * @return the persisted Billing id (its primary key) — guaranteed non-null
     *         non-zero if this method returns normally.
     */
    public Integer persistFluBilling(Billing billing, BillingDetail detail) {
        billingDao.persist(billing);
        // Ensure the detail's foreign key points at the parent we just wrote;
        // the action used to set this AFTER persist, which is fine since the
        // PK is generated on persist — keeping that contract here.
        detail.setBillingNo(billing.getId());
        billingDetailDao.persist(detail);
        return billing.getId();
    }
}
