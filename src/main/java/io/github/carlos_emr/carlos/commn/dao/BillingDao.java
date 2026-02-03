/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.utility.DateRange;

public interface BillingDao extends AbstractDao<Billing> {
    List<Billing> findActive(int billingNo);

    List<Billing> findByBillingType(String type);

    List<Billing> findByAppointmentNo(int apptNo);

    List<Billing> findSet(List<String> list);

    List<Object[]> findBillings(Integer demoNo, List<String> serviceCodes);

    List<Billing> findBillings(Integer demoNo, String statusType, String providerNo, Date startDate, Date endDate);

    List<Object[]> findBillings(Integer billing_no);

    List<Object[]> findProviderBillingsWithGst(String[] providerNos, DateRange dateRange);

    List<Billing> findByProviderStatusAndDates(String providerNo, List<String> statusList, DateRange dateRange);

    List<Billing> getMyMagicBillings();

    List<Object[]> findByManyThings(String statusType, String providerNo, String startDate, String endDate, String demoNo, boolean excludeWCB, boolean excludeMSP, boolean excludePrivate, boolean exludeICBC);

    List<Object[]> findBillingsByStatus(String statusType);

    List<Object[]> findOutstandingBills(Integer demographicNo, String billingType, List<String> statuses);

    List<Object[]> findByBillingMasterNo(Integer billingmasterNo);

    List<Object[]> findBillingsByManyThings(Integer billing, Date billingDate, String ohipNo, String serviceCode);

    Integer countBillings(String diagCode, String creator, Date sdate, Date edate);

    List<Object[]> countBillingVisitsByCreator(String providerNo, Date dateBegin, Date dateEnd);

    List<Object[]> countBillingVisitsByProvider(String providerNo, Date dateBegin, Date dateEnd);

    Integer search_billing_no_by_appt(int demographicNo, int appointmentNo);

    Integer search_billing_no(int demographicNo);

    List<Object[]> search_billob(String providerNo, Date startDate, Date endDate);

    List<Object[]> search_billflu(String creator, Date startDate, Date endDate);

    List<Billing> search_unsettled_history_daterange(String providerNo, Date startDate, Date endDate);

    List<Billing> findActiveBillingsByDemoNo(Integer demoNo, int limit);

    List<Billing> findBillingsByDemoNoServiceCodeAndDate(Integer demoNo, Date date, List<String> serviceCodes);

    List<Billing> search_bill_history_daterange(String providerNo, Date startBillingDate, Date endBillingDate);

    List<Billing> findByProviderStatusForTeleplanFileWriter(String hin);

    List<Object[]> search_bill_generic(int billingNo);
}
