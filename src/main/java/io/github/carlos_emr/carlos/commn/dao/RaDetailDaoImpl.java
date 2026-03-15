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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.util.DateUtils;

@Repository
@SuppressWarnings("unchecked")
public class RaDetailDaoImpl extends AbstractDaoImpl<RaDetail> implements RaDetailDao {

    //These error codes are used when an RA is "settled" in the sense that invoices submitted to OHIP that are "fully done" are marked "settled".
    //These error codes are considered irrelevant in the sense that while an error code is being returned, these specific errors
    //can be safely ignored, and do not require further review.
    //
    //This string should be in a format appropriate for an SQL statement X in irrelevantErrorCodes (se usage)
    //EV = "Check health card for current version code", which is essentially when the pt's ohip card is about to expire
    //55 = "Deduction is an adjustment on an earlier account"
    //57 = "This payment is an adjustment on an earlier account"
    //HM = "Invalid master number used on date of service"
    //30 = Service is not a benefit of OHIP
    //B2 = Paid in accordance with the OHIP Schedule of Benefits for Telephone Virtual Care Services
    //I6 = Premium not applicable
    //V8 = This service paid at lower fee as per stated OHIP policy
    //In addition to this list, the functions that use this string may have additional situations hardcoded; search for use of this string directly
    private static String irrelevantErrorCodes = "('EV','55','57','HM','30','B2','I6','V8')";

    public RaDetailDaoImpl() {
        super(RaDetail.class);
    }

    @Override
    public List<RaDetail> findByBillingNo(Integer billingNo) {
        Query query = entityManager.createQuery("SELECT rad from RaDetail rad WHERE rad.billingNo = ?1 order by rad.raHeaderNo desc, rad.id ");

        query.setParameter(1, billingNo);


        List<RaDetail> results = query.getResultList();

        return results;

    }

    @Override
    public List<RaDetail> findByRaHeaderNo(Integer raHeaderNo) {
        Query query = entityManager.createQuery("SELECT rad from RaDetail rad WHERE rad.raHeaderNo = ?1");

        query.setParameter(1, raHeaderNo);


        List<RaDetail> results = query.getResultList();

        return results;

    }

    @Override
    public List<Integer> findUniqueBillingNoByRaHeaderNoAndProviderAndNotErrorCode(Integer raHeaderNo, String providerOhipNo, String codes) {
        Query query = entityManager.createQuery("SELECT distinct(rad.billingNo) from RaDetail rad WHERE rad.raHeaderNo = ?1 and rad.providerOhipNo = ?2 and rad.errorCode not in ?3");

        String[] cList = codes.split(",");
        List<String> tmp = new ArrayList<String>();
        for (int x = 0; x < cList.length; x++) {
            tmp.add(cList[x]);
        }
        query.setParameter(1, raHeaderNo);
        query.setParameter(2, providerOhipNo);
        query.setParameter(3, tmp);

        List<Integer> results = query.getResultList();

        return results;

    }

    @Override
    public List<RaDetail> getRaDetailByDate(Date startDate, Date endDate, Locale locale) {
        Query query = entityManager.createQuery("SELECT rad from RaHeader rah, RaDetail rad WHERE rah.paymentDate >= ?1 and rah.paymentDate < ?2 and rah.id = rad.raHeaderNo order by rad.raHeaderNo, rad.billingNo, rad.serviceCode");
        String startDateStr = DateUtils.format("yyyyMMdd", startDate, locale);
        query.setParameter(1, startDateStr);
        String endDateStr = DateUtils.format("yyyyMMdd", endDate, locale);
        query.setParameter(2, endDateStr);


        List<RaDetail> results = query.getResultList();

        return results;
    }

