//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.commn.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.Query;

import ca.openosp.openo.commn.NativeSql;
import ca.openosp.openo.commn.model.Billing;
import ca.openosp.openo.utility.DateRange;
import org.springframework.stereotype.Repository;
import ca.openosp.openo.entities.Billingmaster;
import ca.openosp.openo.billings.ca.bc.MSP.MSPReconcile;
import ca.openosp.openo.util.ConversionUtils;

@Repository
@SuppressWarnings("unchecked")
public class BillingDaoImpl extends AbstractDaoImpl<Billing> implements BillingDao {

    public BillingDaoImpl() {
        super(Billing.class);
    }

    @Override
    public List<Billing> findActive(int billingNo) {
        Query q = entityManager.createQuery("select x from Billing x where x.id=?1 and x.status <> ?2");
        q.setParameter(1, billingNo);
        q.setParameter(2, "D");


        List<Billing> results = q.getResultList();

        return results;
    }

    @Override
    public List<Billing> findByBillingType(String type) {
        Query q = entityManager.createQuery("select x from Billing x where x.billingtype=?1");
        q.setParameter(1, type);


        List<Billing> results = q.getResultList();

        return results;
    }

    @Override
    public List<Billing> findByAppointmentNo(int apptNo) {
        Query q = entityManager.createQuery("select x from Billing x where x.appointmentNo=?1");
        q.setParameter(1, apptNo);


        List<Billing> results = q.getResultList();

        return results;
    }

    @Override
    public List<Billing> findSet(List<String> list) {
        if (list.size() == 0) {
            return new ArrayList<Billing>();
        }
        Query query = entityManager.createQuery("SELECT b FROM  Billing b  where b.id in (?1)");
        query.setParameter(1, ConversionUtils.toIntList(list));


        List<Billing> results = query.getResultList();

        return results;
    }

