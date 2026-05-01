/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.billings.dto.BillingONCListItemDTO;
import io.github.carlos_emr.carlos.PMmodule.utility.DateUtils;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingStatusQueryService;
import io.github.carlos_emr.carlos.util.ParamAppender;

/**
 * @author Eugene Katyukhin
 */

@Repository
@SuppressWarnings("unchecked")
public class BillingONCHeader1DaoImpl extends AbstractDaoImpl<BillingONCHeader1> implements BillingONCHeader1Dao {

    public BillingONCHeader1DaoImpl() {
        super(BillingONCHeader1.class);
    }

    @Override
    public List<BillingONCHeader1> getBillCheader1ByDemographicNo(int demographic_no) {
        Query query = entityManager
                .createQuery("select ch from BillingONCHeader1 ch where ch.demographicNo=?1 AND ch.status!='D'");
        query.setParameter(1, demographic_no);
        return query.getResultList();
    }

    @Override
    public int getNumberOfDemographicsWithInvoicesForProvider(String providerNo, Date startDate, Date endDate,
                                                              boolean distinct) {
        String distinctStr = "distinct";
        if (distinct == false) {
            distinctStr = StringUtils.EMPTY;
        }

        Query query = entityManager.createNativeQuery("select count(" + distinctStr
                + " demographic_no) from billing_on_cheader1 ch where ch.provider_no = ?1 and billing_date >= ?2 and billing_date <= ?3");
        query.setParameter(1, providerNo);
        query.setParameter(2, startDate);
        query.setParameter(3, endDate);
        Number bint = (Number) query.getSingleResult();
        return bint.intValue();
    }

    @Override
    public void createBills(List<BillingONCHeader1> lBills) {
        for (BillingONCHeader1 b : lBills) {
            this.persist(b);
        }
    }

    @Override
    public List<BillingONItem> findActiveItems(Integer invoiceNo) {
        if (invoiceNo == null) {
            return java.util.Collections.emptyList();
        }
        // Direct query — sidesteps the parent entity's billingItems collection
        // entirely, so this works (and is efficient) regardless of the
        // BillingONCHeader1.billingItems fetch type.
        Query q = entityManager.createQuery("SELECT i FROM BillingONItem i WHERE i.ch1Id = ?1 AND i.status != 'D'");
        q.setParameter(1, invoiceNo);
        return q.getResultList();
    }

