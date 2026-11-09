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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingHistory;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BcBillingConstants;

@Repository
public class BillingHistoryDao extends AbstractDaoImpl<BillingHistory> {

    /**
     * History totals used by the Bill Status OWED and PAID columns, respectively.
     *
     * @param includingAdjustments payments plus internal adjustments
     * @param excludingAdjustments payments with internal adjustments excluded
     */
    public record PaymentTotals(double includingAdjustments, double excludingAdjustments) {
        public static final PaymentTotals ZERO = new PaymentTotals(0.0, 0.0);
    }

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
        PaymentTotals totals = getPaymentTotalsByBillingMasterNos(List.of(bmn)).getOrDefault(bmn, PaymentTotals.ZERO);
        return ignoreIA ? totals.excludingAdjustments() : totals.includingAdjustments();
    }

    /**
     * Loads payment and internal-adjustment totals for one report in a single query.
     * Bills without history are absent; callers should use a zero default.
     *
     * @param billingMasterNos billing line IDs selected by the report
     * @return totals keyed by billing line ID, including internal adjustments
     */
    public Map<Integer, Double> getTotalPaidFromHistoryByBillingMasterNos(Collection<Integer> billingMasterNos) {
        Map<Integer, Double> totals = new HashMap<>();
        getPaymentTotalsByBillingMasterNos(billingMasterNos)
                .forEach((id, value) -> totals.put(id, value.includingAdjustments()));
        return totals;
    }

    /**
     * Loads both variants in one query for the bulk-settle list. PAID excludes internal
     * adjustments on private bills, while OWED includes them for every bill type.
     *
     * @param billingMasterNos billing line IDs selected by the list
     * @return both totals by billing line ID; bills without history are absent
     */
    public Map<Integer, PaymentTotals> getPaymentTotalsByBillingMasterNos(Collection<Integer> billingMasterNos) {
        Map<Integer, ExactPayments> exact = new HashMap<>();
        List<Integer> ids = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(billingMasterNos));
        for (int offset = 0; offset < ids.size(); offset += 500) {
            List<Object[]> rows = entityManager.createQuery(
                    "SELECT bh.billingMasterNo, bh.amountReceived, bh.paymentTypeId FROM BillingHistory bh WHERE bh.billingMasterNo IN :ids",
                    Object[].class)
                    .setParameter("ids", ids.subList(offset, Math.min(offset + 500, ids.size())))
                    .getResultList();
            for (Object[] row : rows) {
                ExactPayments payments = exact.computeIfAbsent((Integer) row[0], ignored -> new ExactPayments());
                // Match SQL <> semantics: a null payment type is excluded from the non-adjustment sum.
                boolean includeWithoutAdjustments = row[2] != null
                        && !BcBillingConstants.PAYTYPE_IA.equals(row[2].toString());
                payments.add((String) row[1], includeWithoutAdjustments);
            }
        }
        Map<Integer, PaymentTotals> totals = new HashMap<>();
        exact.forEach((id, value) -> totals.put(id, value.totals()));
        return totals;
    }

    private static final class ExactPayments {
        private java.math.BigDecimal including = java.math.BigDecimal.ZERO;
        private java.math.BigDecimal excluding = java.math.BigDecimal.ZERO;
        private boolean unknownIncluding;
        private boolean unknownExcluding;

        private void add(String source, boolean includeWithoutAdjustments) {
            try {
                java.math.BigDecimal amount = source == null || source.isBlank()
                        ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(source);
                including = including.add(amount);
                if (includeWithoutAdjustments) excluding = excluding.add(amount);
            } catch (NumberFormatException invalid) {
                unknownIncluding = true;
                if (includeWithoutAdjustments) unknownExcluding = true;
            }
        }

        private PaymentTotals totals() {
            return new PaymentTotals(unknownIncluding ? Double.NaN : including.doubleValue(),
                    unknownExcluding ? Double.NaN : excluding.doubleValue());
        }
    }
}
