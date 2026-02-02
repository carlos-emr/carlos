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
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.BillingONEAReport;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingProviderData;

public interface BillingONEAReportDao extends AbstractDao<BillingONEAReport> {
    List<BillingONEAReport> findByProviderOhipNoAndGroupNoAndSpecialtyAndProcessDate(String providerOhipNo, String groupNo, String specialty, Date processDate);

    List<BillingONEAReport> findByProviderOhipNoAndGroupNoAndSpecialtyAndProcessDateAndBillingNo(String providerOhipNo, String groupNo, String specialty, Date processDate, Integer billingNo);

    List<BillingONEAReport> findByBillingNo(Integer billingNo);

    List<String> getBillingErrorList(Integer billingNo);

    List<BillingONEAReport> findByMagic(String ohipNo, String billingGroupNo, String specialtyCode, Date fromDate, Date toDate, String reportName);

    List<BillingONEAReport> findByMagic(List<BillingProviderData> list, Date fromDate, Date toDate, String reportName);
}