    @Override
    public List<BillingONItem> findActiveItemsByInvoiceNos(List<Integer> invoiceNos) {
        if (invoiceNos == null || invoiceNos.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Query q = entityManager.createQuery(
                "SELECT i FROM BillingONItem i WHERE i.ch1Id IN (?1) AND i.status != 'D' ORDER BY i.ch1Id, i.id");
        q.setParameter(1, invoiceNos);
        return q.getResultList();
    }

    @Override
    public List<BillingONItem> findItemsByInvoiceNos(List<Integer> invoiceNos) {
        if (invoiceNos == null || invoiceNos.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Query q = entityManager.createQuery(
                "SELECT i FROM BillingONItem i WHERE i.ch1Id IN (?1) ORDER BY i.ch1Id, i.id");
        q.setParameter(1, invoiceNos);
        return q.getResultList();
    }

    @Override
    public BillingONCHeader1 findWithItems(Integer id) {
        if (id == null) {
            return null;
        }
        Query q = entityManager.createQuery("SELECT DISTINCT h FROM BillingONCHeader1 h LEFT JOIN FETCH h.billingItems WHERE h.id = ?1");
        q.setParameter(1, id);
        List<BillingONCHeader1> rows = q.getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<BillingONCHeader1> findByDemoNoWithItems(Integer demoNo, int iOffSet, int pageSize) {
        // DISTINCT keeps the parent count correct when LEFT JOIN FETCH produces
        // one row per child item; the items collection is still populated
        // correctly in each returned entity.
        Query query = entityManager.createQuery("SELECT DISTINCT h FROM BillingONCHeader1 h LEFT JOIN FETCH h.billingItems WHERE h.demographicNo = ?1 AND h.status != 'D' ORDER BY h.billingDate DESC, h.billingTime DESC, h.id DESC");
        query.setParameter(1, demoNo);
        query.setFirstResult(iOffSet);
        query.setMaxResults(pageSize);
        return query.getResultList();
    }

    @Override
    public boolean billedBetweenTheseDays(String serviceCode, Integer demographicNo, Date startDate, Date endDate) {
        boolean hasBeenBilled = false;
        String sql = "select b from BillingONCHeader1 h1, BillingONItem b where b.ch1Id = h1.id and b.serviceCode = ?1 and"
                +
                " h1.demographicNo = ?2 and h1.status != 'D' and h1.billingDate >= ?3 and h1.billingDate <= ?4 order by h1.billingDate desc";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, serviceCode);
        q.setParameter(2, demographicNo);
        q.setParameter(3, (new SimpleDateFormat("yyyy-MM-dd")).format(startDate));
        q.setParameter(4, (new SimpleDateFormat("yyyy-MM-dd")).format(endDate));
        q.setMaxResults(1);

        List<BillingONItem> billingClaims = q.getResultList();

        if (billingClaims.size() > 0) {
            hasBeenBilled = true;
        }

        return hasBeenBilled;
    }

    @Override
    public int getDaysSinceBilled(String serviceCode, Integer demographicNo) {
        String sql = "select b from BillingONCHeader1 h1, BillingONItem b where b.ch1Id = h1.id and b.serviceCode = ?1 and"
                +
                " h1.demographicNo = ?2 and h1.status != 'D' order by h1.billingDate desc";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, serviceCode);
        q.setParameter(2, demographicNo);
        q.setMaxResults(1);

        List<BillingONItem> billingClaims = q.getResultList();
        int numDays = -1;

        if (billingClaims.size() > 0) {
            BillingONItem i = billingClaims.get(0);
            Calendar billdate = Calendar.getInstance();
            billdate.setTime(i.getServiceDate());

            long milliBilldate = billdate.getTimeInMillis();
            long milliToday = Calendar.getInstance().getTimeInMillis();

            numDays = DateUtils.getDifDays(milliToday, milliBilldate);
        }

        return numDays;
    }

    @Override
    public int getDaysSincePaid(String serviceCode, Integer demographic_no) {
        String sql = "select b from BillingONCHeader1 h1, BillingONItem b where b.ch1Id = h1.id and b.serviceCode = ?1 and"
                +
                " h1.demographicNo = ?2 and h1.status = 'S' order by h1.billingDate desc";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, serviceCode);
        q.setParameter(2, demographic_no);
        q.setMaxResults(1);

        List<BillingONItem> billingClaims = q.getResultList();
        int numDays = -1;

        if (billingClaims.size() > 0) {
            BillingONItem i = billingClaims.get(0);
            Calendar billDate = Calendar.getInstance();
            billDate.setTime(i.getServiceDate());

            long milliBilldate = billDate.getTimeInMillis();
            long milliToday = Calendar.getInstance().getTimeInMillis();

            numDays = DateUtils.getDifDays(milliToday, milliBilldate);
        }

        return numDays;
    }

    @Override
    public List<BillingONCHeader1> getInvoices(Integer demographicNo, Integer limit) {
        String sql = "select h1 from BillingONCHeader1 h1 where " +
                " h1.demographicNo = ?1 and h1.status != 'D' order by h1.billingDate desc";
        Query q = entityManager.createQuery(sql);

        q.setParameter(1, demographicNo);
        q.setMaxResults(limit);

        return q.getResultList();
    }

