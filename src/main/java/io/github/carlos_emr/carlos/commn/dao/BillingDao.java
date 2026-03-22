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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.utility.DateRange;

/**
 * DAO interface for healthcare billing operations.
 *
 * @since 2001
 */

public interface BillingDao extends AbstractDao<Billing> {
    /**
     * Find Active.
     *
     * @param billingNo int the billingNo
     * @return List<Billing>
     */
    List<Billing> findActive(int billingNo);

    /**
     * Find By Billing Type.
     *
     * @param type String the type
     * @return List<Billing>
     */
    List<Billing> findByBillingType(String type);

    /**
     * Find By Appointment No.
     *
     * @param apptNo int the apptNo
     * @return List<Billing>
     */
    List<Billing> findByAppointmentNo(int apptNo);

    /**
     * Find Set.
     *
     * @param list List<String> the list
     * @return List<Billing>
     */
    List<Billing> findSet(List<String> list);

    /**
     * Find Billings.
     *
     * @param demoNo Integer the demoNo
     * @param serviceCodes List<String> the serviceCodes
     * @return List<Object[]>
     */
    List<Object[]> findBillings(Integer demoNo, List<String> serviceCodes);

    /**
     * Find Billings.
     *
     * @param demoNo Integer the demoNo
     * @param statusType String the statusType
     * @param providerNo String the providerNo
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return List<Billing>
     */
    List<Billing> findBillings(Integer demoNo, String statusType, String providerNo, Date startDate, Date endDate);

    /**
     * Find Billings.
     *
     * @param billing_no Integer the billing_no
     * @return List<Object[]>
     */
    List<Object[]> findBillings(Integer billing_no);

    /**
     * Find Provider Billings With Gst.
     *
     * @param providerNos String[] the providerNos
     * @param dateRange DateRange the dateRange
     * @return List<Object[]>
     */
    List<Object[]> findProviderBillingsWithGst(String[] providerNos, DateRange dateRange);

    /**
     * Find By Provider Status And Dates.
     *
     * @param providerNo String the providerNo
     * @param statusList List<String> the statusList
     * @param dateRange DateRange the dateRange
     * @return List<Billing>
     */
    List<Billing> findByProviderStatusAndDates(String providerNo, List<String> statusList, DateRange dateRange);

    /**
     * Get My Magic Billings.
     * @return List<Billing>
     */
    List<Billing> getMyMagicBillings();

    /**
     * Find By Many Things.
     *
     * @param statusType String the statusType
     * @param providerNo String the providerNo
     * @param startDate String the startDate
     * @param endDate String the endDate
     * @param demoNo String the demoNo
     * @param excludeWCB boolean the excludeWCB
     * @param excludeMSP boolean the excludeMSP
     * @param excludePrivate boolean the excludePrivate
     * @param exludeICBC boolean the exludeICBC
     * @return List<Object[]>
     */
    List<Object[]> findByManyThings(String statusType, String providerNo, String startDate, String endDate, String demoNo, boolean excludeWCB, boolean excludeMSP, boolean excludePrivate, boolean exludeICBC);

    /**
     * Find Billings By Status.
     *
     * @param statusType String the statusType
     * @return List<Object[]>
     */
    List<Object[]> findBillingsByStatus(String statusType);

    /**
     * Find Outstanding Bills.
     *
     * @param demographicNo Integer the demographicNo
     * @param billingType String the billingType
     * @param statuses List<String> the statuses
     * @return List<Object[]>
     */
    List<Object[]> findOutstandingBills(Integer demographicNo, String billingType, List<String> statuses);

    /**
     * Find By Billing Master No.
     *
     * @param billingmasterNo Integer the billingmasterNo
     * @return List<Object[]>
     */
    List<Object[]> findByBillingMasterNo(Integer billingmasterNo);

    /**
     * Find Billings By Many Things.
     *
     * @param billing Integer the billing
     * @param billingDate Date the billingDate
     * @param ohipNo String the ohipNo
     * @param serviceCode String the serviceCode
     * @return List<Object[]>
     */
    List<Object[]> findBillingsByManyThings(Integer billing, Date billingDate, String ohipNo, String serviceCode);

    /**
     * Count Billings.
     *
     * @param diagCode String the diagCode
     * @param creator String the creator
     * @param sdate Date the sdate
     * @param edate Date the edate
     * @return Integer
     */
    Integer countBillings(String diagCode, String creator, Date sdate, Date edate);

    /**
     * Count Billing Visits By Creator.
     *
     * @param providerNo String the providerNo
     * @param dateBegin Date the dateBegin
     * @param dateEnd Date the dateEnd
     * @return List<Object[]>
     */
    List<Object[]> countBillingVisitsByCreator(String providerNo, Date dateBegin, Date dateEnd);

    /**
     * Count Billing Visits By Provider.
     *
     * @param providerNo String the providerNo
     * @param dateBegin Date the dateBegin
     * @param dateEnd Date the dateEnd
     * @return List<Object[]>
     */
    List<Object[]> countBillingVisitsByProvider(String providerNo, Date dateBegin, Date dateEnd);

    /**
     * Search_billing_no_by_appt.
     *
     * @param demographicNo int the demographicNo
     * @param appointmentNo int the appointmentNo
     * @return Integer
     */
    Integer search_billing_no_by_appt(int demographicNo, int appointmentNo);

    /**
     * Search_billing_no.
     *
     * @param demographicNo int the demographicNo
     * @return Integer
     */
    Integer search_billing_no(int demographicNo);

    /**
     * Search_billob.
     *
     * @param providerNo String the providerNo
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return List<Object[]>
     */
    List<Object[]> search_billob(String providerNo, Date startDate, Date endDate);

    /**
     * Search_billflu.
     *
     * @param creator String the creator
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return List<Object[]>
     */
    List<Object[]> search_billflu(String creator, Date startDate, Date endDate);

    /**
     * Search_unsettled_history_daterange.
     *
     * @param providerNo String the providerNo
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return List<Billing>
     */
    List<Billing> search_unsettled_history_daterange(String providerNo, Date startDate, Date endDate);

    /**
     * Find Active Billings By Demo No.
     *
     * @param demoNo Integer the demoNo
     * @param limit int the limit
     * @return List<Billing>
     */
    List<Billing> findActiveBillingsByDemoNo(Integer demoNo, int limit);

    /**
     * Find Billings By Demo No Service Code And Date.
     *
     * @param demoNo Integer the demoNo
     * @param date Date the date
     * @param serviceCodes List<String> the serviceCodes
     * @return List<Billing>
     */
    List<Billing> findBillingsByDemoNoServiceCodeAndDate(Integer demoNo, Date date, List<String> serviceCodes);

    /**
     * Search_bill_history_daterange.
     *
     * @param providerNo String the providerNo
     * @param startBillingDate Date the startBillingDate
     * @param endBillingDate Date the endBillingDate
     * @return List<Billing>
     */
    List<Billing> search_bill_history_daterange(String providerNo, Date startBillingDate, Date endBillingDate);

    /**
     * Find By Provider Status For Teleplan File Writer.
     *
     * @param hin String the hin
     * @return List<Billing>
     */
    List<Billing> findByProviderStatusForTeleplanFileWriter(String hin);

    /**
     * Search_bill_generic.
     *
     * @param billingNo int the billingNo
     * @return List<Object[]>
     */
    List<Object[]> search_bill_generic(int billingNo);
}
