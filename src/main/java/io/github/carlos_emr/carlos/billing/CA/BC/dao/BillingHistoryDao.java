/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingHistory;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;

@Repository
public class BillingHistoryDao extends AbstractDaoImpl<BillingHistory> {

    public BillingHistoryDao() {
        super(BillingHistory.class);
    }

    /**
     * Finds billing info for the specified master number
     *
     * @param billingMasterNo Master number to find info for
     * @return Returns the list of pairs containing {@link BillingHistory}, {@link BillingPaymentType} instances
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findByBillingMasterNo(Integer billingMasterNo) {
        // "from billing_history bh left join billing_payment_type bt on bh.payment_type_id = bt.id where bh.billingmaster_no = " +
        // billingMasterNo;

        // attempt to rewrite a left join with a cross product...
        Query query = entityManager.createQuery("SELECT bh, bpt FROM BillingHistory bh, BillingPaymentType bpt WHERE (bh.paymentTypeId = bpt.id OR bpt.id IS NULL) AND bh.billingMasterNo = :bmn");
        query.setParameter("bmn", billingMasterNo);
        return query.getResultList();
    }

    /**
     * Finds billing history for the specified master number
     *
     * @param billingMasterNo Master number to find info for
     * @return Returns the list of triples containing {@link Billingmaster}, {@link BillingHistory}, {@link BillingPaymentType} instances
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findBillingHistoryByBillingMasterNo(Integer billingMasterNo) {
        // "from billingmaster bm, billing_history bh left join billing_payment_type bt on bh.payment_type_id = bt.id
        //     where bh.billingmaster_no = bm.billingmaster_no and bm.billing_no = " + billingNo;
        Query query = entityManager.createQuery("SELECT bm, bh, bpt FROM Billingmaster bm, BillingHistory bh, BillingPaymentType bpt WHERE (bh.paymentTypeId = bpt.id OR bpt.id IS NULL ) AND bm.billingmasterNo = bh.billingMasterNo AND bm.billingmasterNo = :bmn");
        query.setParameter("bmn", billingMasterNo);
        return query.getResultList();
    }

    public Double getTotalPaidFromHistory(Integer bmn, boolean ignoreIA) {
        String historyQry = "SELECT SUM(bh.amountReceived) FROM BillingHistory bh where bh.billingMasterNo = :bmn";
        if (ignoreIA) {
            historyQry += " and bh.paymentTypeId <> " + MSPReconcile.PAYTYPE_IA;
        }
        Query query = entityManager.createQuery(historyQry);
        query.setParameter("bmn", bmn);
        List<?> result = query.getResultList();
        if (result.isEmpty()) {
            return 0.0;
        }

        String d = (String) result.get(0);
        if (d == null) {
            return 0.0;
        }

        return Double.valueOf(d);
    }
}