    @Override
    public List<BillingONCHeader1> getInvoices(Integer demographicNo) {
        String sql = "select h1 from BillingONCHeader1 h1 where " +
                " h1.demographicNo = ?1 and h1.status != 'D' order by h1.billingDate desc";
        Query q = entityManager.createQuery(sql);

        q.setParameter(1, demographicNo);

        return q.getResultList();
    }

    @Override
    public List<BillingONCHeader1> getInvoicesByIds(List<Integer> ids) {
        if (ids.isEmpty())
            return new ArrayList<BillingONCHeader1>();

        String sql = "select h1 from BillingONCHeader1 h1 where h1.id in (?1)";
        Query q = entityManager.createQuery(sql);

        q.setParameter(1, ids);

        return q.getResultList();
    }

    @Override
    public List<Map<String, Object>> getInvoicesMeta(Integer demographicNo) {
        String sql = "select new map(h1.id as id, h1.billingDate as billingDate, h1.billingTime as billing_time, h1.providerNo as provider_no) from BillingONCHeader1 h1 where "
                +
                " h1.demographicNo = ?1 and h1.status != 'D' order by h1.billingDate desc";
        Query q = entityManager.createQuery(sql);

        q.setParameter(1, demographicNo);

        return q.getResultList();
    }

    @Override
    public BillingONItem findBillingONItemByServiceCode(BillingONCHeader1 ch1, String serviceCode) {
        String sql = "select b1 from BillingONItem b1 where b1.ch1Id = ?1 and b1.serviceCode = ?2";

        Query q = entityManager.createQuery(sql);
        q.setParameter(1, ch1.getId());
        q.setParameter(2, serviceCode);

        BillingONItem b = null;

        List<BillingONItem> results = q.getResultList();
        if (!results.isEmpty()) {
            if (results.size() > 1) {
                MiscUtils.getLogger().warn(
                        "Duplicate service codes on same invoice. Id:" + ch1.getId() + " Service Code:" + serviceCode);
            }
            b = results.get(0);
        }
        return b;
    }

    @Override
    public List<BillingONCHeader1> get3rdPartyInvoiceByProvider(Provider p, Date start, Date end, Locale locale) {
        String sql = "select distinct bCh1 from BillingONPayment bPay, BillingONCHeader1 bCh1 where bPay.billingNo=bCh1.id and bCh1.providerNo=?1 and bPay.paymentdate >= ?2 and bPay.paymentdate <= ?3 order by bCh1.id";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, p.getProviderNo());
        query.setParameter(2, start);
        query.setParameter(3, end);

        List<BillingONCHeader1> results = query.getResultList();

