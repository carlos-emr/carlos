/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.math.BigDecimal;
import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link BillingOnItemPaymentDao} for healthcare billing data access.
 *
 * @since 2001
 */

public class BillingOnItemPaymentDaoImpl extends AbstractDaoImpl<BillingOnItemPayment> implements BillingOnItemPaymentDao {
    /** Constructs this DAO for the {@link BillingOnItemPayment} entity class. */

    public BillingOnItemPaymentDaoImpl() {
        super(BillingOnItemPayment.class);
    }

    /** {@inheritDoc} */

    @Override
    public BillingOnItemPayment findByPaymentIdAndItemId(int paymentId, int itemId) {
        Query query = entityManager.createQuery("select boip from BillingOnItemPayment boip where boip.billingOnPaymentId = ?1 and boip.billingOnItemId = ?2");
        query.setParameter(1, paymentId);
        query.setParameter(2, itemId);
        return getSingleResultOrNull(query);
    }

    /** {@inheritDoc} */

    @Override
    @SuppressWarnings("unchecked")
    public List<BillingOnItemPayment> getAllByItemId(int itemId) {
        Query query = entityManager.createQuery("select boip from BillingOnItemPayment boip where boip.billingOnItemId =?1");
        query.setParameter(1, itemId);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    @SuppressWarnings("unchecked")
    public List<BillingOnItemPayment> getItemsByPaymentId(int paymentId) {
        Query query = entityManager.createQuery("select boip from BillingOnItemPayment boip where boip.billingOnPaymentId = ?1");
        query.setParameter(1, paymentId);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public BigDecimal getAmountPaidByItemId(int itemId) {
        Query query = entityManager.createQuery("select sum(boip.paid) from BillingOnItemPayment boip where boip.billingOnItemId = ?1");
        query.setParameter(1, itemId);
        BigDecimal paid = null;
        try {
            paid = (BigDecimal) query.getSingleResult();
        } catch (Exception e) {
        }

        if (paid == null) {
            paid = new BigDecimal("0.00");
        }

        return paid;
    }

    /** {@inheritDoc} */

    @Override
    public List<BillingOnItemPayment> getItemPaymentByInvoiceNoItemId(Integer ch1_id, Integer item_id) {
        String sql = "select bPay from BillingOnItemPayment bPay where bPay.ch1Id= ?1 and bPay.billingOnItemId = ?2 ";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, ch1_id);
        query.setParameter(2, item_id);

        @SuppressWarnings("unchecked")
        List<BillingOnItemPayment> results = query.getResultList();

        return results;
    }


    /** {@inheritDoc} */

    @Override
    public List<BillingOnItemPayment> findByBillingNo(int billingNo) {
        Query query = entityManager.createQuery("select boip from BillingOnItemPayment boip where boip.ch1Id = ?1");
        query.setParameter(1, billingNo);
        return query.getResultList();
    }
}
