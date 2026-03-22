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

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.BillingONEAReport;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingProviderData;

/**
 * DAO interface for healthcare billing operations.
 *
 * @since 2001
 */

public interface BillingONEAReportDao extends AbstractDao<BillingONEAReport> {
    /**
     * Find By Provider Ohip No And Group No And Specialty And Process Date.
     *
     * @param providerOhipNo String the providerOhipNo
     * @param groupNo String the groupNo
     * @param specialty String the specialty
     * @param processDate Date the processDate
     * @return List<BillingONEAReport>
     */
    List<BillingONEAReport> findByProviderOhipNoAndGroupNoAndSpecialtyAndProcessDate(String providerOhipNo, String groupNo, String specialty, Date processDate);

    /**
     * Find By Provider Ohip No And Group No And Specialty And Process Date And Billing No.
     *
     * @param providerOhipNo String the providerOhipNo
     * @param groupNo String the groupNo
     * @param specialty String the specialty
     * @param processDate Date the processDate
     * @param billingNo Integer the billingNo
     * @return List<BillingONEAReport>
     */
    List<BillingONEAReport> findByProviderOhipNoAndGroupNoAndSpecialtyAndProcessDateAndBillingNo(String providerOhipNo, String groupNo, String specialty, Date processDate, Integer billingNo);

    /**
     * Find By Billing No.
     *
     * @param billingNo Integer the billingNo
     * @return List<BillingONEAReport>
     */
    List<BillingONEAReport> findByBillingNo(Integer billingNo);

    /**
     * Get Billing Error List.
     *
     * @param billingNo Integer the billingNo
     * @return List<String>
     */
    List<String> getBillingErrorList(Integer billingNo);

    /**
     * Find By Magic.
     *
     * @param ohipNo String the ohipNo
     * @param billingGroupNo String the billingGroupNo
     * @param specialtyCode String the specialtyCode
     * @param fromDate Date the fromDate
     * @param toDate Date the toDate
     * @param reportName String the reportName
     * @return List<BillingONEAReport>
     */
    List<BillingONEAReport> findByMagic(String ohipNo, String billingGroupNo, String specialtyCode, Date fromDate, Date toDate, String reportName);

    /**
     * Find By Magic.
     *
     * @param list List<BillingProviderData> the list
     * @param fromDate Date the fromDate
     * @param toDate Date the toDate
     * @param reportName String the reportName
     * @return List<BillingONEAReport>
     */
    List<BillingONEAReport> findByMagic(List<BillingProviderData> list, Date fromDate, Date toDate, String reportName);
}
