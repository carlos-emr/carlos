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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.NumberFormat;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.util.DateUtils;

@Repository
public class BillingONPaymentDaoImpl extends AbstractDaoImpl<BillingONPayment> implements BillingONPaymentDao {
    @Autowired
    private BillingONExtDao billingONExtDao;

    @Autowired
    private BillingONCHeader1Dao billingONCHeader1Dao;

    public BillingONPaymentDaoImpl() {
        super(BillingONPayment.class);
    }

    public void setBillingONExtDao(BillingONExtDao billingONExtDao) {
        this.billingONExtDao = billingONExtDao;
    }

    public void setBillingONCHeader1Dao(BillingONCHeader1Dao billingONCHeader1Dao) {
        this.billingONCHeader1Dao = billingONCHeader1Dao;
    }

    public BillingONExtDao getBillingONExtDao() {
        return this.billingONExtDao;
    }

    public BillingONCHeader1Dao getBillingONCHeader1Dao() {
        return this.billingONCHeader1Dao;
    }

    public List<BillingONPayment> listPaymentsByBillingNo(Integer billingNo) {
        Query query = entityManager.createQuery("select bp from BillingONPayment bp where bp.billingNo = ?1");
        query.setParameter(1, billingNo);
        List<BillingONPayment> payments = query.getResultList();
        return payments;
    }

    public List<BillingONPayment> listPaymentsByBillingNoDesc(Integer billingNo) {
        Query query = entityManager.createQuery("select bp from BillingONPayment bp where bp.billingNo = ?1 order by bp.id desc");
        query.setParameter(1, billingNo);
        List<BillingONPayment> payments = query.getResultList();
        return payments;
    }

    public BigDecimal getPaymentsSumByBillingNo(Integer billingNo) {
        Query query = entityManager.createQuery("select sum(bp.total_payment) from BillingONPayment bp where bp.billingNo = ?1 and total_payment>0 group by bp.billingONCheader1");
        query.setParameter(1, billingNo);
        BigDecimal paymentsSum = null;
        try {
            paymentsSum = (BigDecimal) query.getSingleResult();
        } catch (NoResultException ex) {
            paymentsSum = new BigDecimal(0);
        }
        return paymentsSum;
    }

    public BigDecimal getPaymentsRefundByBillingNo(Integer billingNo) {
        Query query = entityManager.createQuery("select sum(bp.total_refund) from BillingONPayment bp where bp.billingNo = ?1 and total_refund>0 group by bp.billingONCheader1");
        query.setParameter(1, billingNo);
        BigDecimal paymentsSum = null;
        try {
            paymentsSum = (BigDecimal) query.getSingleResult();
        } catch (NoResultException ex) {
            paymentsSum = new BigDecimal(0);
        }
        return paymentsSum;
    }

    public BigDecimal getPaymentsDiscountByBillingNo(Integer billingNo) {
        Query query = entityManager.createQuery("select sum(bp.total_discount) from BillingONPayment bp where bp.billingNo = ?1 and total_discount>0 group by bp.billingONCheader1");
        query.setParameter(1, billingNo);
        BigDecimal paymentsSum = null;
        try {
            paymentsSum = (BigDecimal) query.getSingleResult();
        } catch (NoResultException ex) {
            paymentsSum = new BigDecimal(0);
        }
        return paymentsSum;
    }

    public String getTotalSumByBillingNoWeb(String billingNo) {
        Query query = entityManager.createQuery("select sum(bp.total_payment) from BillingONPayment bp where bp.billingNo = ?1 group by bp.billingONCheader1");
        BigDecimal paymentsSum = null;
        try {
            query.setParameter(1, Integer.parseInt(billingNo));
            paymentsSum = (BigDecimal) query.getSingleResult();
        } catch (Exception ex) {
            paymentsSum = new BigDecimal(0);
        }
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        return currency.format(paymentsSum);
    }

    public String getPaymentsRefundByBillingNoWeb(String billingNo) {
        Query query = entityManager.createQuery("select -sum(bp.total_payment) from BillingONPayment bp where bp.billingNo = ?1 and total_payment<0 group by bp.billingONCheader1");
        BigDecimal paymentsSum = null;
        try {
            query.setParameter(1, Integer.parseInt(billingNo));
            paymentsSum = (BigDecimal) query.getSingleResult();
        } catch (Exception ex) {
            paymentsSum = new BigDecimal(0);
        }
        NumberFormat currency = NumberFormat.getCurrencyInstance();
        return currency.format(paymentsSum);
    }

