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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.text.DateFormat;
import java.text.ParseException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

@Repository
@SuppressWarnings("unchecked")
public class BillingServiceDaoImpl extends AbstractDaoImpl<BillingService> implements BillingServiceDao {
    static public final String BC = "BC";

    public BillingServiceDaoImpl() {
        super(BillingService.class);
    }

    public List<BillingService> getBillingCodeAttr(String serviceCode) {

        String sql = "SELECT b FROM BillingService b where b.serviceCode=?1" +
                " AND b.billingserviceNo = (SELECT MAX(b2.billingserviceNo) from BillingService b2" +
                " where b.serviceCode = b2.serviceCode and b2.billingserviceDate <= ?2)";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, serviceCode);
        q.setParameter(2, new Date());


        List<BillingService> results = q.getResultList();

        return results;
    }

    public boolean codeRequiresSLI(String code) {
        Query query = entityManager.createQuery("select bs  from BillingService bs where bs.serviceCode like (?1) and sliFlag = TRUE");
        query.setParameter(1, code + "%");


        List<BillingService> list = query.getResultList();
        return list.size() > 0;
    }

    public List<BillingService> findBillingCodesByCode(String code, String region) {
        Query query = entityManager.createQuery("select bs  from BillingService bs where bs.serviceCode like (?1) and region = (?2) order by bs.billingserviceDate");
        query.setParameter(1, code + "%");
        query.setParameter(2, region);


        List<BillingService> list = query.getResultList();
        return list;
    }

    public List<BillingService> findByServiceCode(String code) {
        Query query = entityManager.createQuery("select bs  from BillingService bs where bs.serviceCode = ?1 order by bs.billingserviceDate desc");
        query.setParameter(1, code);


        List<BillingService> list = query.getResultList();
        return list;
    }

    public List<BillingService> findByServiceCodeAndDate(String code, Date date) {
        Query query = entityManager.createQuery("select bs  from BillingService bs where bs.serviceCode = ?1 and bs.billingserviceDate = ?2 order by bs.billingserviceDate desc");
        query.setParameter(1, code);
        query.setParameter(2, date);


        List<BillingService> list = query.getResultList();
        return list;
    }


    public List<BillingService> findByServiceCodes(List<String> codes) {
        Query query = entityManager.createQuery("select bs  from BillingService bs where bs.serviceCode IN (?1) order by bs.billingserviceDate desc");
        query.setParameter(1, codes);


        List<BillingService> list = query.getResultList();
        return list;
    }

    public List<BillingService> finAllPrivateCodes() {
        Query query = entityManager.createQuery("select bs from BillingService bs where bs.serviceCode LIKE ?1");
        query.setParameter(1, "\\_%");


        List<BillingService> list = query.getResultList();
        return list;
    }


    public List<BillingService> findBillingCodesByCode(String code, String region, int order) {
        return findBillingCodesByCode(code, region, new Date(), order);
    }

    public List<BillingService> findBillingCodesByCode(String code, String region, Date billingDate, int order) {
        String orderByClause = order == 1 ? "desc" : "";
        Query query = entityManager
                .createQuery("select bs  from BillingService bs where bs.region = (?1) and bs.serviceCode like (?2) and bs.billingserviceDate = (select max(b2.billingserviceDate) from BillingService b2 where b2.serviceCode = bs.serviceCode and b2.billingserviceDate <= (?3))  order by bs.serviceCode "
                        + orderByClause); // (:order) ");
        query.setParameter(1, region);
        query.setParameter(2, code + "%");
        query.setParameter(3, billingDate);
        // query.setParameter("order", orderByClause);


        List<BillingService> list = query.getResultList();

        return list;
    }

    public String searchDescBillingCode(String code, String region) {

        Query query = entityManager.createQuery("select bs  from BillingService bs where bs.description <> '' AND bs.description <> '----' AND bs.region = (?1) and bs.serviceCode like (?2) and bs.billingserviceDate = (select max(b2.billingserviceDate) from BillingService b2 where b2.serviceCode = bs.serviceCode and b2.billingserviceDate <= (?3))  order by bs.billingserviceDate desc");
        query.setParameter(1, region);
        query.setParameter(2, code + "%");
        query.setParameter(3, Calendar.getInstance().getTime());


        List<BillingService> list = query.getResultList();
        if (list.size() == 0) {
            return "----";
        }
        return list.get(0).getDescription();
    }

    public List<BillingService> search(String str, String region, Date billingDate) {

        Query query = entityManager.createQuery("select bs from BillingService bs where bs.region = (?1) and (bs.serviceCode like (?2) or bs.description like (?2)) and bs.billingserviceDate = (select max(b2.billingserviceDate) from BillingService b2 where b2.serviceCode = bs.serviceCode and b2.billingserviceDate <= (?3))");
        query.setParameter(1, region);
        query.setParameter(2, str);
        query.setParameter(3, billingDate);
        // String sql = "select * from billingservice where service_code like '"+str+"' or description like '%"+str+"%' ";


        List<BillingService> list = query.getResultList();
        return list;
    }

    public BillingService searchBillingCode(String str, String region) {
        return searchBillingCode(str, region, new Date());
    }

    public BillingService searchBillingCode(String str, String region, Date billingDate) {
        Query query = entityManager.createQuery("select bs from BillingService bs where bs.region = (?1) and bs.serviceCode like (?2) and bs.billingserviceDate = (select max(b2.billingserviceDate) from BillingService b2 where b2.serviceCode = bs.serviceCode and b2.billingserviceDate <= (?3))");

        query.setParameter(1, region);
        query.setParameter(2, str + "%");
        query.setParameter(3, billingDate);


        List<BillingService> list = query.getResultList();
        if (list == null || list.size() < 1) {
            return null;
        }

        return list.get(list.size() - 1);
    }

    public BillingService searchPrivateBillingCode(String privateCode, Date billingDate) {

        Query query = entityManager.createQuery("select bs from BillingService bs where bs.region is null and bs.serviceCode = ?1 and bs.billingserviceDate = (select max(b2.billingserviceDate) from BillingService b2 where b2.serviceCode = bs.serviceCode and b2.billingserviceDate <= (?2))");

        query.setParameter(1, privateCode);
        query.setParameter(2, billingDate);

        return getSingleResultOrNull(query);
    }

    public boolean editBillingCodeDesc(String desc, String val, Integer codeId) {
        boolean retval = true;
        BillingService billingservice = find(codeId);
        billingservice.setValue(val);
        billingservice.setDescription(desc);
        merge(billingservice);
        return retval;
    }

    public boolean editBillingCode(String val, Integer codeId) {
        boolean retval = true;
        BillingService billingservice = find(codeId);
        billingservice.setValue(val);
        merge(billingservice);
        return retval;
    }

    public boolean insertBillingCode(String code, String date, String description, String termDate, String region) {
        boolean retval = true;
        BillingService bs = new BillingService();
        bs.setServiceCode(code);
        bs.setDescription(description);
        bs.setTerminationDate(UtilDateUtilities.StringToDate(termDate));
        bs.setBillingserviceDate(UtilDateUtilities.StringToDate(date));
        bs.setRegion(region);
        bs.setGstFlag(false);
        bs.setSliFlag(false);
        entityManager.persist(bs);
        return retval;
    }

    public Date getLatestServiceDate(Date endDate, String serviceCode) {
        String sql = "select max(bs.billingserviceDate) from BillingService bs where bs.billingserviceDate <= ?1 and bs.serviceCode = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, endDate);
        query.setParameter(2, serviceCode);
        Date date = (Date) query.getSingleResult();
        return date;
    }

    public Object[] getUnitPrice(String bcode, Date date) {
        String sql = "select bs from BillingService bs where bs.serviceCode = ?1 and bs.billingserviceDate = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bcode);
        query.setParameter(2, getLatestServiceDate(date, bcode));


        List<BillingService> results = query.getResultList();

        if (results.size() > 0) {
            BillingService bs = results.get(0);
            return new Object[]{bs.getValue(), bs.getGstFlag()};
        } else
            return null;
    }

    public String getUnitPercentage(String bcode, Date date) {
        String sql = "select bs from BillingService bs where bs.serviceCode = ?1 and bs.billingserviceDate = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, bcode);
        query.setParameter(2, getLatestServiceDate(date, bcode));


        List<BillingService> results = query.getResultList();

        if (results.size() > 0) {
            return results.get(0).getPercentage();
        } else {
            return null;
        }
    }


    public List<BillingService> findBillingCodesByFontStyle(Integer styleId) {
        String sql = "select bs from BillingService bs where bs.displayStyle = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, styleId);

        return query.getResultList();
    }


    public List<BillingService> findByRegionGroupAndType(String billRegion, String serviceGroup, String serviceType) {
        Query query = entityManager.createQuery("SELECT b FROM BillingService b, CtlBillingService c" +
                " WHERE b.serviceCode= c.serviceCode and b.region= ?1 and c.serviceGroup= ?2" +
                " AND c.serviceType = ?3 order by c.serviceOrder");
        query.setParameter(1, billRegion);
        query.setParameter(2, serviceGroup);
        query.setParameter(3, serviceType);
        return query.getResultList();
    }

    public List<BillingService> findByServiceCodeOrDescription(String serviceCode) {
        Query query = createQuery("bs", "bs.serviceCode like ?1 or bs.description like ?2");
        query.setParameter(1, serviceCode);
        query.setParameter(2, "%" + serviceCode + "%");
        return query.getResultList();
    }

    @NativeSql("billingservice")
    public List<BillingService> findMostRecentByServiceCode(String serviceCode) {
        Query query = entityManager.createNativeQuery("select * from billingservice b where b.service_code like ?1 and b.billingservice_date = " +
                "(select max(b2.billingservice_date) from billingservice b2 where b2.service_code = b.service_code and b2.billingservice_date <= now())", BillingService.class);
        query.setParameter(1, serviceCode + "%");
        return query.getResultList();
    }

    public List<BillingService> findAll() {
        Query query = entityManager.createQuery("FROM BillingService bs ORDER BY TRIM(bs.description)");
        return query.getResultList();
    }


    public List<Object[]> findSomethingByBillingId(Integer billingNo) {
        String sql = "FROM BillingService bs, Wcb w, Billing b " +
                "WHERE w.billingNo = b.id " +
                "AND w.billingNo = ?1 " +
                "AND w.status = 'O' " +
                "AND b.status IN ('O', 'W') " +
                "AND bs.serviceCode = w.feeItem";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, billingNo);
        return query.getResultList();

    }

    public List<BillingService> findGst(String code, Date date) {
        String sql = "FROM BillingService b " +
                "WHERE b.serviceCode = ?1 " +
                "AND b.billingserviceDate = " +
                "(" +
                "SELECT MAX(b2.billingserviceDate) FROM BillingService b2 " +
                "	WHERE b2.serviceCode = ?1 " +
                "	AND b2.billingserviceDate <= ?2" +
                ")";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, code);
        query.setParameter(2, date);
        return query.getResultList();
    }

    public List<BillingService> search_service_code(String code, String code1, String code2, String desc, String desc1, String desc2) {
        String sql = "select b from BillingService b where (b.serviceCode like ?1 or b.serviceCode like ?2 or b.serviceCode like ?3 or b.description like ?4 or b.description like ?5 or b.description like ?6) and b.id = (select max(b2.id) from BillingService b2 where b2.serviceCode = b.serviceCode)";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, code);
        query.setParameter(2, code1);
        query.setParameter(3, code2);
        query.setParameter(4, desc);
        query.setParameter(5, desc1);
        query.setParameter(6, desc2);

        return query.getResultList();
    }

    public List<BillingService> findByServiceCodeAndLatestDate(String serviceCode, Date date) {
        String sql =
                "FROM BillingService bs WHERE bs.serviceCode = ?1" +
                        "AND bs.billingserviceDate = (" +
                        "	SELECT MAX(bss.billingserviceDate) FROM BillingService bss " +
                        "	WHERE bss.billingserviceDate <= ?2" +
                        "	AND bss.serviceCode = ?3" +
                        ")";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceCode);
        query.setParameter(2, date);
        query.setParameter(3, serviceCode);
        return query.getResultList();
    }

    public List<Object[]> findBillingServiceAndCtlBillingServiceByMagic(String serviceType, String serviceGroup, Date billReferenceDate) {
        String sql = "FROM BillingService b, CtlBillingService c " +
                "WHERE c.serviceCode = b.serviceCode " +
                "AND c.status='A' " +
                "AND c.serviceType = ?1" +
                "AND c.serviceGroup = ?2" +
                "AND b.billingserviceDate in (" +
                "	SELECT MAX(b2.billingserviceDate) FROM BillingService b2 " +
                "	WHERE b2.billingserviceDate <= ?3 " +
                "	AND b2.serviceCode = b.serviceCode " +
                ") " +
                "AND b.billingserviceNo = (" +
                "	SELECT MAX(b3.billingserviceNo) FROM BillingService b3 " +
                "	WHERE b3.billingserviceDate = b.billingserviceDate " +
                "	AND b3.serviceCode = b.serviceCode" +
                ") ORDER BY c.serviceOrder";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceType);
        query.setParameter(2, serviceGroup);
        query.setParameter(3, billReferenceDate);
        return query.getResultList();
    }

    public List<Object> findBillingCodesByCodeAndTerminationDate(String serviceCode, Date terminationDate) {
        String sql = "SELECT DISTINCT(bs.serviceCode) FROM BillingService bs WHERE bs.serviceCode = ?1 AND bs.terminationDate > ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceCode);
        query.setParameter(2, terminationDate);
        return query.getResultList();
    }

    public String getCodeDescription(String val, String billReferalDate) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        String sql = "select x from BillingService x where x.serviceCode=?1 and x.billingserviceDate = (select max(y.billingserviceDate) from BillingService y where y.billingserviceDate <=?2 and y.serviceCode =?3)";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, val);
        try {
            query.setParameter(2, df.parse(billReferalDate));
        } catch (ParseException e1) {
            return null;
        }
        query.setParameter(3, val);
        String retval = null;

        List<BillingService> billingServices = query.getResultList();
        if (billingServices.size() > 0) {
            BillingService b = billingServices.get(0);
            retval = b.getDescription();

            Date serviceDate = null;
            try {
                serviceDate = df.parse(billReferalDate);
            } catch (ParseException e) {
                return null;
            }
            Date tDate = b.getTerminationDate();
            if (tDate.before(serviceDate)) {
                retval = "defunct";
            }
        }

        return retval;
    }
}