        return results;
    }

    @Override
    public List<BillingONCHeader1> get3rdPartyInvoiceByDate(Date start, Date end, Locale locale) {
        String sql = "select distinct bCh1 from BillingONPayment bPay, BillingONCHeader1 bCh1 where bPay.billingNo=bCh1.id and bPay.paymentdate >= ?1 and bPay.paymentdate <= ?2 order by bCh1.id";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, start);
        query.setParameter(2, end);

        List<BillingONCHeader1> results = query.getResultList();

        return results;
    }

    @Override
    public BillingONCHeader1 getLastOHIPBillingDateForServiceCode(Integer demographicNo, String serviceCode) {
        String sql = "select b from BillingONItem i, BillingONCHeader1 b where i.ch1Id=b.id and i.status!='D' and i.serviceCode=?1 and b.demographicNo=?2  and (b.payProgram='HCP' or b.payProgram='RMB' or b.payProgram='WCB') and (b.status='S' or b.status='O' or b.status='B') order by b.billingDate desc";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceCode);
        query.setParameter(2, demographicNo);

        List<BillingONCHeader1> results = query.getResultList();
        BillingONCHeader1 result = null;
        if (results.size() > 0) {
            result = results.get(0);
        }
        return result;
    }

    @Override
    public List<BillingONCHeader1> findByAppointmentNo(Integer appointmentNo) {
        String sql = "select h1 from BillingONCHeader1 h1 where h1.appointmentNo=?1";
        Query q = entityManager.createQuery(sql);

        q.setParameter(1, appointmentNo);

        return q.getResultList();
    }

    @Override
    /**
     * Counts billing visits by provider within a specified date range.
     */
    public List<Object[]> countBillingVisitsByProvider(String providerNo, Date dateBegin, Date dateEnd) {
        String sql = "SELECT b.visitType, count(b) FROM BillingONCHeader1 b "
                + "WHERE b.status <> 'D' "
                + "AND b.appointmentNo <> 0 "
                + "AND b.apptProviderNo = ?1 "
                + "AND b.billingDate >= ?2 "
                + "AND b.billingDate <= ?3 "
                + "GROUP BY b.visitType";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, providerNo);
        q.setParameter(2, (new SimpleDateFormat("yyyy-MM-dd")).format(dateBegin));
        q.setParameter(3, (new SimpleDateFormat("yyyy-MM-dd")).format(dateEnd));
        return q.getResultList();
    }

    @Override
    public List<Object[]> countBillingVisitsByCreator(String providerNo, Date dateBegin, Date dateEnd) {
        String sql = "SELECT b.visitType, count(b) FROM BillingONCHeader1 b "
                + "WHERE b.status <> 'D' "
                + "AND b.appointmentNo <> 0 "
                + "AND b.creator = ?1 "
                + "AND b.billingDate >= ?2 "
                + "AND b.billingDate <= ?3 "
                + "GROUP BY b.visitType";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, providerNo);
        q.setParameter(2, (new SimpleDateFormat("yyyy-MM-dd")).format(dateBegin));
        q.setParameter(3, (new SimpleDateFormat("yyyy-MM-dd")).format(dateEnd));
        return q.getResultList();
    }

    @Override
    public List<Long> count_larrykain_clinic(String facilityNum, Date startDate, Date endDate) {
        Query q = entityManager.createQuery(
                "select count(b) from BillingONCHeader1 b where b.visitType = '00' and b.faciltyNum = ?1 and b.status <> 'D' and b.billingDate >=?2 and b.billingDate <=?3");

        q.setParameter(1, facilityNum);
        q.setParameter(2, (new SimpleDateFormat("yyyy-MM-dd")).format(startDate));
        q.setParameter(3, (new SimpleDateFormat("yyyy-MM-dd")).format(endDate));

        return q.getResultList();
    }

    @Override
    public List<Long> count_larrykain_hospital(String facilityNum1, String facilityNum2, String facilityNum3,
                                               String facilityNum4, Date startDate, Date endDate) {
        Query q = entityManager.createQuery(
                "select count(b) from BillingONCHeader1 b where b.visitType<>'00' and (b.faciltyNum=?1 or b.faciltyNum=?2 or b.faciltyNum=?3 or b.faciltyNum=?4) and status<>'D' and b.billingDate >=?5 and b.billingDate <=?6");

        q.setParameter(1, facilityNum1);
        q.setParameter(2, facilityNum2);
        q.setParameter(3, facilityNum3);
        q.setParameter(4, facilityNum4);
        q.setParameter(5, (new SimpleDateFormat("yyyy-MM-dd")).format(startDate));
        q.setParameter(6, (new SimpleDateFormat("yyyy-MM-dd")).format(endDate));

        return q.getResultList();
    }

    @Override
    public List<Long> count_larrykain_other(String facilityNum1, String facilityNum2, String facilityNum3,
                                            String facilityNum4, String facilityNum5, Date startDate, Date endDate) {
        Query q = entityManager.createQuery(
                "select count(b) from BillingONCHeader1 b where b.visitType<>'00' and status<>'D' and  (b.faciltyNum<>?1 and b.faciltyNum<>?2 and b.faciltyNum<>?3 and b.faciltyNum<>?4 and b.faciltyNum<>?5) and b.billingDate >=?6 and b.billingDate<=?7");

        q.setParameter(1, facilityNum1);
        q.setParameter(2, facilityNum2);
        q.setParameter(3, facilityNum3);
        q.setParameter(4, facilityNum4);
        q.setParameter(5, facilityNum5);
        q.setParameter(6, (new SimpleDateFormat("yyyy-MM-dd")).format(startDate));
        q.setParameter(7, (new SimpleDateFormat("yyyy-MM-dd")).format(endDate));

        return q.getResultList();
    }

    @Override
    public List<BillingONCHeader1> findBillingsByManyThings(String status, String providerNo, Date startDate,
                                                            Date endDate, Integer demoNo) {
        Map<String, Object> params = new HashMap<String, Object>();
        StringBuilder buf = getBaseQueryBuf(null, "b").append("WHERE b.status = :status ");
        params.put("status", status);

        if (providerNo != null) {
            buf.append("AND b.providerNo = :providerNo ");
            params.put("providerNo", providerNo);
        }

        if (startDate != null) {
            buf.append("AND b.billingDate >= :startDate ");
            params.put("startDate", (new SimpleDateFormat("yyyy-MM-dd")).format(startDate));
        }

        if (endDate != null) {
            buf.append("AND b.billingDate <= :endDate ");
            params.put("endDate", (new SimpleDateFormat("yyyy-MM-dd")).format(endDate));
        }

        if (demoNo != null) {
            buf.append("AND b.demographicNo = :demoNo ");
            params.put("demoNo", demoNo);
        }

        Query query = entityManager.createQuery(buf.toString());
        for (Entry<String, Object> e : params.entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
        return query.getResultList();
    }

    @Override
    public List<BillingONCHeader1> findByProviderStatusAndDateRange(String providerNo, List<String> statuses, DateRange dateRange) {
        int counter = 1;
        // Build query
        StringBuilder sqlCommand = new StringBuilder("select h from ").append(BillingONCHeader1.class.getSimpleName()).append(" h WHERE ");
        sqlCommand.append("h.providerNo = ?").append(counter++).append(" AND h.status IN (?").append(counter++).append(") ");
        // Set date range lower/upper bounds (if date range is provided)
        if (dateRange.getFrom() != null) {
            sqlCommand.append(" AND h.billingDate > ?").append(counter++);
        }
        if (dateRange.getTo() != null) {
            sqlCommand.append(" AND h.billingDate <= ?").append(counter++);
        }
        sqlCommand.append(" AND h.payProgram IN (?").append(counter++).append(") ORDER BY h.billingDate, h.billingTime");
        Query query = entityManager.createQuery(sqlCommand.toString());

        // Set date range parameters
        counter = 1;
        // Set providerNo, statuses, and payPrograms parameters
        query.setParameter(counter++, providerNo);
        query.setParameter(counter++, statuses);
        if (dateRange.getFrom() != null) {
            query.setParameter(counter++, new SimpleDateFormat("yyyy-MM-dd").format(dateRange.getFrom()));
        }
        if (dateRange.getTo() != null) {
            query.setParameter(counter++, new SimpleDateFormat("yyyy-MM-dd").format(dateRange.getTo()));
        }
        query.setParameter(counter++, Arrays.asList(new String[]{"HCP", "WCB", "RMB"}));

        return query.getResultList();
    }

    @Override
    public List<Object[]> findBillingsAndDemographicsById(Integer id) {
        String sql = "SELECT b, d FROM BillingONCHeader1 b, Demographic d WHERE b.id = ?1 AND b.demographicNo = d.DemographicNo";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        return query.getResultList();
    }

    @Override
    public List<BillingONCHeader1> findByMagic(List<String> payPrograms, String statusType, String providerNo,
                                               Date startDate, Date endDate, Integer demoNo, String visitLocation, Date paymentStartDate,
                                               Date paymentEndDate) {
        ParamAppender app = new ParamAppender("SELECT h FROM BillingONCHeader1 h, BillingONPayment bp ");
        app.and("h.id = bp.billingNo");
        app.and("h.payProgram in (:payPrograms)", "payPrograms", payPrograms);
        app.and("h.status = :status", "status", statusType);
        app.and("h.providerNo = :providerNo", "providerNo", providerNo);
        app.and("h.billingDate >= :startDate", "startDate", (new SimpleDateFormat("yyyy-MM-dd")).format(startDate));
        app.and("h.billingDate <= :endDate", "endDate", (new SimpleDateFormat("yyyy-MM-dd")).format(endDate));
        if (visitLocation != null) {
            app.and("h.facilityNum = :facilityNum", "facilityNum", visitLocation);
        }
        if (demoNo != null) {
            app.and("h.demographicNo = :demographicNo", "demographicNo", demoNo);
        }
        if (paymentStartDate != null) {
            app.and("bp.paymentdate >= :paymentStartDate", "paymentStartDate", paymentStartDate);
        }
        if (paymentEndDate != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(paymentEndDate);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            app.and("bp.paymentdate < :paymentEndDate", "paymentEndDate", cal.getTime());
        }

        app.addOrder("h.billingDate, h.billingTime");

        Query query = entityManager.createQuery(app.toString());
        app.setParams(query);
        return query.getResultList();
    }

    @Override
    public List<BillingONCHeader1> getBillingItemByDxCode(Integer demographicNo, String dxCode) {
        String queryStr = "select h FROM BillingONItem b, BillingONCHeader1 h WHERE h.id = b.ch1Id and h.demographicNo=?1 and (b.dx =?2 or b.dx1 = ?2 or b.dx2=?2)";
        Query query = entityManager.createQuery(queryStr);
        query.setParameter(1, demographicNo);
        query.setParameter(2, dxCode);

        @SuppressWarnings("unchecked")
        List<BillingONCHeader1> rs = query.getResultList();

        return rs;
    }

    @Override
    public List<Object[]> findByMagic2(List<String> payPrograms, String statusType, String providerNo, Date startDate,
                                       Date endDate, Integer demoNo, List<String> serviceCodes, String dx, String visitType, String visitLocation,
                                       Date paymentStartDate, Date paymentEndDate) {
        return findByMagic2(payPrograms, statusType, providerNo, startDate, endDate, demoNo, serviceCodes, dx,
                visitType, visitLocation, paymentStartDate, paymentEndDate, null);
    }

    @Override
    public List<Object[]> findByMagic2(List<String> payPrograms, String statusType, String providerNo, Date startDate,
                                       Date endDate, Integer demoNo, List<String> serviceCodes, String dx, String visitType, String visitLocation,
                                       Date paymentStartDate, Date paymentEndDate, String claimNo) {
        String base = "SELECT ch1, bi FROM BillingONCHeader1 ch1, BillingONItem bi";
        if (paymentStartDate != null || paymentEndDate != null) {
            base += ", BillingONPayment bp ";
        }
        if (claimNo != null) {
            base += ", RaDetail rd ";
        }
        ParamAppender app = new ParamAppender(base);
        app.and("ch1.id = bi.ch1Id");
        if (paymentStartDate != null || paymentEndDate != null) {
            app.and("ch1.id = bp.billingNo");
        }
        if (claimNo != null) {
            app.and("ch1.id = rd.billingNo");
            app.and("bi.serviceCode = rd.serviceCode");
            if ("%".equals(claimNo)) {
                // in this scenario, there is no need to filter on claimNo because % means match
                // all claim numbers
            } else {
                app.and("rd.claimNo = :claimNo", "claimNo", claimNo);
            }
        }

        app.and("bi.status != 'D'");

        app.and("ch1.payProgram in (:payPrograms)", "payPrograms", payPrograms);
        app.and("ch1.status = :status", "status", statusType);
        app.and("ch1.providerNo = :providerNo", "providerNo", providerNo);
        if (startDate != null) {
            app.and("ch1.billingDate >= :startDate", "startDate",
                    (new SimpleDateFormat("yyyy-MM-dd")).format(startDate));
        }
        if (endDate != null) {
            app.and("ch1.billingDate <= :endDate", "endDate", (new SimpleDateFormat("yyyy-MM-dd")).format(endDate));
        }

        if (paymentStartDate != null) {
            app.and("bp.paymentdate >= :paymentStartDate", "paymentStartDate", paymentStartDate);
        }
        if (paymentEndDate != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(paymentEndDate);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            app.and("bp.paymentdate < :paymentEndDate", "paymentEndDate", cal.getTime());
        }

        if (visitLocation != null && !BillingStatusQueryService.ANY_VISIT_LOCATION.equals(visitLocation)) {
            app.and("ch1.faciltyNum = :facilityNum", "facilityNum", visitLocation);
        }
        if (demoNo != null && demoNo > 0) {
            app.and("ch1.demographicNo = :demographicNo", "demographicNo", demoNo);
        }

        app.and("bi.dx = :dx", "dx", dx);
        app.and("ch1.visitType = :visitType", "visitType", visitType);

        if (serviceCodes != null && !serviceCodes.isEmpty()) {
            app.and("bi.serviceCode in (:serviceCodes)", "serviceCodes", serviceCodes);
        }

        app.addOrder("ch1.billingDate, ch1.billingTime");

        Query query = entityManager.createQuery(app.toString());
        query = app.setParams(query);
        return query.getResultList();
    }

    @Override
    public List<BillingONCHeader1> findByDemoNo(Integer demoNo, int iOffSet, int pageSize) {
        String sql = "FROM BillingONCHeader1 b WHERE b.demographicNo = ?1 " +
                "AND b.status != 'D' " +
                "ORDER BY b.billingDate DESC, b.billingTime DESC, b.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demoNo);
        query.setFirstResult(iOffSet);
        query.setMaxResults(pageSize);
        return query.getResultList();
    }

    @Override
    public List<BillingONCHeader1> findByDemoNoAndDates(Integer demoNo, DateRange dateRange, int iOffSet,
                                                        int pageSize) {
        String sql = "FROM BillingONCHeader1 b WHERE b.demographicNo = ?1 " +
                "AND b.billingDate >= ?2 " +
                "AND b.billingDate <= ?3 " +
                "AND b.status != 'D' " +
                "ORDER BY b.billingDate DESC, b.billingTime DESC, b.id DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demoNo);
        query.setParameter(2, (new SimpleDateFormat("yyyy-MM-dd")).format(dateRange.getFrom()));
        query.setParameter(3, (new SimpleDateFormat("yyyy-MM-dd")).format(dateRange.getTo()));
        query.setFirstResult(iOffSet);
        query.setMaxResults(pageSize);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findBillingsAndDemographicsByDemoIdAndDates(Integer demoNo, String payProgram, Date fromDate,
                                                                      Date toDate) {
        ParamAppender app = new ParamAppender("SELECT bch, d FROM BillingONCHeader1 bch, Demographic d");
        app.and("bch.demographicNo = d.DemographicNo");
        app.and("bch.demographicNo = :demoNo", "demoNo", demoNo);
        app.and("bch.payProgram = :payProgram", "payProgram", payProgram);
        app.and("bch.billingDate >= :fromDate", "fromDate", (new SimpleDateFormat("yyyy-MM-dd")).format(fromDate));
        app.and("bch.billingDate <= :toDate", "toDate", (new SimpleDateFormat("yyyy-MM-dd")).format(toDate));
        app.addOrder("bch.id");

        Query query = entityManager.createQuery(app.toString());
        app.setParams(query);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findDemographicsAndBillingsByDxAndServiceDates(List<String> dxCodes, Date from, Date to) {
        String sql = "SELECT d, bc, bi FROM Demographic d, BillingONCHeader1 bc, BillingONItem bi WHERE bc.demographicNo = d.DemographicNo AND bc.id = bi.ch1Id AND bi.dx in (?1) AND bi.serviceDate >= ?2 and bi.serviceDate <= ?3 GROUP BY d.demographicNo, bi.dx ORDER BY d.demographicNo, bi.serviceDate";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, dxCodes);
        query.setParameter(2, from);
        query.setParameter(3, to);
        return query.getResultList();
    }

    @Override
    public List<BillingONCHeader1> findBillingsByDemoNoCh1HeaderServiceCodeAndDate(Integer demoNo,
                                                                                   List<String> serviceCodes, Date from, Date to) {
        String sql = "SELECT b FROM BillingONCHeader1 b, BillingONItem bd " +
                "WHERE b.demographicNo = ?1 " +
                "AND bd.ch1Id=b.id " +
                "AND bd.serviceCode IN (?2) " +
                "AND b.billingDate >= ?3 " +
                "AND b.billingDate <= ?4 " +
                "AND bd.status <> 'D' " +
                "AND b.status <> 'D' " +
                "ORDER BY b.billingDate DESC";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demoNo);
        query.setParameter(2, serviceCodes);
        query.setParameter(3, (new SimpleDateFormat("yyyy-MM-dd")).format(from));
        query.setParameter(4, (new SimpleDateFormat("yyyy-MM-dd")).format(to));

        return query.getResultList();
    }

    @Override
    public List<String[]> findBillingData(String conditions) {
        if (conditions == null)
            return null;

        String sql = "SELECT ch1.id,ch1.pay_program,ch1.demographic_no,ch1.demographic_name,ch1.billing_date,ch1.billing_time,"
                + "ch1.status,ch1.provider_no,ch1.provider_ohip_no,ch1.apptProvider_no,ch1.timestamp1,ch1.total,ch1.paid,ch1.clinic,"
                + "bi.fee, bi.service_code, bi.ser_num, bi.dx, bi.id as billing_on_item_id "
                + "FROM billing_on_item bi LEFT JOIN billing_on_cheader1 ch1 ON ch1.id=bi.ch1_id "
                + "WHERE "
                + conditions
                + " ORDER BY ch1.billing_date, ch1.billing_time";
        Query query = entityManager.createQuery(sql);

        List<String[]> results = query.getResultList();

        return results;
    }

    @Override
    public List<BillingONCHeader1> findAllByPayProgram(String payProgram, int startIndex, int limit) {
        String sql = "select b FROM BillingONCHeader1 b where b.payProgram=?1 order by b.id ASC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, payProgram);
        query.setFirstResult(startIndex);
        query.setMaxResults(limit);

        List<BillingONCHeader1> results = query.getResultList();

        return results;
    }

    /**
     * Returns lightweight Ontario billing header DTOs for a demographic, excluding
     * soft-deleted records ({@code status = 'D'}), ordered by billing date descending.
     *
     * @param demographicNo Integer the patient demographic number
     * @return List&lt;BillingONCListItemDTO&gt; of active billing records ordered by billing date descending
     * @since 2026-04-11
     */
    @Override
    public List<BillingONCListItemDTO> findBillingDTOsByDemographicNo(Integer demographicNo) {
        Query query = entityManager.createQuery("""
                SELECT NEW io.github.carlos_emr.carlos.billings.dto.BillingONCListItemDTO(
                    b.id, b.demographicNo, b.providerNo, b.appointmentNo,
                    b.billingDate, b.billingTime, b.status, b.payProgram, b.visitType,
                    b.admissionDate, b.faciltyNum, b.total, b.paid, b.timestamp,
                    b.clinic, b.demographicName)
                FROM BillingONCHeader1 b
                WHERE b.demographicNo = :demoNo AND b.status <> 'D'
                ORDER BY b.billingDate DESC
                """);
        query.setParameter("demoNo", demographicNo);
        return query.getResultList();
    }

}
