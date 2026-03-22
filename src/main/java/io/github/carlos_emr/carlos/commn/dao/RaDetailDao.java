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

/**
 * DAO interface for remittance advice operations.
 *
 * @since 2001
 */

public interface RaDetailDao extends AbstractDao<RaDetail> {
    /**
     * Find By Billing No.
     *
     * @param billingNo Integer the billingNo
     * @return List<RaDetail>
     */
    List<RaDetail> findByBillingNo(Integer billingNo);

    /**
     * Find By Ra Header No.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @return List<RaDetail>
     */
    List<RaDetail> findByRaHeaderNo(Integer raHeaderNo);

    /**
     * Find Unique Billing No By Ra Header No And Provider And Not Error Code.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param providerOhipNo String the providerOhipNo
     * @param codes String the codes
     * @return List<Integer>
     */
    List<Integer> findUniqueBillingNoByRaHeaderNoAndProviderAndNotErrorCode(Integer raHeaderNo, String providerOhipNo, String codes);

    /**
     * Get Ra Detail By Date.
     *
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @param locale Locale the locale
     * @return List<RaDetail>
     */
    List<RaDetail> getRaDetailByDate(Date startDate, Date endDate, Locale locale);

    /**
     * Get Ra Detail By Date.
     *
     * @param p Provider the p
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @param locale Locale the locale
     * @return List<RaDetail>
     */
    List<RaDetail> getRaDetailByDate(Provider p, Date startDate, Date endDate, Locale locale);

    /**
     * Get Ra Detail By Claim No.
     *
     * @param claimNo String the claimNo
     * @return List<RaDetail>
     */
    List<RaDetail> getRaDetailByClaimNo(String claimNo);

    /**
     * Search_raerror35.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param error1 String the error1
     * @param error2 String the error2
     * @param providerOhipNo String the providerOhipNo
     * @return List<RaDetail>
     */
    List<RaDetail> search_raerror35(Integer raHeaderNo, String error1, String error2, String providerOhipNo);

    /**
     * Search_ranoerror35.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param error1 String the error1
     * @param error2 String the error2
     * @param providerOhipNo String the providerOhipNo
     * @return List<Integer>
     */
    List<Integer> search_ranoerror35(Integer raHeaderNo, String error1, String error2, String providerOhipNo);

    /**
     * Search_raob.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @return List<Integer>
     */
    List<Integer> search_raob(Integer raHeaderNo);

    /**
     * Search_racolposcopy.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @return List<Integer>
     */
    List<Integer> search_racolposcopy(Integer raHeaderNo);

    /**
     * Search_raprovider.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @return List<Object[]>
     */
    List<Object[]> search_raprovider(Integer raHeaderNo);

    /**
     * Search_rasummary_dt.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param providerOhipNo String the providerOhipNo
     * @return List<RaDetail>
     */
    List<RaDetail> search_rasummary_dt(Integer raHeaderNo, String providerOhipNo);

    /**
     * Search_ranoerror Q.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param providerOhipNo String the providerOhipNo
     * @return List<Integer>
     */
    List<Integer> search_ranoerrorQ(Integer raHeaderNo, String providerOhipNo);

    /**
     * Get Billing Explanatory List.
     *
     * @param billingNo Integer the billingNo
     * @return List<String>
     */
    List<String> getBillingExplanatoryList(Integer billingNo);

    /**
     * Find By Billing No Service Date And Provider No.
     *
     * @param billingNo Integer the billingNo
     * @param serviceDate String the serviceDate
     * @param providerNo String the providerNo
     * @return List<RaDetail>
     */
    List<RaDetail> findByBillingNoServiceDateAndProviderNo(Integer billingNo, String serviceDate, String providerNo);

    /**
     * Find By Billing No And Error Code.
     *
     * @param billingNo Integer the billingNo
     * @param errorCode String the errorCode
     * @return List<RaDetail>
     */
    List<RaDetail> findByBillingNoAndErrorCode(Integer billingNo, String errorCode);

    /**
     * Find Distinct Id Ohip With Error.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param providerOhipNo String the providerOhipNo
     * @param codes List<String> the codes
     * @return List<Integer>
     */
    List<Integer> findDistinctIdOhipWithError(Integer raHeaderNo, String providerOhipNo, List<String> codes);

    /**
     * Find By Header And Billing Nos.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param billingNo Integer the billingNo
     * @return List<RaDetail>
     */
    List<RaDetail> findByHeaderAndBillingNos(Integer raHeaderNo, Integer billingNo);

    /**
     * Find By Ra Header No And Service Codes.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param serviceCodes List<String> the serviceCodes
     * @return List<RaDetail>
     */
    List<RaDetail> findByRaHeaderNoAndServiceCodes(Integer raHeaderNo, List<String> serviceCodes);

    /**
     * Find By Ra Header No And Provider Ohip No.
     *
     * @param raHeaderNo Integer the raHeaderNo
     * @param providerOhipNo String the providerOhipNo
     * @return List<RaDetail>
     */
    List<RaDetail> findByRaHeaderNoAndProviderOhipNo(Integer raHeaderNo, String providerOhipNo);
}