    @Override
    public List<RaDetail> getRaDetailByDate(Provider p, Date startDate, Date endDate, Locale locale) {
        Query query = entityManager.createQuery("SELECT rad from RaHeader rah, RaDetail rad WHERE rah.paymentDate >= ?1 and rah.paymentDate < ?2 and rah.id = rad.raHeaderNo and rad.providerOhipNo = ?3 order by rad.raHeaderNo, rad.billingNo, rad.serviceCode");
        String startDateStr = DateUtils.format("yyyyMMdd", startDate, locale);
        query.setParameter(1, startDateStr);
        String endDateStr = DateUtils.format("yyyyMMdd", endDate, locale);
        query.setParameter(2, endDateStr);
        query.setParameter(3, p.getOhipNo());


        List<RaDetail> results = query.getResultList();

        return results;
    }

    @Override
    public List<RaDetail> getRaDetailByClaimNo(String claimNo) {

        Query query = entityManager.createQuery("SELECT rad from RaDetail rad where rad.claimNo = ?1");
        query.setParameter(1, claimNo);


        List<RaDetail> raDetails = query.getResultList();

        return raDetails;
    }

    @Override
    public List<RaDetail> search_raerror35(Integer raHeaderNo, String error1, String error2, String providerOhipNo) {
        Query query = entityManager.createQuery("SELECT rad from RaDetail rad WHERE rad.raHeaderNo = ?1 and rad.errorCode<>'' and rad.errorCode<>?2 and rad.errorCode<>?3 and not rad.errorCode in ?4 and (rad.serviceCode<>'Q200A' or rad.errorCode<>'I9') and rad.providerOhipNo like ?5");

        query.setParameter(1, raHeaderNo);
        query.setParameter(2, error1);
        query.setParameter(3, error2);
        query.setParameter(4, irrelevantErrorCodes);
        query.setParameter(5, providerOhipNo);


        List<RaDetail> results = query.getResultList();

        return results;

    }

    @Override
    public List<Integer> search_ranoerror35(Integer raHeaderNo, String error1, String error2, String providerOhipNo) {
        Query query = entityManager.createQuery("select distinct rad.billingNo from RaDetail rad where rad.raHeaderNo=?1 and (rad.errorCode='' or rad.errorCode=?2 or rad.errorCode=?3 or rad.errorCode in ?4 or (rad.serviceCode='Q200A' and rad.errorCode='I9')) and rad.providerOhipNo like ?5");

        query.setParameter(1, raHeaderNo);
        query.setParameter(2, error1);
        query.setParameter(3, error2);
        query.setParameter(4, irrelevantErrorCodes);
        query.setParameter(5, providerOhipNo);


        List<Integer> results = query.getResultList();

        return results;
    }

    @Override
    public List<Integer> search_raob(Integer raHeaderNo) {
        String[] arServiceCodes = {"P006A", "P020A", "P022A", "P028A", "P023A", "P007A", "P009A", "P011A", "P008B", "P018B", "E502A", "C989A", "E409A", "E410A", "E411A", "H001A"};

        Query query = entityManager.createQuery("select distinct rad.billingNo from RaDetail rad where rad.raHeaderNo=?1 and rad.serviceCode in ?2");

        query.setParameter(1, raHeaderNo);
        query.setParameter(2, Arrays.asList(arServiceCodes));


        List<Integer> results = query.getResultList();

        return results;
    }

    @Override
    public List<Integer> search_racolposcopy(Integer raHeaderNo) {
        String[] arServiceCodes = {"A004A", "A005A", "Z731A", "Z666A", "Z730A", "Z720A"};

        Query query = entityManager.createQuery("select distinct rad.billingNo from RaDetail rad where rad.raHeaderNo=?1 and rad.serviceCode in ?2");

        query.setParameter(1, raHeaderNo);
        query.setParameter(2, Arrays.asList(arServiceCodes));


        List<Integer> results = query.getResultList();

        return results;
    }

    @Override
    public List<Object[]> search_raprovider(Integer raHeaderNo) {
        Query query = entityManager.createQuery("from RaDetail r, Provider p where p.OhipNo=r.providerOhipNo and r.raHeaderNo=?1 group by r.providerOhipNo");

        query.setParameter(1, raHeaderNo);


        List<Object[]> results = query.getResultList();

        return results;
    }

