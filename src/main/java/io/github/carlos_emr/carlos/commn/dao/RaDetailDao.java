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

import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaDetail;

public interface RaDetailDao extends AbstractDao<RaDetail> {
    List<RaDetail> findByBillingNo(Integer billingNo);

    List<RaDetail> findByRaHeaderNo(Integer raHeaderNo);

    List<Integer> findUniqueBillingNoByRaHeaderNoAndProviderAndNotErrorCode(Integer raHeaderNo, String providerOhipNo, String codes);

    List<RaDetail> getRaDetailByDate(Date startDate, Date endDate, Locale locale);

    List<RaDetail> getRaDetailByDate(Provider p, Date startDate, Date endDate, Locale locale);

    List<RaDetail> getRaDetailByClaimNo(String claimNo);

    List<RaDetail> search_raerror35(Integer raHeaderNo, String error1, String error2, String providerOhipNo);

    List<Integer> search_ranoerror35(Integer raHeaderNo, String error1, String error2, String providerOhipNo);

    List<Integer> search_raob(Integer raHeaderNo);

    List<Integer> search_racolposcopy(Integer raHeaderNo);

    List<Object[]> search_raprovider(Integer raHeaderNo);

    List<RaDetail> search_rasummary_dt(Integer raHeaderNo, String providerOhipNo);

    List<Integer> search_ranoerrorQ(Integer raHeaderNo, String providerOhipNo);

    List<String> getBillingExplanatoryList(Integer billingNo);

    List<RaDetail> findByBillingNoServiceDateAndProviderNo(Integer billingNo, String serviceDate, String providerNo);

    List<RaDetail> findByBillingNoAndErrorCode(Integer billingNo, String errorCode);

    List<Integer> findDistinctIdOhipWithError(Integer raHeaderNo, String providerOhipNo, List<String> codes);

    List<RaDetail> findByHeaderAndBillingNos(Integer raHeaderNo, Integer billingNo);

    List<RaDetail> findByRaHeaderNoAndServiceCodes(Integer raHeaderNo, List<String> serviceCodes);

    List<RaDetail> findByRaHeaderNoAndProviderOhipNo(Integer raHeaderNo, String providerOhipNo);
}