    public int getPaymentIdByBillingNo(int billingNo) {
        Query query = entityManager.createQuery("select bp.id from BillingONPayment bp where bp.billingNo = ?1");
        try {
            query.setParameter(1, Integer.valueOf(billingNo));
            return (Integer) query.getSingleResult();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getCountOfPaymentByPaymentTypeId(int paymentTypeId) {
        Query query = entityManager.createQuery("select count(bp.id) from BillingONPayment bp where bp.paymentTypeId = ?1");
        query.setParameter(1, paymentTypeId);
        Number countResult = (Number) query.getSingleResult();
        return countResult.intValue();
    }

    public String getPaymentTypeById(int paymentTypeId) {
        Query query = entityManager.createQuery("select bp.paymentType from BillingPaymentType bp where bp.id = ?1");
        query.setParameter(1, paymentTypeId);
        List<String> types = query.getResultList();
        if (types != null && types.size() > 0)
            return types.get(0);
        else
            return null;
    }


    public List<BillingONPayment> find3rdPartyPayRecordsByBill(BillingONCHeader1 bCh1) {
        String sql = "select bPay from BillingONPayment bPay where bPay.billingNo= ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());

        @SuppressWarnings("unchecked")
        List<BillingONPayment> results = query.getResultList();

        Collections.sort(results, BillingONPayment.BILLING_ON_PAYMENT_COMPARATOR);
        return results;
    }

    public List<Integer> find3rdPartyPayments(Integer billingNo) {
        String sql = "select bPay.id from BillingONPayment bPay where bPay.billingNo = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, billingNo);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return results;
    }

    public List<BillingONPayment> find3rdPartyPaymentsByBillingNo(Integer billingNo) {
        String sql = "select bPay from BillingONPayment bPay where bPay.billingNo = ?1 order by bPay.id asc";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, billingNo);

        @SuppressWarnings("unchecked")
        List<BillingONPayment> results = query.getResultList();

        List<BillingONPayment> returnList = new ArrayList<BillingONPayment>();

        for (BillingONPayment payment : results) {
            if (payment.getBillingNo() != null) {
                BillingONCHeader1 cheader1 = billingONCHeader1Dao.find(payment.getBillingNo());
                payment.setBillingOnCheader1(cheader1);
            }
            returnList.add(payment);
        }

        return returnList;
    }

    public List<BillingONPayment> find3rdPartyPayRecordsByBill(BillingONCHeader1 bCh1, Date startDate, Date endDate) {
        String sql = "select bPay from BillingONPayment bPay where bPay.billingNo = ?1 and bPay.paymentdate >= ?2 and bPay.paymentdate < ?3 order by bPay.paymentdate";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bCh1.getId());
        query.setParameter(2, startDate);
        query.setParameter(3, endDate);

        @SuppressWarnings("unchecked")
        List<BillingONPayment> results = query.getResultList();

        Collections.sort(results, BillingONPayment.BILLING_ON_PAYMENT_COMPARATOR);
        return results;
    }

    public void createPayment(BillingONCHeader1 bCh1, Locale locale, String payType, BigDecimal paidAmt, String payMethod, String providerNo) {
        //add new payment
        BillingONPayment newPayment = new BillingONPayment();
        Date now = new Date();

        newPayment.setBillingNo(bCh1.getId());
        newPayment.setBillingOnCheader1(bCh1);
        newPayment.setPaymentDate(now);
        addPaymentItems(newPayment, bCh1, locale, now, payType, paidAmt, payMethod, providerNo);
        this.persist(newPayment);

        //update billing claim header's total paid amount to reflect new payment
        BigDecimal amtPaid = bCh1.getPaid();

        if (payType.equals("P"))
            amtPaid = amtPaid.add(paidAmt);
        else
            amtPaid = amtPaid.subtract(paidAmt);

        bCh1.setPaid(amtPaid);

        BillingONCHeader1Dao bCh1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
        bCh1Dao.merge(bCh1);
    }

    private void addPaymentItems(BillingONPayment billingPayment, BillingONCHeader1 bCh1, Locale locale, Date payDate, String payType, BigDecimal paidAmt, String payMethod, String providerNo) {
        //add creator's providerNo

        BillingONExt bExt = new BillingONExt();

        bExt.setBillingNo(bCh1.getId());
        bExt.setDateTime(payDate);
        bExt.setDemographicNo(bCh1.getDemographicNo());
        bExt.setStatus('1');
        bExt.setKeyVal("provider_no");
        bExt.setValue(providerNo);

        billingPayment.getBillingONExtItems().add(bExt);

        //add pay date   
        bExt = new BillingONExt();
        bExt.setBillingNo(bCh1.getId());
        bExt.setDateTime(payDate);
        bExt.setDemographicNo(bCh1.getDemographicNo());
        bExt.setStatus('1');
        bExt.setKeyVal("payDate");
        bExt.setValue(DateUtils.formatDateTime(payDate, locale));

        billingPayment.getBillingONExtItems().add(bExt);

        //add payment amount     
        bExt = new BillingONExt();
        bExt.setBillingNo(bCh1.getId());
        bExt.setDateTime(payDate);
        bExt.setDemographicNo(bCh1.getDemographicNo());
        bExt.setStatus('1');

        if (payType.equals("P")) {
            bExt.setKeyVal("payment");
            bExt.setValue(paidAmt.toPlainString());
        } else {
            bExt.setKeyVal("refund");
            bExt.setValue(paidAmt.toPlainString());
        }
        billingPayment.getBillingONExtItems().add(bExt);

        //add pay method     
        bExt = new BillingONExt();
        bExt.setBillingNo(bCh1.getId());
        bExt.setDateTime(payDate);
        bExt.setDemographicNo(bCh1.getDemographicNo());
        bExt.setStatus('1');
        bExt.setKeyVal("payMethod");
        bExt.setValue(payMethod);

        billingPayment.getBillingONExtItems().add(bExt);
    }
}
