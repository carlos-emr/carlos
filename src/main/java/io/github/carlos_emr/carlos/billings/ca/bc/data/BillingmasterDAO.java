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
import io.github.carlos_emr.carlos.util.UtilMisc;
import java.util.ArrayList;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.*;
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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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


    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
                + " billingmaster bm where b.billing_no= bm.billing_no");

        boolean hasStatusType = statusType != null && !statusType.trim().isEmpty();
        boolean hasProviderNo = providerNo != null && !providerNo.trim().equalsIgnoreCase("all");
        boolean hasStartDate = startDate != null && !startDate.trim().isEmpty();
        boolean hasEndDate = endDate != null && !endDate.trim().isEmpty();

        if (hasStatusType) {
            sb.append(" and bm.billingstatus = :statusType");
        }

        if (hasProviderNo) {
            sb.append(" and b.provider_no = :providerNo");
        }

        if (hasStartDate) {
            sb.append(" and ( to_days(service_date) > to_days(:startDate)) ");
        }

        if (hasEndDate) {
            sb.append(" and ( to_days(service_date) < to_days(:endDate)) ");
        }

        Query query = entityManager.createNativeQuery(sb.toString());

        if (hasStatusType) {
            query.setParameter("statusType", statusType.trim());
        }

        if (hasProviderNo) {
            query.setParameter("providerNo", providerNo.trim());
        }

        if (hasStartDate) {
            query.setParameter("startDate", startDate.trim());
        }

        if (hasEndDate) {
            query.setParameter("endDate", endDate.trim());
        }

        return query.getResultList();
    }

    public List<Object[]> select_user_bill_report_wcb(Integer billingMasterNo) {
        // w_duration is a numeric code (1, 2, 9) stored as TINYINT(1), not a Boolean.
        // Widen the SQL expression so the driver retains its numeric value.
        Query q = entityManager.createNativeQuery(" SELECT b.demographic_no, b.billingmaster_no, d.first_name, d.last_name, d.address, d.city, d.province, d.postal," +
                " d.hin, d.month_of_birth, d.date_of_birth, d.year_of_birth, b.practitioner_no, b.billing_code, w.bill_amount, b.billing_unit, b.service_date," +
                " b.billing_no, t.t_dataseq,  w.w_servicelocation, w.w_icd9 AS w_icd9_1, w.w_reporttype, w.w_mname, w.w_gender, w.w_doi, w.w_area, w.w_phone, w.w_empname, " +
                "w.w_emparea, w.w_empphone, w.w_wcbno, w.w_opaddress, w.w_opcity, w.w_rphysician, w.w_duration + 0 AS w_duration, w.w_ftreatment, w.w_problem, w.w_servicedate," +
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

    /** Materializes the legacy BC native read on the caller transaction. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<BillRow> findBcBills(String account, String payeeNo, String providerNo,
            String startDate, String endDate, boolean excludeWCB, boolean excludeMSP,
            boolean excludePrivate, boolean exludeICBC, String type) {
        List<Object> criteriaParams = new ArrayList<>();
        String criteriaQry = createBcCriteriaString(account, payeeNo, providerNo, startDate, endDate, excludeWCB, excludeMSP, excludePrivate, exludeICBC, type, "", criteriaParams);
        String orderByClause = "order by billingstatus";

        if (MSPReconcile.REP_ACCOUNT_REC.equals(type)) {
            orderByClause = "order by bs.sortOrder, bm.paymentMethod, b.demographic_name, bm.service_date";
        } else if (MSPReconcile.REP_INVOICE.equals(type)) {
            orderByClause = "order by b.provider_no, bt.sortOrder, bm.service_date, b.demographic_name";
        }
        String p = "select provider.first_name, provider.last_name, b.billingtype, b.update_date, bm.billingmaster_no, b.billing_no, " + " b.demographic_name, b.demographic_no, bm.billing_unit, bm.billing_code, bm.bill_amount, bm.billingstatus, bm.mva_claim_code, bm.service_location," + " bm.phn, bm.service_end_time, service_start_time, bm.service_to_day, bm.service_date, bm.oin_sex_code, b.dob, dx_code1, b.provider_no, apptProvider_no, bt.sortOrder "
                + " from demographic, provider, billing as b left join billingtypes bt on b.billingtype = bt.billingtype, billingmaster as bm left join billingstatus_types bs on bm.billingstatus = bs.billingstatus" + " where bm.billing_no=b.billing_no " + " and b.provider_no = provider.provider_no " + " and demographic.demographic_no = b.demographic_no " + criteriaQry + " " + orderByClause;
        return readBcRows(p, criteriaParams.toArray(), rs -> new BillRow(rs.getString("b.billingtype"), rs.getString("billing_no"), rs.getString("demographic_no"), rs.getString("billing_unit"), rs.getString("demographic_name"), rs.getString("update_date"), rs.getString("billingstatus"), rs.getString("service_end_time"), rs.getString("service_start_time"), rs.getString("service_to_day"), rs.getString("billingmaster_no"), rs.getString("bill_amount"), rs.getString("billing_code"), rs.getString("dx_code1"), rs.getString("service_date"), rs.getString("mva_claim_code"), rs.getString("phn"), rs.getString("service_location"), rs.getString("dob"), rs.getString("oin_sex_code"), rs.getString("apptProvider_no"), rs.getString("b.provider_no"), rs.getString("first_name"), rs.getString("last_name")));
    }

    /** Materializes the legacy BC native read on the caller transaction. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<PaymentRow> findBcPayments(String account, String payeeNo, String providerNo,
            String startDate, String endDate, boolean excludeWCB, boolean excludeMSP,
            boolean excludePrivate, boolean exludeICBC) {
        List<Object> criteriaParams = new ArrayList<>();
        String criteriaQry = createBcCriteriaString(account, payeeNo, providerNo, UtilMisc.replace(startDate, "-", ""), UtilMisc.replace(endDate, "-", ""), excludeWCB, excludeMSP, excludePrivate, exludeICBC, MSPReconcile.REP_PAYREF, "", criteriaParams);
        String p = "SELECT teleplanS00.t_payment, b.billingtype, b.demographic_name, apptProvider_no, provider_no, payee_no, b.demographic_no, teleplanS00.t_paidamt, t_exp1, t_exp2, t_dataseq, bm.service_date, bm.paymentMethod, teleplanS00.t_ajc1," + " teleplanS00.t_aja1, teleplanS00.t_aja2, teleplanS00.t_aja3, teleplanS00.t_aja4, teleplanS00.t_aja5, teleplanS00.t_aja6, teleplanS00.t_aja7, bm.billingmaster_no, teleplanS00.t_practitionerno"
                + " FROM teleplanS00 left join billingmaster as bm on teleplanS00.t_officeno = bm.billingmaster_no, billing as b" + " where b.billing_no = bm.billing_no" + criteriaQry + " and bm.billingstatus != 'D'" + " order by t_payment";
        return readBcRows(p, criteriaParams.toArray(), rs -> new PaymentRow(rs.getString("b.billingtype"), rs.getString("t_payment"), rs.getString("paymentMethod"), rs.getString("demographic_no"), rs.getString("demographic_name"), rs.getString("t_paidamt"), rs.getString("bm.billingmaster_no"), rs.getString("service_date"), rs.getString("t_dataseq"), rs.getString("t_exp1"), rs.getString("t_exp2"), rs.getString("t_practitionerno"), rs.getString("provider_no"), rs.getString("payee_no"), rs.getString("teleplanS00.t_ajc1"), rs.getString("teleplanS00.t_aja1"), rs.getString("teleplanS00.t_aja2"), rs.getString("teleplanS00.t_aja3"), rs.getString("teleplanS00.t_aja4"), rs.getString("teleplanS00.t_aja5"), rs.getString("teleplanS00.t_aja6"), rs.getString("teleplanS00.t_aja7")));
    }

    /** Materializes the legacy BC native read on the caller transaction. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<PrivatePaymentRow> findBcPrivatePayments(String account, String payeeNo, String providerNo,
            String startDate, String endDate) {
        List<Object> criteriaParams = new ArrayList<>();
        String criteriaQry = createBcCriteriaString(account, payeeNo, providerNo, startDate, endDate, true, true, false, true, "", "creation_date", criteriaParams);
        String p = "SELECT b.billingtype, bm.billingmaster_no, b.demographic_no, b.demographic_name, bm.service_date, b.apptProvider_no, b.provider_no, bm.payee_no," + " bh.creation_date, bh.amount_received, payment_type_id" + " FROM billing_history bh left join billingmaster bm on bh.billingmaster_no = bm.billingmaster_no, billing b" + " where bm.billing_no = b.billing_no " + " and bh.payment_type_id != " + MSPReconcile.PAYTYPE_IA + " " + criteriaQry + " and bm.billingstatus != '" + MSPReconcile.DELETED + "'";
        return readBcRows(p, criteriaParams.toArray(), rs -> new PrivatePaymentRow(rs.getString("bm.billingmaster_no"), rs.getString("b.billingtype"), rs.getString("demographic_no"), rs.getString("demographic_name"), rs.getString("service_date"), rs.getString("apptProvider_no"), rs.getString("provider_no"), rs.getString("payee_no"), rs.getString("amount_received"), rs.getDate("creation_date"), rs.getString("payment_type_id")));
    }

    // FindSecBugs IMPROPER_UNICODE: the legacy ALL report sentinel is case-insensitive, not an authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive legacy ALL report sentinel; not an authorization decision")
    private String createBcCriteriaString(String account, String payeeNo, String providerNo, String startDate, String endDate, boolean excludeWCB, boolean excludeMSP, boolean excludePrivate, boolean exludeICBC, String repType, String dateFieldOption, List<Object> criteriaParams) {
        String criteriaQry = "";
        String dateField = MSPReconcile.REP_PAYREF.equals(repType) ? "t_payment" : "service_date";

        // The native date field is chosen only from these fixed report columns.

        if ("creation_date".equals(dateFieldOption)) {
            dateField = "creation_date";
        }
        if (providerNo != null && !providerNo.trim().equalsIgnoreCase("all")) {
            if (MSPReconcile.REP_PAYREF.equals(repType)) {
                List<String> providers = readBcRows("select ohip_no from provider where provider_no = ?", new Object[]{providerNo}, rs -> rs.getString(1));
                String[] row = providers.isEmpty() ? null : new String[]{providers.getFirst()};
                if (row != null && row.length > 0) {
                    String ohip_no = row[0];
                    criteriaQry += " and t_practitionerno = ?";
                    criteriaParams.add(ohip_no);
                } else {
                    throw new RuntimeException("Provider must have ohip no!");
                }
            } else {
                criteriaQry += " and b.apptProvider_no = ?";
                criteriaParams.add(providerNo);
            }
        }

        if (payeeNo != null && !payeeNo.trim().equalsIgnoreCase("all")) {
            criteriaQry += " and bm.payee_no LIKE ?";
            criteriaParams.add(payeeNo);
        }
        if (account != null && !account.trim().equalsIgnoreCase("all")) {
            criteriaQry += " and b.provider_no LIKE ?";
            criteriaParams.add(account);
        }
        if (startDate != null && !startDate.trim().equalsIgnoreCase("")) {
            criteriaQry += " and ( to_days(" + dateField + ") >= to_days(?)) ";
            criteriaParams.add(startDate);
        }

        if (endDate != null && !endDate.trim().equalsIgnoreCase("")) {
            criteriaQry += " and ( to_days(" + dateField + ") <= to_days(?)) ";
            criteriaParams.add(endDate);
        }
        //put this crap in a Map and use an 'in' clause instead
        if (excludeWCB) {
            criteriaQry += " and b.billingType != 'WCB' ";
        }

        if (excludeMSP) {
            criteriaQry += " and b.billingType != 'MSP' ";
        }

        if (excludePrivate) {
            criteriaQry += " and b.billingType != 'Pri' ";
        }

        if (exludeICBC) {
            criteriaQry += " and b.billingType != 'ICBC' ";
        }

        if (repType.equals(MSPReconcile.REP_REJ)) {
            criteriaQry += " and bm.billingstatus = '" + MSPReconcile.REJECTED + "'";
        } else if (repType.equals(MSPReconcile.REP_INVOICE)) {
            criteriaQry += " and bm.billingstatus != '" + MSPReconcile.DELETED + "'";
        } else if (repType.equals(MSPReconcile.REP_ACCOUNT_REC)) {
            criteriaQry += " and bm.billingstatus not in('" + MSPReconcile.DELETED + "','" + MSPReconcile.BADDEBT + "')";
        } else if (repType.equals(MSPReconcile.REP_WO)) {
            criteriaQry += " and bm.billingstatus = '" + MSPReconcile.BADDEBT + "'";
        }
        return criteriaQry;
    }


    /** Matches the original submission date LIKE pattern and data centre. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean hasBcMatchingBillingRecord(String billingmasterNo, String receivedDate, String dataCenterNo) {
        String qry = "SELECT tsl.id \n" +
                "FROM teleplan_submission_link tsl \n" +
                "JOIN billingmaster bm on bm.billingmaster_no = tsl.billingmaster_no\n" +
                "JOIN billactivity ba on ba.id = tsl.bill_activity_id\n" +
                "WHERE tsl.billingmaster_no = ?\n" +
                "AND bm.datacenter = ?\n" +
                "AND ba.sentdate like ?";
        return !readBcRows(qry, new Object[]{billingmasterNo, dataCenterNo, receivedDate}, rs -> rs.getInt(1)).isEmpty();
    }

    /** Reads only the report fields from the existing WCB mapping. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public WcbRow findBcWcb(String billingNo) {
        List<WCB> forms = entityManager.createQuery("from WCB w where w.billing_no = :billingNo", WCB.class)
                .setParameter("billingNo", Integer.parseInt(billingNo)).setMaxResults(1).getResultList();
        if (forms.isEmpty()) return null;
        WCB wcb = forms.getFirst();
        return new WcbRow(wcb.getBill_amount(), wcb.getW_feeitem(), wcb.getW_icd9());
    }

    @FunctionalInterface
    private interface BcRowMapper<T> {
        T map(java.sql.ResultSet rows) throws java.sql.SQLException;
    }

    // Keep MariaDB TO_DAYS, decimal coercion, LIKE, NULL and ordering semantics. The
    // connection belongs to the persistence context; only our statement/cursor is closed here.
    private <T> List<T> readBcRows(String sql, Object[] params, BcRowMapper<T> mapper) {
        if (entityManager.isJoinedToTransaction()) entityManager.flush();
        return entityManager.unwrap(org.hibernate.Session.class).doReturningWork(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
                try (var rows = statement.executeQuery()) {
                    List<T> result = new java.util.ArrayList<>();
                    while (rows.next()) result.add(mapper.map(rows));
                    return result;
                }
            }
        });
    }


    /** Legacy extract restriction uses provider_ohip_no, not the account's provider_no. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Billing> findBcExtractBills(String providerOhipNo, String dateRange, String[] dates) {
        if (!dateRange.isEmpty() && !dateRange.matches("(?i)^\\s*(?:and\\s+(?:billing_date|visitdate)\\s*(?:=|>=|<=|>|<)\\s*\\?\\s*)+$")) {
            throw new IllegalArgumentException("Unsupported billing date range filter");
        }
        Query query = entityManager.createNativeQuery("select * from billing where provider_ohip_no=?1 and (status='O' or status='W') "
                + numberBcDateParameters(dateRange), Billing.class);
        query.setParameter(1, providerOhipNo);
        for (int i = 0; i < dates.length; i++) query.setParameter(i + 2, dates[i]);
        return query.getResultList();
    }

    private String numberBcDateParameters(String filter) {
        StringBuilder result = new StringBuilder();
        int parameter = 2;
        for (char ch : filter.toCharArray()) {
            if (ch == '?') result.append('?').append(parameter++);
            else result.append(ch);
        }
        return result.toString();
    }

    /** Selects open claim entities without suspending the extract's transaction. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Billingmaster> findBcExtractClaims(int billingNo) {
        return entityManager.createQuery("from Billingmaster b where b.billingNo = :billingNo and b.billingstatus = 'O'", Billingmaster.class)
                .setParameter("billingNo", billingNo).getResultList();
    }

    /** Participates in the service boundary; existing REQUIRES_NEW write callers retain their policy. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markBcMasterBilled(int id) {
        Billingmaster claim = entityManager.find(Billingmaster.class, id);
        if (claim != null) claim.setBillingstatus("B");
    }

    /** Detached remittance fields; Jasper field names and native ordering are preserved. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<RemittanceRow> findBcRemittance(String payeeNo, String s21Id) {
        String qry = "SELECT billing_code, provider.first_name, provider.last_name, t_practitionerno, t_s00type, billingmaster.service_date as 't_servicedate', t_payment," + "t_datacenter, billing.demographic_name, billing.demographic_no, teleplanS00.t_paidamt, t_exp1, t_exp2, t_exp3, t_exp4, t_exp5, t_exp6, t_dataseq " + " from teleplanS00, billing, billingmaster, provider " + " where teleplanS00.t_officeno = billingmaster.billingmaster_no " + " and teleplanS00.s21_id = ?"
                + " and billingmaster.billing_no = billing.billing_no " + " and provider.ohip_no= teleplanS00.t_practitionerno " + " and teleplanS00.t_practitionerno NOT LIKE '' and teleplanS00.t_payeeno LIKE ? order by provider.first_name, t_servicedate, billing.demographic_name";
        return readBcRows(qry, new Object[]{s21Id, payeeNo}, rs -> new RemittanceRow(rs.getString("billing_code"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("t_practitionerno"), rs.getString("t_s00type"), rs.getString("t_servicedate"), rs.getString("t_payment"), rs.getString("t_datacenter"), rs.getString("demographic_name"), rs.getObject("demographic_no", Integer.class), rs.getString("t_paidamt"), rs.getString("t_exp1"), rs.getString("t_exp2"), rs.getString("t_exp3"), rs.getString("t_exp4"), rs.getString("t_exp5"), rs.getString("t_exp6"), rs.getString("t_dataseq")));
    }

    /** Detached remittance fields; Jasper field names and native ordering are preserved. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<AdjustmentRemittanceRow> findBcAdjustmentRemittance(String payeeNo, String s21Id) {
        String qry = "SELECT b.billing_no, bm.billingmaster_no, bm.billing_code, p.first_name, p.last_name, ts00.t_practitionerno, ts00.t_s00type, ts00.t_msprcddate, bm.service_date, ts00.t_payment, ts00.t_datacenter, b.demographic_name, b.demographic_no, \n" +
                "IF(t_paidamt/100>bm.bill_amount, \"+\", IF(t_paidamt/100<bm.bill_amount, \"-\", \"\")) as differential, ts00.t_paidamt, bm.bill_amount, \n" +
                "ts00.t_exp1, ts00.t_exp2, ts00.t_exp3, ts00.t_exp4, ts00.t_exp5, ts00.t_exp6, ts00.t_dataseq, \n" +
                "t_ajc1, t_aja1, t_ajc2, t_aja2, t_ajc3, t_aja3, t_ajc4, t_aja4, t_ajc5, t_aja5, t_ajc6, t_aja6, t_ajc7, t_aja7\n" +
                "\n" +
                "from teleplanS00 ts00\n" +
                "join billingmaster bm on ts00.t_officeno = bm.billingmaster_no\n" +
                "join billing b on bm.billing_no = b.billing_no\n" +
                "join provider p on p.ohip_no= ts00.t_practitionerno\n" +
                "where ts00.s21_id = ?\n" +
                "and ts00.t_practitionerno != '' \n" +
                "and ts00.t_payeeno = ?\n" +
                "order by p.first_name, bm.service_date, b.demographic_name;";
        return readBcRows(qry, new Object[]{s21Id, payeeNo}, rs -> new AdjustmentRemittanceRow(rs.getObject("billing_no", Integer.class), rs.getObject("billingmaster_no", Integer.class), rs.getString("billing_code"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("t_practitionerno"), rs.getString("t_s00type"), rs.getString("t_msprcddate"), rs.getString("service_date"), rs.getString("t_payment"), rs.getString("t_datacenter"), rs.getString("demographic_name"), rs.getObject("demographic_no", Integer.class), rs.getString("differential"), rs.getString("t_paidamt"), rs.getString("bill_amount"), rs.getString("t_exp1"), rs.getString("t_exp2"), rs.getString("t_exp3"), rs.getString("t_exp4"), rs.getString("t_exp5"), rs.getString("t_exp6"), rs.getString("t_dataseq"), rs.getString("t_ajc1"), rs.getString("t_aja1"), rs.getString("t_ajc2"), rs.getString("t_aja2"), rs.getString("t_ajc3"), rs.getString("t_aja3"), rs.getString("t_ajc4"), rs.getString("t_aja4"), rs.getString("t_ajc5"), rs.getString("t_aja5"), rs.getString("t_ajc6"), rs.getString("t_aja6"), rs.getString("t_ajc7"), rs.getString("t_aja7")));
    }
}
