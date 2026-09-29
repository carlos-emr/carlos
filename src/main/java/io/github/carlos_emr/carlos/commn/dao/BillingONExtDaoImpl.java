/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.carlos.commn.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Query;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.stereotype.Repository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author mweston4
 */
@Repository
@SuppressWarnings("unchecked")
public class BillingONExtDaoImpl extends AbstractDaoImpl<BillingONExt> implements BillingONExtDao {
    public final static String KEY_PAYMENT = "payment";
    public final static String KEY_REFUND = "refund";
    public final static String KEY_DISCOUNT = "discount";
    public final static String KEY_CREDIT = "credit";
    public final static String KEY_PAY_DATE = "payDate";
    public final static String KEY_PAY_METHOD = "payMethod";
    public final static String KEY_TOTAL = "total";
    public final static String KEY_GST = "gst";

    public BillingONExtDaoImpl() {
        super(BillingONExt.class);
    }

    @Override
    public List<BillingONExt> find(String key, String value) {
        Query q = createQuery("q", "q.keyVal = ?1 AND q.value = ?2");
        q.setParameter(1, key);
        q.setParameter(2, value);
        return q.getResultList();
    }

    @Override
    public List<BillingONExt> findByBillingNoAndKey(Integer billingNo, String key) {
        String sql = "select bExt from BillingONExt bExt where bExt.billingNo=?1 and bExt.keyVal=?2 order by bExt.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, billingNo);
        query.setParameter(2, key);

        List<BillingONExt> results = query.getResultList();

        return results;
    }

    @Override
    public List<BillingONExt> findByBillingNoAndPaymentIdAndKey(Integer billingNo, Integer paymentId, String key) {
        String sql = "select bExt from BillingONExt bExt where bExt.billingNo=?1 and bExt.paymentId=?2 and bExt.keyVal=?3 order by bExt.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, billingNo);
        query.setParameter(2, paymentId);
        query.setParameter(3, key);
        List<BillingONExt> results = query.getResultList();

        return results;
    }

    @Override
    public String getPayMethodDesc(BillingONExt bExt) {
        BillingPaymentTypeDao payMethod = SpringUtils.getBean(BillingPaymentTypeDao.class);
        Integer payMethodId = Integer.parseInt(bExt.getValue());
        BillingPaymentType payMethodDesc = payMethod.find(payMethodId);
        return payMethodDesc.getPaymentType();
    }

    @Override
    public BigDecimal getPayment(BillingONPayment paymentRecord) {

        String sql = "select bExt from BillingONExt bExt where paymentId=?1 and billingNo=?2 and keyVal=?3";
        Query query = entityManager.createQuery(sql);

        query.setParameter(1, paymentRecord.getId());
        query.setParameter(2, paymentRecord.getBillingNo());
        query.setParameter(3, "payment");

        List<BillingONExt> results = query.getResultList();

        BigDecimal amtPaid = null;
        if (results.size() > 1) {
            MiscUtils.getLogger().warn("Multiple payments found for Payment Id:" + paymentRecord.getId());
        }

        if (results.isEmpty()) {
            amtPaid = new BigDecimal("0.00");
        } else {
            BillingONExt payment = results.get(0);
            try {
                amtPaid = new BigDecimal(payment.getValue());
            } catch (NumberFormatException e) {
                // A malformed currency value would silently understate the
                // displayed payment total. Promote to ERROR with sanitized
                // context (paymentId + billingNo) so reconciliation can find
                // the offending row, and rethrow as the typed billing
                // exception so callers see the corruption rather than a
                // misleading $0.00.
                MiscUtils.getLogger().error("billing_on_ext.payment for paymentId={} billingNo={} is not a valid currency amount",
                        LogSafe.sanitize(String.valueOf(paymentRecord.getId())),
                        LogSafe.sanitize(String.valueOf(paymentRecord.getBillingNo())), e);
                throw new BillingValidationException(
                        "Corrupt billing_on_ext.payment value; see logs for paymentId/billingNo", e);
            }
        }
        return amtPaid;
    }

    @Override
    public BigDecimal getRefund(BillingONPayment paymentRecord) {
        String sql = "select bExt from BillingONExt bExt where paymentId=?1 and billingNo=?2 and keyVal=?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, paymentRecord.getId());
        query.setParameter(2, paymentRecord.getBillingNo());
        query.setParameter(3, "refund");

        List<BillingONExt> results = query.getResultList();

        BigDecimal amtRefunded = null;
        if (results.size() > 1) {
            MiscUtils.getLogger().warn("Multiple payments found for Payment Id:" + paymentRecord.getId());
        }

        if (results.isEmpty()) {
            amtRefunded = new BigDecimal("0.00");
        } else {
            BillingONExt refund = results.get(0);
            try {
                amtRefunded = new BigDecimal(refund.getValue());
            } catch (NumberFormatException e) {
                // Same reasoning as getPayment: silently understated refund
                // totals are worse than failing loudly. Rethrow.
                MiscUtils.getLogger().error("billing_on_ext.refund for paymentId={} billingNo={} is not a valid currency amount",
                        LogSafe.sanitize(String.valueOf(paymentRecord.getId())),
                        LogSafe.sanitize(String.valueOf(paymentRecord.getBillingNo())), e);
                throw new BillingValidationException(
                        "Corrupt billing_on_ext.refund value; see logs for paymentId/billingNo", e);
            }
        }
        return amtRefunded;
    }

