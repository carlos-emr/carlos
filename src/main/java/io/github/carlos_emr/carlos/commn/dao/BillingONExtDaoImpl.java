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
import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.stereotype.Repository;

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

    /** {@inheritDoc} */

    @Override
    public List<BillingONExt> find(String key, String value) {
        Query q = createQuery("q", "q.keyVal = ?1 AND q.value = ?2");
        q.setParameter(1, key);
        q.setParameter(2, value);
        return q.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<BillingONExt> findByBillingNoAndKey(Integer billingNo, String key) {
        String sql = "select bExt from BillingONExt bExt where bExt.billingNo=?1 and bExt.keyVal=?2 order by bExt.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, billingNo);
        query.setParameter(2, key);

        List<BillingONExt> results = query.getResultList();

        return results;
    }

    /** {@inheritDoc} */

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

    /** {@inheritDoc} */

    @Override
    public String getPayMethodDesc(BillingONExt bExt) {
        BillingPaymentTypeDao payMethod = SpringUtils.getBean(BillingPaymentTypeDao.class);
        Integer payMethodId = Integer.parseInt(bExt.getValue());
        BillingPaymentType payMethodDesc = payMethod.find(payMethodId);
        return payMethodDesc.getPaymentType();
    }

    /** {@inheritDoc} */

    @Override
    public BigDecimal getPayment(BillingONPayment paymentRecord) {

        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and keyVal=?2";
        Query query = entityManager.createQuery(sql);

        query.setParameter(1, paymentRecord.getBillingNo());
        query.setParameter(2, "payment");

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
                MiscUtils.getLogger().warn("Payment not a valid currency amount (" + payment.getValue()
                        + ") for Payment Id:" + paymentRecord.getId());
                amtPaid = new BigDecimal("0.00");
            }
        }
        return amtPaid;
    }

    /** {@inheritDoc} */

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
                MiscUtils.getLogger().warn("Refund not a valid currency amount (" + refund.getValue()
                        + ") for Payment Id:" + paymentRecord.getId());
                amtRefunded = new BigDecimal("0.00");
            }
        }
        return amtRefunded;
    }

    /** {@inheritDoc} */

    @Override
    public BillingONExt getRemitTo(BillingONCHeader1 bCh1) {
        BillingONExt bExt = null;

        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '1');
        query.setParameter(3, "remitTo");

        List<BillingONExt> results = query.getResultList();

        if (results.size() > 1) {
            MiscUtils.getLogger().warn("More than one active remit to result for invoice number: " + bCh1.getId());
        }

        if (!results.isEmpty())
            bExt = results.get(0);
        return bExt;
    }

    /** {@inheritDoc} */

    @Override
    public BillingONExt getBillTo(BillingONCHeader1 bCh1) {
        BillingONExt bExt = null;

        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '1');
        query.setParameter(3, "billTo");

        List<BillingONExt> results = query.getResultList();

        if (results.size() > 1) {
            MiscUtils.getLogger().warn("More than one active bill to result for invoice number: " + bCh1.getId());
        }

        if (!results.isEmpty())
            bExt = results.get(0);
        return bExt;
    }

    /** {@inheritDoc} */

    @Override
    public BillingONExt getBillToInactive(BillingONCHeader1 bCh1) {
        BillingONExt bExt = null;

        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '0');
        query.setParameter(3, "billTo");

        List<BillingONExt> results = query.getResultList();

        if (results.size() > 1) {
            MiscUtils.getLogger().warn("More than one inactive bill to result for invoice number: " + bCh1.getId());
        }

        if (!results.isEmpty())
            bExt = results.get(0);
        return bExt;
    }

    /** {@inheritDoc} */

    @Override
    public BillingONExt getDueDate(BillingONCHeader1 bCh1) {
        BillingONExt bExt = null;

        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '1');
        query.setParameter(3, "dueDate");

        List<BillingONExt> results = query.getResultList();

        if (results.size() > 1) {
            MiscUtils.getLogger().warn("More than one active dueDate result for invoice number: " + bCh1.getId());
        }

        if (!results.isEmpty()) {
            bExt = results.get(0);
        }

        return bExt;
    }

    /** {@inheritDoc} */

    @Override
    public BillingONExt getUseBillTo(BillingONCHeader1 bCh1) {
        BillingONExt bExt = null;

        String sql = "select bExt from BillingONExt bExt where billingNo=?1 and status=?2 and keyVal=?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, '1');
        query.setParameter(3, "useBillTo");

        List<BillingONExt> results = query.getResultList();

        if (results.size() > 1) {
            MiscUtils.getLogger().warn("More than one active useBillTo result for invoice number: " + bCh1.getId());
        }

        if (!results.isEmpty()) {
            bExt = results.get(0);
        }

        return bExt;
    }

    /** {@inheritDoc} */

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

    /** {@inheritDoc} */

    @Override
    public List<BillingONExt> findByBillingNoAndPaymentNo(int billingNo, int paymentId) {

        String sql = "select bExt from BillingONExt bExt where bExt.paymentId=?1 and bExt.billingNo=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, paymentId);
        query.setParameter(2, billingNo);

        List<BillingONExt> results = query.getResultList();

        return results;
    }

    /** {@inheritDoc} */

    @Override
    public List<BillingONExt> getClaimExtItems(int billingNo) {
        Query query = entityManager.createQuery("select ext from BillingONExt ext where ext.billingNo = ?1");
        query.setParameter(1, billingNo);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<BillingONExt> getBillingExtItems(String billingNo) {
        Query query = entityManager
                .createQuery("select ext from BillingONExt ext where ext.billingNo = ?1 and ext.status='1' ");
        try {
            query.setParameter(1, Integer.parseInt(billingNo));
            return query.getResultList();
        } catch (Exception e) {
            return null;
        }
    }

    /** {@inheritDoc} */

    @Override
    public List<BillingONExt> getInactiveBillingExtItems(String billingNo) {
        Query query = entityManager
                .createQuery("select ext from BillingONExt ext where ext.billingNo = ?1 and ext.status='0' ");
        try {
            query.setParameter(1, Integer.parseInt(billingNo));
            return query.getResultList();
        } catch (Exception e) {
            return null;
        }
    }

    /** {@inheritDoc} */

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
        } catch (Exception e) {
        }
        if (ext != null) {
            val = new BigDecimal(ext.getValue()).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        return val;
    }

    /** {@inheritDoc} */

    @Override
    public BillingONExt getClaimExtItem(Integer billingNo, Integer demographicNo, String keyVal)
            throws NonUniqueResultException {
        String filter1 = (billingNo == null ? "" : "ext.billingNo = ?1");
        String filter2 = (demographicNo == null ? "" : "ext.demographicNo = ?2");
        String filter3 = (keyVal == null ? "" : "ext.keyVal = ?3");
        String sql = "select ext from BillingONExt ext";
        boolean isWhere = false;
        if (filter1 != null) {
            sql += " where ext.billingNo = ?1";
            isWhere = true;
        }
        if (filter2 != null) {
            if (isWhere)
                sql += " and demographicNo = ?2";
            else {
                sql += "where demographicNo = ?2";
                isWhere = true;
            }
        }
        if (filter3 != null) {
            if (isWhere)
                sql += " and keyVal = ?3";
            else {
                sql += "where keyVal = ?3";
                isWhere = true;
            }
        }
        Query query = entityManager.createQuery(sql);
        if (filter1 != null)
            query.setParameter(1, billingNo);
        if (filter1 != null)
            query.setParameter(2, demographicNo);
        if (filter3 != null)
            query.setParameter(3, keyVal);
        BillingONExt res = null;
        try {
            res = (BillingONExt) query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
        return res;
    }

    /** {@inheritDoc} */

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

    /** {@inheritDoc} */

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
