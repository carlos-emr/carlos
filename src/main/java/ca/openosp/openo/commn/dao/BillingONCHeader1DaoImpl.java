//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package ca.openosp.openo.commn.dao;

import java.math.BigDecimal;
import java.math.BigInteger;
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

import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.PMmodule.utility.DateUtils;
import ca.openosp.openo.billing.CA.ON.model.BillingPercLimit;
import ca.openosp.openo.billing.CA.dao.GstControlDao;
import ca.openosp.openo.billing.CA.model.GstControl;
import ca.openosp.openo.commn.model.BillingONCHeader1;
import ca.openosp.openo.commn.model.BillingONItem;
import ca.openosp.openo.commn.model.BillingService;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.DateRange;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import ca.openosp.OscarProperties;
import ca.openosp.openo.billings.ca.on.data.BillingDataHlp;
import ca.openosp.openo.billings.ca.on.pageUtil.BillingStatusPrep;
import ca.openosp.openo.util.ParamAppender;

/**
 * @author Eugene Katyukhin
 */

@Repository
@SuppressWarnings("unchecked")
public class BillingONCHeader1DaoImpl extends AbstractDaoImpl<BillingONCHeader1> implements BillingONCHeader1Dao {

    @Autowired
    private DemographicDao demographicDao;
    @Autowired
    private ProviderDao providerDao;
    @Autowired
    private BillingServiceDao billingServiceDao;
    @Autowired
    private GstControlDao gstControlDao;

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
        BigInteger bint = (BigInteger) query.getSingleResult();
        return bint.intValue();
    }

    @Override
    public void createBills(List<BillingONCHeader1> lBills) {
        for (BillingONCHeader1 b : lBills) {
            this.persist(b);
        }
    }

    @Override
    public String createBill(String provider, Integer demographic, String code, String clinicRefCode, Date serviceDate,
                             String curUser) {
        BillingONCHeader1 header1 = null;
        Provider prov = providerDao.getProvider(provider);
        OscarProperties properties = OscarProperties.getInstance();
        ArrayList<String> codes = new ArrayList<String>();
        ArrayList<String> dxCodes = new ArrayList<String>();

        codes.add(code);
        String total = this.calcTotal(codes, serviceDate);

        header1 = this.assembleHeader1(prov, demographic, clinicRefCode, serviceDate, total, curUser, properties);
        if (header1 == null)
            return null;
        this.addItems(header1, codes, dxCodes, serviceDate);
        this.persist(header1);

        return total;
    }

    @Override
    public String createBill(String provider, Integer demographic, String code, String dxCode, String clinicRefCode,
                             Date serviceDate, String curUser) {
        BillingONCHeader1 header1 = null;
        Provider prov = providerDao.getProvider(provider);
        OscarProperties properties = OscarProperties.getInstance();
        ArrayList<String> codes = new ArrayList<String>();
        ArrayList<String> dxCodes = new ArrayList<String>();

        codes.add(code);
        dxCodes.add(dxCode);

        String total = this.calcTotal(codes, serviceDate);

        header1 = this.assembleHeader1(prov, demographic, clinicRefCode, serviceDate, total, curUser, properties);
        if (header1 == null)
            return null;
        this.addItems(header1, codes, dxCodes, serviceDate);
        this.persist(header1);

        return total;
    }

    @Override
    public String createBills(String provider, List<String> demographic_nos, List<String> codes, List<String> dxcodes,
                              String clinicRefCode, Date serviceDate, String curUser) {
        BillingONCHeader1 header1 = null;
        Provider prov = providerDao.getProvider(provider);
        OscarProperties properties = OscarProperties.getInstance();

        String total = calcTotal(codes, serviceDate);
        for (String demographic : demographic_nos) {
            header1 = this.assembleHeader1(prov, Integer.parseInt(demographic), clinicRefCode, serviceDate, total,
                    curUser, properties);
            if (header1 == null)
                continue;
            this.addItems(header1, codes, dxcodes, serviceDate);
            this.persist(header1);
        }

        return total;
    }

    private BillingONCHeader1 assembleHeader1(Provider prov, Integer demographic, String clinicRefCode,
                                              Date serviceDate, String total, String curUser, OscarProperties properties) {

        BillingONCHeader1 header1 = new BillingONCHeader1();
        header1.setTranscId(BillingDataHlp.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        header1.setRecId(BillingDataHlp.CLAIMHEADER1_REORDIDENTIFICATION);
        header1.setHeaderId(0);

        Demographic demo = demographicDao.getDemographicById(demographic);

        if (demo == null) {
            return null;
        }
        header1.setHin(demo.getHin());
        header1.setVer(demo.getVer());
        header1.setDob(demo.getDateOfBirth());
        String payProg = demo.getHcType().equals("ON") ? "HCP" : "RMB";
        header1.setPayProgram(payProg);
        header1.setPayee(BillingDataHlp.CLAIMHEADER1_PAYEE);
        header1.setRefNum("");
        header1.setFaciltyNum(clinicRefCode);
        // header1.setAdmissionDate(null);
        header1.setRefLabNum("");
        header1.setManReview("");
        header1.setLocation(properties.getProperty("clinic_no", ""));
        header1.setDemographicNo(Integer.valueOf(demographic));
        header1.setProviderNo(prov.getProviderNo());
        header1.setAppointmentNo(0);
        header1.setDemographicName(demo.getLastName() + "," + demo.getFirstName());
        header1.setSex(demo.getSex());
        header1.setProvince(demo.getHcType());
        header1.setBillingDate(serviceDate);
        header1.setBillingTime(serviceDate);
        header1.setPaid(new BigDecimal("0.00"));
        header1.setStatus("O");
        header1.setComment("");
        header1.setVisitType("00");
        header1.setProviderOhipNo(prov.getOhipNo());
        header1.setProviderRmaNo(prov.getRmaNo());
        header1.setApptProviderNo("");
        header1.setAsstProviderNo("");
        header1.setCreator(curUser);
        header1.setTotal(new BigDecimal(total));

        return header1;
    }

    private void addItems(BillingONCHeader1 h1, List<String> codes, List<String> dxcodes, Date serviceDate) {

        BillingService billingService = null;
        BillingONItem item = null;
        for (String code : codes) {
            item = new BillingONItem();
            item.setTranscId(BillingDataHlp.ITEM_TRANSACTIONIDENTIFIER);
            item.setRecId(BillingDataHlp.ITEM_REORDIDENTIFICATION);
            item.setServiceCode(code);

            billingService = billingServiceDao.searchBillingCode(code, "ON", serviceDate);
            item.setFee(billingService.getValue());
            item.setServiceCount("1");
            item.setServiceDate(serviceDate);
            item.setStatus("O");

            if (dxcodes.size() == 1) {
                item.setDx(dxcodes.get(0));
                item.setDx1("");
                item.setDx2("");
            } else if (dxcodes.size() == 2) {
                item.setDx(dxcodes.get(0));
                item.setDx1(dxcodes.get(1));
                item.setDx2("");
            } else if (dxcodes.size() == 3) {
                item.setDx(dxcodes.get(0));
                item.setDx1(dxcodes.get(1));
                item.setDx2(dxcodes.get(2));
            } else {
                item.setDx("");
                item.setDx1("");
                item.setDx2("");
            }

            h1.getBillingItems().add(item);
        }
    }

    private String calcTotal(List<String> codes, Date serviceDate) {
        GstControl gstControl = gstControlDao.find(Integer.valueOf(1));
        BigDecimal gst;
        BigDecimal gstTotal;
        BigDecimal total = new BigDecimal(0.0);
        BigDecimal percent = new BigDecimal(0.0);
        BillingPercLimit billingPerc;
        ArrayList<BillingService> aPercentCodes = new ArrayList<BillingService>();
        BillingService billingservice = null;
        for (String code : codes) {
            billingservice = billingServiceDao.searchBillingCode(code, "ON", serviceDate);

            if (billingservice != null && billingservice.getPercentage() != null
                    && !billingservice.getPercentage().equalsIgnoreCase("")) {
                // billingPerc = billingservice
                aPercentCodes.add(billingservice);

            } else {
                if (billingservice != null && billingservice.getGstFlag()) {
                    gst = gstControl.getGstPercent();
                    gst = gst.divide(new BigDecimal(100.0));
                    gstTotal = gst.multiply(new BigDecimal(Double.parseDouble(billingservice.getValue())));
                    total = total.add(gstTotal).setScale(2, BigDecimal.ROUND_HALF_UP);
                }

                if (billingservice != null)
                    total = total.add(new BigDecimal(billingservice.getValue()));
            }
        }

        BigDecimal percBase = total;
        BigDecimal percentCalc;
        for (BillingService percentcode : aPercentCodes) {
            percent = new BigDecimal(Double.parseDouble(percentcode.getPercentage())).setScale(2,
                    BigDecimal.ROUND_HALF_UP);
            percentCalc = percBase.multiply(percent).setScale(2, BigDecimal.ROUND_HALF_UP);
            billingPerc = percentcode.getBillingPercLimit();
            if (billingPerc != null) {
                percentCalc = percentCalc.min(new BigDecimal(Double.parseDouble(billingPerc.getMax())));
                percentCalc = percentCalc.max(new BigDecimal(Double.parseDouble(billingPerc.getMin())));
            }

            total = total.add(percentCalc);
        }
        total.setScale(2, BigDecimal.ROUND_UP);
        return total.toString();
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

    // @Override
    // public GstControlDao getGstControlDao() {
    //     return gstControlDao;
    // }

    // @Override
    // public void setGstControlDao(GstControlDao gstControlDao) {
    //     this.gstControlDao = gstControlDao;
    // }

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
    public List<Object[]> countBillingVisitsByProvider(String providerNo, Date dateBegin, Date dateEnd) {
        String sql = "SELECT b.visitType, count(b) FROM BillingONCHeader1 b "
                + "WHERE b.status <> 'D' "
                + "AND b.appointmentNo <> '0' "
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
                + "AND b.appointmentNo <> '0' "
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
        String sql = "FROM BillingONCHeader1 b, Demographic d WHERE b.id = ?1 AND b.demographicNo = d.DemographicNo";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        return query.getResultList();
    }

    @Override
    public List<BillingONCHeader1> findByMagic(List<String> payPrograms, String statusType, String providerNo,
                                               Date startDate, Date endDate, Integer demoNo, String visitLocation, Date paymentStartDate,
                                               Date paymentEndDate) {
        ParamAppender app = new ParamAppender("FROM BillingONCHeader1 h, BillingONPayment bp ");
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
        String base = "FROM BillingONCHeader1 ch1, BillingONItem bi";
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

        if (visitLocation != null && !BillingStatusPrep.ANY_VISIT_LOCATION.equals(visitLocation)) {
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
        ParamAppender app = new ParamAppender("FROM BillingONCHeader1 bch, Demographic d");
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
        String sql = "FROM Demographic d, BillingONCHeader1 bc, BillingONItem bi " +
                "WHERE bc.demographicNo = d.DemographicNo " +
                "AND bc.id = bi.ch1Id " +
                "AND bi.dx in (?1) " +
                "AND bi.serviceDate >= ?2 and bi.serviceDate <= ?3 " +
                "GROUP BY d.demographicNo, bi.dx " +
                "ORDER BY d.demographicNo, bi.serviceDate";
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

}