    @Override
    public BillingONExt getRemitTo(BillingONCHeader1 bCh1) {
        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3 order by bExt.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '1');
        query.setParameter(3, "remitTo");

        List<BillingONExt> results = query.getResultList();

        return singleExtOrNull(results, "remitTo", bCh1.getId(), '1');
    }

    @Override
    public BillingONExt getBillTo(BillingONCHeader1 bCh1) {
        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3 order by bExt.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '1');
        query.setParameter(3, "billTo");

        List<BillingONExt> results = query.getResultList();

        return singleExtOrNull(results, "billTo", bCh1.getId(), '1');
    }

    @Override
    public BillingONExt getBillToInactive(BillingONCHeader1 bCh1) {
        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3 order by bExt.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '0');
        query.setParameter(3, "billTo");

        List<BillingONExt> results = query.getResultList();

        return singleExtOrNull(results, "billTo", bCh1.getId(), '0');
    }

    @Override
    public BillingONExt getDueDate(BillingONCHeader1 bCh1) {
        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3 order by bExt.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '1');
        query.setParameter(3, "dueDate");

        List<BillingONExt> results = query.getResultList();

        return singleExtOrNull(results, "dueDate", bCh1.getId(), '1');
    }

    @Override
    public BillingONExt getUseBillTo(BillingONCHeader1 bCh1) {
        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3 order by bExt.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '1');
        query.setParameter(3, "useBillTo");

        List<BillingONExt> results = query.getResultList();

        return singleExtOrNull(results, "useBillTo", bCh1.getId(), '1');
    }

    private BillingONExt singleExtOrNull(List<BillingONExt> results, String key, Integer billingNo, char status) {
        // These ext lookups are modeled as a single current row per
        // billingNo/key/status combination. Silently picking the newest row
        // would hide duplicate-active data and make billing edits
        // non-deterministic for operators.
        if (results.size() > 1) {
            String statusLabel = status == '1' ? "active" : "inactive";
            MiscUtils.getLogger().error("Duplicate {} billing_on_ext {} rows for invoice number: {}",
                    statusLabel,
                    LogSafe.sanitize(key),
                    LogSafe.sanitize(String.valueOf(billingNo)));
            throw new BillingDataLoadException(
                    "duplicate " + statusLabel + " billing_on_ext " + key,
                    BillingDataLoadException.Phase.DAO_QUERY,
                    Map.of("billingNo", String.valueOf(billingNo), "key", key, "status", String.valueOf(status)));
        }
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<BillingONExt> find(Integer billingNo, String key, Date start, Date end) {
        Query q = createQuery("b",
                "b.billingNo = ?1 AND b.keyVal = ?2 AND b.dateTime >= ?3 AND b.dateTime <= ?4");
        q.setParameter(1, billingNo);
        q.setParameter(2, key);
        q.setParameter(3, start);
        q.setParameter(4, end);
        return q.getResultList();
    }

    @Override
    public List<BillingONExt> findByBillingNoAndPaymentNo(int billingNo, int paymentId) {

        String sql = "select bExt from BillingONExt bExt where bExt.paymentId=?1 and bExt.billingNo=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, paymentId);
        query.setParameter(2, billingNo);

        List<BillingONExt> results = query.getResultList();

        return results;
    }

    @Override
    public List<BillingONExt> getClaimExtItems(int billingNo) {
        Query query = entityManager.createQuery("select ext from BillingONExt ext where ext.billingNo = ?1");
        query.setParameter(1, billingNo);
        return query.getResultList();
    }

    @Override
    public List<BillingONExt> getBillingExtItems(String billingNo) {
        Integer billingId = parseBillingNo(billingNo);
        if (billingId == null) {
            return java.util.Collections.emptyList();
        }
        Query query = entityManager
                .createQuery("select ext from BillingONExt ext where ext.billingNo = ?1 and ext.status='1' ");
        query.setParameter(1, billingId);
        return query.getResultList();
    }

    @Override
    public List<BillingONExt> getInactiveBillingExtItems(String billingNo) {
        Integer billingId = parseBillingNo(billingNo);
        if (billingId == null) {
            return java.util.Collections.emptyList();
        }
        Query query = entityManager
                .createQuery("select ext from BillingONExt ext where ext.billingNo = ?1 and ext.status='0' ");
        query.setParameter(1, billingId);
        return query.getResultList();
    }

    private static Integer parseBillingNo(String billingNo) {
        if (billingNo == null || billingNo.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(billingNo);
        } catch (NumberFormatException e) {
            throw new BillingValidationException(
                    "BillingONExtDao: malformed billingNo [" + LogSafe.sanitize(billingNo) + "]",
                    e);
        }
    }

    @Override
    public BigDecimal getAccountVal(int billingNo, String key) {
        BigDecimal val = new BigDecimal("0.00").setScale(2, BigDecimal.ROUND_HALF_UP);
        if (!KEY_TOTAL.equals(key) && !KEY_PAYMENT.equals(key) && !KEY_DISCOUNT.equals(key) && !KEY_REFUND.equals(key)
                && !KEY_CREDIT.equals(key)) {
            return val;
        }
        Query query = entityManager
                .createQuery("select ext from BillingONExt ext where ext.billingNo = ?1 and ext.keyVal = ?2");
        query.setParameter(1, billingNo);
        query.setParameter(2, key);
        BillingONExt ext = null;
        try {
            ext = (BillingONExt) query.getSingleResult();
        } catch (NoResultException e) {
            // Expected: no ext row for this (billingNo, key) tuple — return zeroed default below.
        }
        if (ext != null) {
            try {
                val = BillingMoney.parseOptionalNonNegativeAmount(ext.getValue(), key)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
            } catch (BillingValidationException e) {
                MiscUtils.getLogger().error(
                        "billing_on_ext.{} for billingNo={} is not a valid currency amount",
                        LogSafe.sanitize(key),
                        LogSafe.sanitize(String.valueOf(billingNo)), e);
                throw new BillingValidationException(
                        "Corrupt billing_on_ext." + key + " value; see logs for billingNo", e);
            }
        }
        return val;
    }

    @Override
    public BillingONExt getClaimExtItem(Integer billingNo, Integer demographicNo, String keyVal)
            throws NonUniqueResultException {
        // Three optional clauses bound by NAMED parameters. JPA §4.6.4.2
        // requires positional parameters to be contiguous from 1, so the
        // earlier `?1, ?3` whitelist would fail Hibernate strict mode.
        // Named params are unconstrained on order and only-bind-what-you-use.
        boolean hb = (billingNo != null);
        boolean hd = (demographicNo != null);
        boolean hk = (keyVal != null);
        // Defensive: every prod caller passes at least billingNo, but the
        // pre-fix code's `else { select ext from BillingONExt ext }` would
        // scan the whole ext table on misuse and either NonUniqueResultException
        // (>1 row) or return a single arbitrary row. Fail fast so the bug
        // is caught at the call site rather than at SELECT-time on a busy
        // production schema.
        if (!hb && !hd && !hk) {
            throw new IllegalArgumentException(
                    "BillingONExtDao.getClaimExtItem requires at least one filter (billingNo, demographicNo, or keyVal)");
        }
        String hql;
        if (hb && hd && hk) {
            hql = "select ext from BillingONExt ext where ext.billingNo = :billingNo and ext.demographicNo = :demographicNo and ext.keyVal = :keyVal";
        } else if (hb && hd) {
            hql = "select ext from BillingONExt ext where ext.billingNo = :billingNo and ext.demographicNo = :demographicNo";
        } else if (hb && hk) {
            hql = "select ext from BillingONExt ext where ext.billingNo = :billingNo and ext.keyVal = :keyVal";
        } else if (hd && hk) {
            hql = "select ext from BillingONExt ext where ext.demographicNo = :demographicNo and ext.keyVal = :keyVal";
        } else if (hb) {
            hql = "select ext from BillingONExt ext where ext.billingNo = :billingNo";
        } else if (hd) {
            hql = "select ext from BillingONExt ext where ext.demographicNo = :demographicNo";
        } else { // hk only — already gated above for the all-null case
            hql = "select ext from BillingONExt ext where ext.keyVal = :keyVal";
        }
        Query query = entityManager.createQuery(hql);
        if (hb) query.setParameter("billingNo", billingNo);
        if (hd) query.setParameter("demographicNo", demographicNo);
        if (hk) query.setParameter("keyVal", keyVal);
        BillingONExt res = null;
        try {
            res = (BillingONExt) query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
        return res;
    }

    @Override
    public void setExtItem(int billingNo, int demographicNo, String keyVal, String value, Date dateTime, char status)
            throws NonUniqueResultException {
        BillingONExt ext = getClaimExtItem(billingNo, demographicNo, keyVal);
        if (ext != null) {
            ext.setValue(value);
            ext.setDateTime(dateTime);
            ext.setStatus(status);
            this.merge(ext);
        } else {
            BillingONExt res = new BillingONExt();
            res.setBillingNo(billingNo);
            res.setDemographicNo(demographicNo);
            res.setKeyVal(keyVal);
            res.setValue(value);
            res.setDateTime(dateTime);
            res.setStatus(status);
            this.persist(res);
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public boolean isNumberKey(String key) {
        if (KEY_PAYMENT.equalsIgnoreCase(key)
                || KEY_DISCOUNT.equalsIgnoreCase(key)
                || KEY_TOTAL.equalsIgnoreCase(key)
                || KEY_REFUND.equalsIgnoreCase(key)
                || KEY_CREDIT.equals(key)) {
            return true;
        }
        return false;
    }
}