    @Override
    public List<RaDetail> search_rasummary_dt(Integer raHeaderNo, String providerOhipNo) {
        Query query = entityManager.createQuery("select rad from RaDetail rad where rad.raHeaderNo=?1 and rad.providerOhipNo like ?2");

        query.setParameter(1, raHeaderNo);
        query.setParameter(2, providerOhipNo);


        List<RaDetail> results = query.getResultList();

        return results;
    }

    @Override
    public List<Integer> search_ranoerrorQ(Integer raHeaderNo, String providerOhipNo) {
        String[] arServiceCodes = {"Q011A", "Q020A", "Q130A", "Q131A", "Q132A", "Q133A", "Q140A", "Q141A", "Q142A"};

        Query query = entityManager.createQuery("select distinct rad.billingNo from RaDetail rad where rad.raHeaderNo=?1 and rad.serviceCode in ?2 and rad.errorCode='30' and rad.providerOhipNo like ?3");

        query.setParameter(1, raHeaderNo);
        query.setParameter(2, Arrays.asList(arServiceCodes));
        query.setParameter(3, providerOhipNo);


        List<Integer> results = query.getResultList();

        return results;
    }

    @Override
    public List<String> getBillingExplanatoryList(Integer billingNo) {

        Query query = entityManager.createQuery("SELECT errorCode from RaDetail rad where rad.billingNo = ?1 and rad.errorCode!='' and rad.raHeaderNo=(select max(rad2.raHeaderNo) from RaDetail rad2 where rad2.billingNo=?1)");
        query.setParameter(1, billingNo);


        List<String> errors = query.getResultList();

        return errors;
    }

    @Override
    public List<RaDetail> findByBillingNoServiceDateAndProviderNo(Integer billingNo, String serviceDate, String providerNo) {
        Query query = createQuery("r", "r.billingNo = ?1 AND r.serviceDate = ?2 and r.providerOhipNo = ?3");
        query.setParameter(1, billingNo);
        query.setParameter(2, serviceDate);
        query.setParameter(3, providerNo);
        return query.getResultList();

    }

    @Override
    public List<RaDetail> findByBillingNoAndErrorCode(Integer billingNo, String errorCode) {
        Query query = createQuery("r", "r.billingNo = ?1 AND r.errorCode = ?2");
        query.setParameter(1, billingNo);
        query.setParameter(2, errorCode);
        return query.getResultList();
    }

    @Override
    public List<Integer> findDistinctIdOhipWithError(Integer raHeaderNo, String providerOhipNo, List<String> codes) {
        Query query = createQuery("select distinct r.billingNo", "r", "r.raHeaderNo = ?1 " +
                "AND r.providerOhipNo = ?2 " +
                "AND r.errorCode <> '' " +
                "AND r.errorCode NOT IN ?3");

        query.setParameter(1, raHeaderNo);
        query.setParameter(2, providerOhipNo);
        query.setParameter(3, codes);
        return query.getResultList();

    }

    @Override
    public List<RaDetail> findByHeaderAndBillingNos(Integer raHeaderNo, Integer billingNo) {
        Query query = createQuery("r", "r.raHeaderNo = ?1 AND r.billingNo = ?2");
        query.setParameter(1, raHeaderNo);
        query.setParameter(2, billingNo);
        return query.getResultList();
    }

    @Override
    public List<RaDetail> findByRaHeaderNoAndServiceCodes(Integer raHeaderNo, List<String> serviceCodes) {
        Query query = createQuery("r", "r.raHeaderNo = ?1 AND r.serviceCode in ?2");
        query.setParameter(1, raHeaderNo);
        query.setParameter(2, serviceCodes);
        return query.getResultList();
    }

    @Override
    public List<RaDetail> findByRaHeaderNoAndProviderOhipNo(Integer raHeaderNo, String providerOhipNo) {
        Query query = createQuery("r", "r.raHeaderNo = ?1 AND r.providerOhipNo = ?2");
        query.setParameter(1, raHeaderNo);
        query.setParameter(2, providerOhipNo);
        return query.getResultList();
    }
}
 