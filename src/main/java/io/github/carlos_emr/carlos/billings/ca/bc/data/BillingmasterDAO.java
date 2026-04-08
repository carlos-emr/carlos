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

package io.github.carlos_emr.carlos.billings.ca.bc.data;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.entities.WCB;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * @author jay
 */
@Repository
@SuppressWarnings("unchecked")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class BillingmasterDAO {
    private static Logger log = MiscUtils.getLogger();

    @PersistenceContext(unitName = "entityManagerFactory")
    protected EntityManager entityManager = null;

    /**
     * Creates a new instance of BillingmasterDAO
     */
    public BillingmasterDAO() {
    }

    public List<Billingmaster> getBillingMasterWithStatus(String billingmasterNo, String status) {
        log.debug("WHAT IS NULL ? " + billingmasterNo + "  status " + status + "   " + entityManager);
        Query query = entityManager.createQuery("select b from Billingmaster b where b.billingNo = (:billingNo) and b.billingstatus = (:status)");
        query.setParameter("billingNo", Integer.parseInt(billingmasterNo));
        query.setParameter("status", status);
        List<Billingmaster> list = query.getResultList();
        return list;
    }

    public List<Billingmaster> getBillingMasterByBillingNo(String billingNo) {
        return getBillingmasterByBillingNo(Integer.parseInt(billingNo));
    }

    public List<Billingmaster> getBillingmasterByBillingNo(int billingNo) {
        Query query = entityManager.createQuery("select b from Billingmaster b where b.billingNo = (:billingNo)");
        query.setParameter("billingNo", billingNo);
        List<Billingmaster> list = query.getResultList();
        return list;
    }

    public List<Billing> getPrivateBillings(String demographicNo) {
        Query query = entityManager.createQuery("select b from Billing b where b.billingtype = 'Pri' and b.demographicNo = (:demographicNo)");
        query.setParameter("demographicNo", Integer.parseInt(demographicNo));
        return query.getResultList();
    }

    /**
     * Same as {@link #getBillingmaster(int)}
     */
    public Billingmaster getBillingmaster(String billingmasterNo) {
        return getBillingmaster(Integer.parseInt(billingmasterNo));
    }

    public Billingmaster getBillingmaster(int billingmasterNo) {
        Query query = entityManager.createQuery("select b from Billingmaster b where b.billingmasterNo = (:billingmasterNo)");
        query.setParameter("billingmasterNo", billingmasterNo);
        List<Billingmaster> list = query.getResultList();
        if (list.size() > 0) {
            return list.get(0);
        }
        MiscUtils.getLogger().info("Unable to retrieve a billingmaster record with billingmasterNo = " + billingmasterNo);
        return null;
    }

    public void save(Billingmaster bm) {
        entityManager.persist(bm);
    }

    public void save(WCB wcb) {
        if (wcb.getW_doi() == null) {
            wcb.setW_doi(new Date()); //Fixes SF ID : 2962864
        }
        entityManager.persist(wcb);
    }

    public void save(Billing billing) {
        entityManager.persist(billing);
    }

    public void update(Billingmaster bm) {
        entityManager.merge(bm);
    }

    public void update(Billing billing) {
        entityManager.merge(billing);
    }

    public Billing getBilling(int billingNo) {
        return entityManager.find(Billing.class, billingNo);
    }

    public List<WCB> getWCBForms(int demographic) {
        Query query = entityManager.createQuery("select wcb from WCB wcb where wcb.demographic_no = (:demographicNo) order by wcb.id desc");
        query.setParameter("demographicNo", demographic);
        List<WCB> list = query.getResultList();
        return list;
    }

    public List<WCB> getWCBForms(String demographic) {
        return getWCBForms(Integer.parseInt(demographic));
    }

    public WCB getWCBForm(String formID) {
        if (formID == null) {
            return null;
        }
        MiscUtils.getLogger().debug("\nFORM ID " + formID);
        return entityManager.find(WCB.class, Integer.parseInt(formID));
    }

    public Billingmaster getBillingMasterByBillingMasterNo(String billingNo) {
        return getBillingmaster(billingNo);
    }

    public int markListAsBilled(List<String> list) { //TODO: Should be set form CONST var
        if (list.size() == 0) {
            return 0;
        }
        Query query = entityManager.createQuery("UPDATE Billingmaster b set b.billingstatus = 'B' where b.billingmasterNo in (:billingNumbers)");
        query.setParameter("billingNumbers", ConversionUtils.toIntList(list));
        return query.executeUpdate();
    }

    /**
     * Sets the specified billing unit for the billing master with the specified number.
     *
     * @param billingUnit Billing unit to be set on the billing master
     * @param billingNo   Number of the billing master to be updated
     * @return Returns the total number of rows affected by the operation
     */
    public int updateBillingUnitForBillingNumber(String billingUnit, Integer billingNo) {
        Query query = entityManager.createQuery("UPDATE " + Billingmaster.class.getSimpleName() + " b SET b.billingUnit = :billingUnit WHERE b.billingNo = :billingNo");
        query.setParameter("billingUnit", billingUnit);
        query.setParameter("billingNo", billingNo);
        return query.executeUpdate();
    }


    public WCB getWcbByBillingNo(Integer billing_no) {
        Query query = entityManager.createQuery("FROM WCB w WHERE w.billing_no = :billingNo");
        query.setParameter("billingNo", billing_no);
        query.setMaxResults(1);

        List<WCB> ws = query.getResultList();
        if (ws.isEmpty())
            return null;
        return ws.get(0);
    }


    public List<Object[]> findByStatus(String status) {
        Query query = entityManager.createQuery("SELECT b, bm FROM Billing b, Billingmaster bm " +
                "WHERE b.id = bm.billingNo " +
                "AND bm.billingstatus = :st");
        query.setParameter("st", status);
        return query.getResultList();
    }


    @NativeSql({"billingmaster", "billing"})
    public List<Object[]> getBillingMasterByVariousFields(String statusType, String providerNo, String startDate, String endDate) {
        StringBuilder sb = new StringBuilder(" select "
                + "b.billing_no, "
                + "b.demographic_no, "
                + "b.demographic_name, "
                + "b.update_date, "
                + "b.status, "
                + "b.apptProvider_no, "
                + "b.appointment_no, "
                + "b.billing_date, "
                + "b.billing_time, "
                + "bm.billingstatus, "
                + "bm.bill_amount, "
                + "bm.billing_code, "
                + "bm.dx_code1, "
                + "bm.dx_code2, "
                + "bm.dx_code3,"
                + "b.provider_no, "
                + "b.visitdate, "
                + "b.visittype,"
                + "bm.billingmaster_no "
                + "from billing b, "
                + " billingmaster bm where b.billing_no= bm.billing_no and bm.billingstatus = :statusType");

        if (providerNo != null && !providerNo.trim().equalsIgnoreCase("all")) {
            sb.append(" and provider_no = :providerNo");
        }

        if (startDate != null && !startDate.trim().equalsIgnoreCase("")) {
            sb.append(" and ( to_days(service_date) > to_days(:startDate)) ");
        }

        if (endDate != null && !endDate.trim().equalsIgnoreCase("")) {
            sb.append(" and ( to_days(service_date) < to_days(:endDate)) ");
        }

        Query query = entityManager.createNativeQuery(sb.toString());
        query.setParameter("statusType", statusType);

        if (providerNo != null && !providerNo.trim().equalsIgnoreCase("all")) {
            query.setParameter("providerNo", providerNo);
        }

        if (startDate != null && !startDate.trim().equalsIgnoreCase("")) {
            query.setParameter("startDate", startDate);
        }

        if (endDate != null && !endDate.trim().equalsIgnoreCase("")) {
            query.setParameter("endDate", endDate);
        }

        return query.getResultList();
    }

    public List<Object[]> select_user_bill_report_wcb(Integer billingMasterNo) {
        Query q = entityManager.createNativeQuery(" SELECT b.demographic_no, b.billingmaster_no, d.first_name, d.last_name, d.address, d.city, d.province, d.postal," +
                " d.hin, d.month_of_birth, d.date_of_birth, d.year_of_birth, b.practitioner_no, b.billing_code, w.bill_amount, b.billing_unit, b.service_date," +
                " b.billing_no, t.t_dataseq,  w.w_servicelocation, w.w_icd9 AS w_icd9_1, w.w_reporttype, w.w_mname, w.w_gender, w.w_doi, w.w_area, w.w_phone, w.w_empname, " +
                "w.w_emparea, w.w_empphone, w.w_wcbno, w.w_opaddress, w.w_opcity, w.w_rphysician, w.w_duration, w.w_ftreatment, w.w_problem, w.w_servicedate," +
                "w.w_diagnosis, w.w_icd9 AS w_icd9_2, w.w_bp, w.w_side, w.w_noi, w.w_work, w.w_workdate, w.w_clinicinfo, w.w_capability, w.w_capreason, w.w_estimate, w.w_rehab," +
                "w.w_rehabtype, w.w_estimatedate, w.w_tofollow, w.w_wcbadvisor, w.w_feeitem, w.w_extrafeeitem, b.billingstatus, w.formNeeded, w.provider_no, w.w_payeeno, w.w_pracno " +
                " FROM billingmaster b LEFT JOIN teleplanC12  t ON t.t_officefolioclaimno=b.billingmaster_no, demographic d, wcb w " +
                "WHERE b.demographic_no=d.demographic_no AND b.billing_no=w.billing_no AND b.billingmaster_no=?");

        q.setParameter(1, billingMasterNo);

        @SuppressWarnings("unchecked")
        List<Object[]> results = q.getResultList();

        return results;
    }

    public List<Billing> search_teleplanbill(Integer billingmasterNo) {
        Query q = entityManager.createQuery("select b from Billing b, Billingmaster bm where b.id= bm.billingNo and bm.billingmasterNo=?1");
        q.setParameter(1, billingmasterNo);

        @SuppressWarnings("unchecked")
        List<Billing> results = q.getResultList();

        return results;
    }

    public List<Billingmaster> findByDemoNoCodeAndStatuses(Integer demoNo, String billingCode, List<String> statuses) {
        Query query = entityManager.createQuery("FROM Billingmaster bm " +
                "WHERE bm.demographicNo = :demoNo " +
                "AND bm.billingCode = :billingCode " +
                "AND bm.billingstatus NOT IN (:statuses)");
        query.setParameter("demoNo", demoNo);
        query.setParameter("billingCode", billingCode);
        query.setParameter("statuses", statuses);
        return query.getResultList();
    }

    public List<Billingmaster> findByDemoNoCodeStatusesAndYear(Integer demoNo, Date date, String billingCode) {
        // serviceDate is stored as String in yyyyMMdd format, so use SUBSTRING to extract the year
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        String yearStr = String.valueOf(cal.get(java.util.Calendar.YEAR));

        Query query = entityManager.createQuery(
                "FROM Billingmaster bm WHERE bm.demographicNo = :demoNo AND bm.billingCode = :billingCode AND SUBSTRING(bm.serviceDate, 1, 4) = :year AND bm.billingstatus != 'D'");
        query.setParameter("demoNo", demoNo);
        query.setParameter("year", yearStr);
        query.setParameter("billingCode", billingCode);
        return query.getResultList();
    }

    public List<Object[]> getRecentReferralDoctors(Integer demographicNo) {
        Query query = entityManager.createQuery("SELECT b.referralNo1, b.referralNo2 FROM Billingmaster b WHERE b.demographicNo = ?1 AND (b.referralNo1!='' OR b.referralNo2!='') ORDER BY b.billingmasterNo DESC");
        query.setParameter(1, demographicNo);
        return query.setMaxResults(3).getResultList();
    }

    public List<Billingmaster> findBillingsByChronicCodes(Integer demographic_no, String chroniccodes) {
        String sql = "select bm from Billingmaster bm where bm.demographicNo = :demographic and bm.billingCode in (:chroniccodes)";
        Query query = entityManager.createQuery(sql);

        List<String> codeList = Arrays.asList(chroniccodes.split(","));

        query.setParameter("chroniccodes", codeList);
        query.setParameter("demographic", demographic_no);

        @SuppressWarnings("unchecked")
        List<Billingmaster> billings = query.getResultList();
        return billings;
    }
}
