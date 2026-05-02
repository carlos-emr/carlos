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

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.BillingONEAReport;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;

/**
 * DAO for Ontario explanatory-advice/error-report rows.
 *
 * <p>These queries are shaped around the ministry report dimensions used by
 * the legacy screens: provider, group, specialty, process date, and report
 * filename.</p>
 */
public interface BillingONEAReportDao extends AbstractDao<BillingONEAReport> {
    /** Find report rows for one provider/group/specialty/process-date combination. */
    List<BillingONEAReport> findByProviderOhipNoAndGroupNoAndSpecialtyAndProcessDate(String providerOhipNo, String groupNo, String specialty, Date processDate);

    /** Narrow the standard explanatory-advice lookup to a specific billing number. */
    List<BillingONEAReport> findByProviderOhipNoAndGroupNoAndSpecialtyAndProcessDateAndBillingNo(String providerOhipNo, String groupNo, String specialty, Date processDate, Integer billingNo);

    /** Load all explanatory-advice rows recorded against one billing number. */
    List<BillingONEAReport> findByBillingNo(Integer billingNo);

    /** Return only the billing error-code text values for one billing number. */
    List<String> getBillingErrorList(Integer billingNo);

    /** Legacy ad hoc search path driven by optional provider/group/date/report filters. */
    List<BillingONEAReport> findByMagic(String ohipNo, String billingGroupNo, String specialtyCode, Date fromDate, Date toDate, String reportName);

    /** Bulk provider variant of the legacy ad hoc explanatory-advice search. */
    List<BillingONEAReport> findByMagic(List<BillingProviderDto> list, Date fromDate, Date toDate, String reportName);
}