    @Override
    public List<Object[]> findBillings(Integer demoNo, List<String> serviceCodes) {
        Map<String, Object> params = new HashMap<String, Object>();
        StringBuilder serviceCodeValues = new StringBuilder();
        for (int i = 0; i < serviceCodes.size(); i++) {
            if (serviceCodeValues.length() != 0) {
                serviceCodeValues.append(" OR ");
            }

            String param = "serviceCode" + (i + 1);
            serviceCodeValues.append("bd.serviceCode = :").append(param);
            params.put(param, serviceCodes.get(i));
        }

        StringBuilder buf = new StringBuilder("FROM Billing b, BillingDetail bd where b.demographicNo = :demoNo and bd.billingNo = b.id");
        params.put("demoNo", demoNo);

        if (serviceCodeValues.length() != 0) {
            buf.append(" AND ( ").append(serviceCodeValues).append(" ) ");
        }

        buf.append(" AND bd.status != :deletedFlag and b.status != :deletedFlag order by b.billingDate desc");
        params.put("deletedFlag", "D");

        Query query = entityManager.createQuery(buf.toString());
        query.setMaxResults(1);

        for (Entry<String, Object> e : params.entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
        return query.getResultList();
    }

    @Override
    public List<Billing> findBillings(Integer demoNo, String statusType, String providerNo, Date startDate, Date endDate) {
        String providerQuery = "", startDateQuery = "", endDateQuery = "", demoQuery = "";

        Map<String, Object> params = new HashMap<String, Object>();
        if (providerNo != null && !providerNo.trim().equalsIgnoreCase("all")) {
            providerQuery = " and b.apptProviderNo = :providerNo";

            params.put("providerNo", providerNo);
        }

        if (startDate != null) {
            startDateQuery = " and ( b.billingDate >= :startDate ) ";
            params.put("startDate", startDate);
        }

        if (endDate != null) {
            endDateQuery = " and ( b.billingDate <= :endDate) ";
            params.put("endDate", endDate);
        }

        if (demoNo != null) {
            demoQuery = " and b.demographicNo = :demoNo";
            params.put("demoNo", demoNo);
        }

        String queryString = "FROM Billing b WHERE b.status like :status "
                + providerQuery + startDateQuery
                + endDateQuery
                + demoQuery;
        params.put("status", statusType);

        Query query = entityManager.createQuery(queryString);
        for (Entry<String, Object> param : params.entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }

        return query.getResultList();
    }

    @Override
    public List<Object[]> findBillings(Integer billing_no) {
        Query query = entityManager.createQuery("FROM " + Billingmaster.class.getSimpleName() + " b, Billing bi where bi.id = b.billingNo and b.billingNo = ?1");
        query.setParameter(1, billing_no);
        return query.getResultList();
    }

    /**
     * Finds all billings for providers in optional date range
     *
     * @param providerNos
     * @param dateRange
     * @return
     */
    @Override
    public List<Object[]> findProviderBillingsWithGst(String[] providerNos, DateRange dateRange) {
        Map<String, Object> params = new HashMap<String, Object>();
        StringBuilder sqlStr = new StringBuilder("FROM " + Billingmaster.class.getSimpleName() + " b, Billing bi where bi.id = b.billingNo and bi.providerNo IN (:providerNos) " + "and b.gst > 0.00");
        params.put("providerNos", Arrays.asList(providerNos));

        if (dateRange != null) {
            if (dateRange.getFrom() != null) {
                sqlStr.append(" AND bi.billingDate >= :dateFrom");
                params.put("dateFrom", dateRange.getFrom());
            }

            if (dateRange.getTo() != null) {
                sqlStr.append(" AND bi.billingDate <= :dateTo");
                params.put("dateTo", dateRange.getTo());
            }
        }

        Query query = entityManager.createQuery(sqlStr.toString());
        for (Entry<String, Object> param : params.entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }

        return query.getResultList();
    }

    /**
     * Finds billings by the providers, status and dates
     *
     * @param providerNo
     * @param statusList
     * @param dateRange
     * @return list of Billing
     */
    @Override
    public List<Billing> findByProviderStatusAndDates(String providerNo, List<String> statusList, DateRange dateRange) {
        Map<String, Object> params = new HashMap<String, Object>();
        StringBuilder buf = new StringBuilder("FROM Billing b WHERE b.providerOhipNo = :pon");
        params.put("pon", providerNo);

        if (statusList != null && !statusList.isEmpty()) {
            buf.append(" AND b.status IN (:sl)");
            params.put("sl", statusList);
        }

        if (dateRange != null) {
            if (dateRange.getFrom() != null) {
                buf.append(" AND b.billingDate >= :bf");
                params.put("bf", dateRange.getFrom());
            }

            if (dateRange.getTo() != null) {
                buf.append(" AND b.billingDate <= :bt");
                params.put("bt", dateRange.getTo());
            }
        }

        Query query = entityManager.createQuery(buf.toString());

        for (Entry<String, Object> param : params.entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }

        return query.getResultList();
    }

    @Override
    public List<Billing> getMyMagicBillings() {
        Query q = createQuery("b", "b.status <> 'B' AND b.billingtype IN ('ICBC', 'WCB', 'MSP')");
        return q.getResultList();
    }

    @Override
    @NativeSql({"billing", "provider", "billingmaster"})
    public List<Object[]> findByManyThings(String statusType, String providerNo, String startDate, String endDate, String demoNo, boolean excludeWCB, boolean excludeMSP, boolean excludePrivate, boolean exludeICBC) {
        StringBuilder queryBuilder = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        int paramCounter = 1;

        // Base query
        queryBuilder.append(" select b.billing_no, b.demographic_no, b.demographic_name, b.update_date, b.billingtype,");
        queryBuilder.append(" b.status, b.apptProvider_no, b.appointment_no, b.billing_date, b.billing_time, bm.billingstatus, ");
        queryBuilder.append(" bm.bill_amount, bm.billing_code, bm.dx_code1, bm.dx_code2, bm.dx_code3,");
        queryBuilder.append(" b.provider_no, b.visitdate, b.visittype, bm.billingmaster_no, p.first_name, p.last_name,COALESCE(bm.billing_unit,'')");
        queryBuilder.append(" from billing b left join provider p on p.provider_no = b.provider_no");
        queryBuilder.append(", billingmaster bm where b.billing_no= bm.billing_no");

        // Status type clause
        if ("?".equals(statusType)) {
            queryBuilder.append(" and bm.billingstatus in (?").append(paramCounter).append(", ?").append(paramCounter + 1).append(", ?").append(paramCounter + 2).append(")");
            params.put(String.valueOf(paramCounter++), MSPReconcile.PAIDWITHEXP);
            params.put(String.valueOf(paramCounter++), MSPReconcile.REJECTED);
            params.put(String.valueOf(paramCounter++), MSPReconcile.HELD);
        } else if ("$".equals(statusType)) {
            queryBuilder.append(" and bm.billingstatus in (?").append(paramCounter).append(", ?").append(paramCounter + 1).append(", ?").append(paramCounter + 2).append(")");
            params.put(String.valueOf(paramCounter++), MSPReconcile.PAIDWITHEXP);
            params.put(String.valueOf(paramCounter++), MSPReconcile.PAIDPRIVATE);
            params.put(String.valueOf(paramCounter++), MSPReconcile.SETTLED);
        } else if (statusType != null) {
            queryBuilder.append(" and bm.billingstatus like ?").append(paramCounter);
            params.put(String.valueOf(paramCounter++), statusType);
        }

        // Provider clause
        if (providerNo != null && !providerNo.trim().equalsIgnoreCase("all")) {
            queryBuilder.append(" and b.provider_no = ?").append(paramCounter);
            params.put(String.valueOf(paramCounter++), providerNo);
        }

        // Start date clause
        if (startDate != null && !startDate.trim().equalsIgnoreCase("")) {
            queryBuilder.append(" and ( to_days(service_date) >= to_days(?").append(paramCounter).append("))");
            params.put(String.valueOf(paramCounter++), startDate);
        }

        // End date clause
        if (endDate != null && !endDate.trim().equalsIgnoreCase("")) {
            queryBuilder.append(" and ( to_days(service_date) <= to_days(?").append(paramCounter).append("))");
            params.put(String.valueOf(paramCounter++), endDate);
        }

        // Demographic clause
        if (demoNo != null && !demoNo.trim().equalsIgnoreCase("")) {
            queryBuilder.append(" and b.demographic_no = ?").append(paramCounter);
            params.put(String.valueOf(paramCounter++), demoNo);
        }

        // Billing type exclusions
        if (excludeWCB) {
            queryBuilder.append(" and b.billingType != ?").append(paramCounter);
            params.put(String.valueOf(paramCounter++), MSPReconcile.BILLTYPE_WCB);
        }

        if (excludeMSP) {
            queryBuilder.append(" and b.billingType != ?").append(paramCounter);
            params.put(String.valueOf(paramCounter++), MSPReconcile.BILLTYPE_MSP);
        }

        if (excludePrivate) {
            queryBuilder.append(" and b.billingType != ?").append(paramCounter);
            params.put(String.valueOf(paramCounter++), MSPReconcile.BILLTYPE_PRI);
        }

        if (exludeICBC) {
            queryBuilder.append(" and b.billingType != ?").append(paramCounter);
            params.put(String.valueOf(paramCounter++), MSPReconcile.BILLTYPE_ICBC);
        }

        queryBuilder.append(" order by b.billing_date desc");

        Query query = entityManager.createNativeQuery(queryBuilder.toString());
        
        // Set all parameters
        for (Map.Entry<String, Object> param : params.entrySet()) {
            query.setParameter(Integer.parseInt(param.getKey()), param.getValue());
        }
        
        return query.getResultList();
    }

    @Override
    public List<Object[]> findBillingsByStatus(String statusType) {
        Query query = entityManager.createQuery("FROM Billing b, Billingmaster bm " +
                "WHERE b.id = bm.billingNo " +
                "AND bm.billingstatus = ?1");
        query.setParameter(1, statusType);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findOutstandingBills(Integer demographicNo, String billingType, List<String> statuses) {
        int counter = 1;
        String q = "FROM Billingmaster bm, Billing b " +
                "WHERE bm.billingNo = b.id " +
                "AND b.demographicNo = ?" + counter++ + " " + 
                (statuses.isEmpty() ? "" : ("AND bm.billingstatus NOT IN ( ?" + counter++) + " ) ") +
                "AND b.billingtype = ?" + counter++;

        counter = 1;
        Query query = entityManager.createQuery(q);
        query.setParameter(counter++, demographicNo);
        if (!statuses.isEmpty()) {
            query.setParameter(counter++, statuses);
        }
        query.setParameter(counter++, billingType);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findByBillingMasterNo(Integer billingmasterNo) {
        Query query = entityManager.createQuery("FROM Billingmaster b, Billing b1 " +
                "WHERE b1.id = b.billingNo " +
                "AND b.billingmasterNo = ?1");
        query.setParameter(1, billingmasterNo);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findBillingsByManyThings(Integer billing, Date billingDate, String ohipNo, String serviceCode) {
        Query q = entityManager.createQuery("FROM Billing b, BillingDetail bd " +
                "WHERE b.id = ?1 " +
                "AND b.billingDate = ?2 " +
                "AND b.providerOhipNo = ?3 " +
                "AND bd.billingNo = b.id " +
                "AND bd.serviceCode = ?4");
        q.setParameter(1, billing);
        q.setParameter(2, billingDate);
        q.setParameter(3, ohipNo);
        q.setParameter(4, serviceCode);
        return q.getResultList();
    }

    @Override
    public Integer countBillings(String diagCode, String creator, Date sdate, Date edate) {
        String sql = "SELECT DISTINCT b.id FROM Billing b, BillingDetail bd " +
                "WHERE b.id = bd.billingNo " +
                "AND bd.diagnosticCode = ?1 " +
                "AND b.creator = ?2 " +
                "AND b.billingDate >= ?3 " +
                "AND b.billingDate <= ?4 " +
                "AND b.status != 'D' " +
                "AND bd.status!='D'";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, diagCode);
        query.setParameter(2, creator);
        query.setParameter(3, sdate);
        query.setParameter(4, edate);


        List<Integer> ids = query.getResultList();
        return ids.size();
    }

    @Override
    public List<Object[]> countBillingVisitsByCreator(String providerNo, Date dateBegin, Date dateEnd) {
        String sql = "SELECT b.visitType, COUNT(b) FROM Billing b "
                + "WHERE b.status <> 'D' "
                + "AND b.appointmentNo <> '0' "
                + "AND b.creator = ?1 "
                + "AND b.billingDate >= ?2 "
                + "AND b.billingDate <= ?3 "
                + "GROUP BY b.visitType";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, providerNo);
        q.setParameter(2, dateBegin);
        q.setParameter(3, dateEnd);
        return q.getResultList();
    }

    @Override
    public List<Object[]> countBillingVisitsByProvider(String providerNo, Date dateBegin, Date dateEnd) {
        String sql = "SELECT b.visitType, COUNT(b) FROM Billing b "
                + "WHERE b.status <> 'D' "
                + "AND b.appointmentNo <> '0' "
                + "AND b.apptProviderNo = ?1"
                + "AND b.billingDate >= ?2"
                + "AND b.billingDate <= ?3"
                + "GROUP BY b.visitType";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, providerNo);
        q.setParameter(2, dateBegin);
        q.setParameter(3, dateEnd);
        return q.getResultList();
    }

    @Override
    public Integer search_billing_no_by_appt(int demographicNo, int appointmentNo) {
        Query q = entityManager.createQuery("select x from Billing x where x.demographicNo=?1 and x.appointmentNo = ?2 and x.status <> ?3 order by x.updateDate desc, x.updateTime desc");
        q.setParameter(1, demographicNo);
        q.setParameter(2, appointmentNo);
        q.setParameter(3, "D");


        List<Billing> results = q.getResultList();

        if (results.size() > 0)
            return results.get(0).getId();

        return null;
    }

    @Override
    public Integer search_billing_no(int demographicNo) {
        Query q = entityManager.createQuery("select x from Billing x where x.demographicNo=?1 order by x.updateDate desc, x.updateTime desc");
        q.setParameter(1, demographicNo);


        List<Billing> results = q.getResultList();

        if (results.size() > 0)
            return results.get(0).getId();

        return null;
    }

    @Override
    public List<Object[]> search_bill_generic(int billingNo) {
        Query query = entityManager.createQuery("select distinct d.LastName, d.FirstName, p.LastName, p.FirstName, b.id, b.billingDate, b.billingTime, b.status, b.appointmentNo, b.hin"
                + " from Billing b, Provider p, Appointment a, Demographic d "
                + "where p.ProviderNo=a.providerNo and d.DemographicNo= b.demographicNo and b.appointmentNo=a.id and b.status <> 'D' and b.id=?1");
        query.setParameter(1, billingNo);


        List<Object[]> results = query.getResultList();

        return results;
    }

    @Override
    public List<Billing> findByProviderStatusForTeleplanFileWriter(String hin) {
        Query query = createQuery("bs", "bs.providerOhipNo = ?1 and (bs.status = 'O' or bs.status = 'W') and bs.billingtype != 'Pri'");
        query.setParameter(1, hin);
        return query.getResultList();
    }

    @Override
    public List<Billing> search_bill_history_daterange(String providerNo, Date startBillingDate, Date endBillingDate) {
        Query q = entityManager.createQuery("select b from Billing b where b.providerNo like ?1 and b.billingDate >=?2 and b.billingDate<=?3 and b.status<>'D' and b.status<>'S' and b.status<>'B' and b.demographicNo <> 0 order by b.billingDate desc, b.billingTime desc");
        q.setParameter(1, providerNo);
        q.setParameter(2, startBillingDate);
        q.setParameter(3, endBillingDate);

        @SuppressWarnings("unchecked")
        List<Billing> results = q.getResultList();

        return results;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> search_billob(String providerNo, Date startDate, Date endDate) {
        String[] serviceCodes = {"P006A", "P011A", "P009A", "P020A", "P022A", "P028A", "P023A", "P007A", "P008B", "P018B", "E502A", "C989A", "E409A", "E410A", "E411A", "H001A"};
        Query q = entityManager.createQuery("select distinct b.id,b.total,b.status,b.billingDate, b.demographicName from Billing b, BillingDetail bd "
                + "where bd.billingNo=b.id and b.status<>'D' and bd.serviceCode in (?1) and b.providerNo like ?2" +
                "and b.billingDate>=?3 and b.billingDate<=?4");
        q.setParameter(1, Arrays.asList(serviceCodes));
        q.setParameter(2, providerNo);
        q.setParameter(3, startDate);
        q.setParameter(4, endDate);
        return q.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> search_billflu(String creator, Date startDate, Date endDate) {
        Query q = entityManager.createQuery("select distinct b.content, b.id,b.total,b.status,b.billingDate, b.demographicName From Billing b, BillingDetail bd " +
                "where bd.billingNo=b.id and b.status<>'D' and( bd.serviceCode='G590A' or bd.serviceCode='G591A') and b.creator like ?1" +
                "and b.billingDate>=?2 and b.billingDate<=?3 order by b.demographicName");
        q.setParameter(1, creator);
        q.setParameter(2, startDate);
        q.setParameter(3, endDate);

        return q.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Billing> search_unsettled_history_daterange(String providerNo, Date startDate, Date endDate) {
        Query q = entityManager.createQuery("select b from Billing b where b.providerNo=?1 and b.billingDate >=?2 and b.billingDate<=?3 and (b.status='B') and b.demographicNo <> 0 order by b.billingDate desc, b.billingTime desc");
        q.setParameter(1, providerNo);
        q.setParameter(2, startDate);
        q.setParameter(3, endDate);

        return q.getResultList();
    }

    @Override
    public List<Billing> findActiveBillingsByDemoNo(Integer demoNo, int limit) {
        String sql = "FROM Billing b " +
                "WHERE b.demographicNo = ?1" +
                "AND b.status <> 'D' " +
                "ORDER BY b.billingDate DESC, b.id DESC";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demoNo);
        if (limit > 0) {
            query.setMaxResults(limit);
        }
        return query.getResultList();
    }

    @Override
    public List<Billing> findBillingsByDemoNoServiceCodeAndDate(Integer demoNo, Date date, List<String> serviceCodes) {
        String sql = "SELECT b FROM Billing b, BillingDetail bd " +
                "WHERE bd.billingNo = b.id " +
                "AND bd.status <> 'D' " +
                "AND b.status <> 'D' " +
                "AND bd.serviceCode IN (?1) " +
                "AND b.billingDate <= ?2" +
                "AND b.demographicNo = ?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceCodes);
        query.setParameter(2, date);
        query.setParameter(3, demoNo);
        return query.getResultList();

    }

}
