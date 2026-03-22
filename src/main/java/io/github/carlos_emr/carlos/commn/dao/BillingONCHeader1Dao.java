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
import java.util.Locale;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.DateRange;

/**
 * @author Eugene Katyukhin
 */

public interface BillingONCHeader1Dao extends AbstractDao<BillingONCHeader1> {

    /**
     * Get Bill Cheader1 By Demographic No.
     *
     * @param demographic_no int the demographic_no
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> getBillCheader1ByDemographicNo(int demographic_no);

    public int getNumberOfDemographicsWithInvoicesForProvider(String providerNo, Date startDate, Date endDate,
                                                              boolean distinct);

    /**
     * Create Bills.
     *
     * @param lBills List<BillingONCHeader1> the lBills
     */
    public void createBills(List<BillingONCHeader1> lBills);

    public String createBill(String provider, Integer demographic, String code, String clinicRefCode, Date serviceDate,
                             String curUser);

    public String createBill(String provider, Integer demographic, String code, String dxCode, String clinicRefCode,
                             Date serviceDate, String curUser);

    public String createBills(String provider, List<String> demographic_nos, List<String> codes, List<String> dxcodes,
                              String clinicRefCode, Date serviceDate, String curUser);

    /**
     * Billed Between These Days.
     *
     * @param serviceCode String the serviceCode
     * @param demographicNo Integer the demographicNo
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return boolean
     */
    public boolean billedBetweenTheseDays(String serviceCode, Integer demographicNo, Date startDate, Date endDate);

    /**
     * Get Days Since Billed.
     *
     * @param serviceCode String the serviceCode
     * @param demographicNo Integer the demographicNo
     * @return int
     */
    public int getDaysSinceBilled(String serviceCode, Integer demographicNo);

    /**
     * Get Days Since Paid.
     *
     * @param serviceCode String the serviceCode
     * @param demographic_no Integer the demographic_no
     * @return int
     */
    public int getDaysSincePaid(String serviceCode, Integer demographic_no);

    /**
     * Get Invoices.
     *
     * @param demographicNo Integer the demographicNo
     * @param limit Integer the limit
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> getInvoices(Integer demographicNo, Integer limit);

    /**
     * Get Invoices.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> getInvoices(Integer demographicNo);

    /**
     * Get Invoices By Ids.
     *
     * @param ids List<Integer> the ids
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> getInvoicesByIds(List<Integer> ids);

    /**
     * Get Invoices Meta.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Map<String, Object>>
     */
    public List<Map<String, Object>> getInvoicesMeta(Integer demographicNo);

    // public GstControlDao getGstControlDao();

    // public void setGstControlDao(GstControlDao gstControlDao);

    /**
     * Find Billing O N Item By Service Code.
     *
     * @param ch1 BillingONCHeader1 the ch1
     * @param serviceCode String the serviceCode
     * @return BillingONItem
     */
    public BillingONItem findBillingONItemByServiceCode(BillingONCHeader1 ch1, String serviceCode);

    /**
     * Get3rd Party Invoice By Provider.
     *
     * @param p Provider the p
     * @param start Date the start
     * @param end Date the end
     * @param locale Locale the locale
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> get3rdPartyInvoiceByProvider(Provider p, Date start, Date end, Locale locale);

    /**
     * Get3rd Party Invoice By Date.
     *
     * @param start Date the start
     * @param end Date the end
     * @param locale Locale the locale
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> get3rdPartyInvoiceByDate(Date start, Date end, Locale locale);

    /**
     * Get Last O H I P Billing Date For Service Code.
     *
     * @param demographicNo Integer the demographicNo
     * @param serviceCode String the serviceCode
     * @return BillingONCHeader1
     */
    public BillingONCHeader1 getLastOHIPBillingDateForServiceCode(Integer demographicNo, String serviceCode);

    /**
     * Find By Appointment No.
     *
     * @param appointmentNo Integer the appointmentNo
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> findByAppointmentNo(Integer appointmentNo);

    /**
     * Count Billing Visits By Provider.
     *
     * @param providerNo String the providerNo
     * @param dateBegin Date the dateBegin
     * @param dateEnd Date the dateEnd
     * @return List<Object[]>
     */
    public List<Object[]> countBillingVisitsByProvider(String providerNo, Date dateBegin, Date dateEnd);

    /**
     * Count Billing Visits By Creator.
     *
     * @param providerNo String the providerNo
     * @param dateBegin Date the dateBegin
     * @param dateEnd Date the dateEnd
     * @return List<Object[]>
     */
    public List<Object[]> countBillingVisitsByCreator(String providerNo, Date dateBegin, Date dateEnd);

    /**
     * Count_larrykain_clinic.
     *
     * @param facilityNum String the facilityNum
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return List<Long>
     */
    public List<Long> count_larrykain_clinic(String facilityNum, Date startDate, Date endDate);

    public List<Long> count_larrykain_hospital(String facilityNum1, String facilityNum2, String facilityNum3,
                                               String facilityNum4, Date startDate, Date endDate);

    public List<Long> count_larrykain_other(String facilityNum1, String facilityNum2, String facilityNum3,
                                            String facilityNum4, String facilityNum5, Date startDate, Date endDate);

    public List<BillingONCHeader1> findBillingsByManyThings(String status, String providerNo, Date startDate,
                                                            Date endDate, Integer demoNo);

    public List<BillingONCHeader1> findByProviderStatusAndDateRange(String providerNo, List<String> statuses,
                                                                    DateRange dateRange);

    /**
     * Find Billings And Demographics By Id.
     *
     * @param id Integer the id
     * @return List<Object[]>
     */
    public List<Object[]> findBillingsAndDemographicsById(Integer id);

    public List<BillingONCHeader1> findByMagic(List<String> payPrograms, String statusType, String providerNo,
                                               Date startDate, Date endDate, Integer demoNo, String visitLocation, Date paymentStartDate,
                                               Date paymentEndDate);

    /**
     * Get Billing Item By Dx Code.
     *
     * @param demographicNo Integer the demographicNo
     * @param dxCode String the dxCode
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> getBillingItemByDxCode(Integer demographicNo, String dxCode);

    public List<Object[]> findByMagic2(List<String> payPrograms, String statusType, String providerNo, Date startDate,
                                       Date endDate, Integer demoNo, List<String> serviceCodes, String dx, String visitType, String visitLocation,
                                       Date paymentStartDate, Date paymentEndDate);

    public List<Object[]> findByMagic2(List<String> payPrograms, String statusType, String providerNo, Date startDate,
                                       Date endDate, Integer demoNo, List<String> serviceCodes, String dx, String visitType, String visitLocation,
                                       Date paymentStartDate, Date paymentEndDate, String claimNo);

    /**
     * Find By Demo No.
     *
     * @param demoNo Integer the demoNo
     * @param iOffSet int the iOffSet
     * @param pageSize int the pageSize
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> findByDemoNo(Integer demoNo, int iOffSet, int pageSize);

    /**
     * Find By Demo No And Dates.
     *
     * @param demoNo Integer the demoNo
     * @param dateRange DateRange the dateRange
     * @param iOffSet int the iOffSet
     * @param pageSize int the pageSize
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> findByDemoNoAndDates(Integer demoNo, DateRange dateRange, int iOffSet, int pageSize);

    public List<Object[]> findBillingsAndDemographicsByDemoIdAndDates(Integer demoNo, String payProgram, Date fromDate,
                                                                      Date toDate);

    /**
     * Find Demographics And Billings By Dx And Service Dates.
     *
     * @param dxCodes List<String> the dxCodes
     * @param from Date the from
     * @param to Date the to
     * @return List<Object[]>
     */
    public List<Object[]> findDemographicsAndBillingsByDxAndServiceDates(List<String> dxCodes, Date from, Date to);

    public List<BillingONCHeader1> findBillingsByDemoNoCh1HeaderServiceCodeAndDate(Integer demoNo,
                                                                                   List<String> serviceCodes, Date from, Date to);

    /**
     * Find Billing Data.
     *
     * @param conditions String the conditions
     * @return List<String[]>
     */
    public List<String[]> findBillingData(String conditions);

    /**
     * Find All By Pay Program.
     *
     * @param payProgram String the payProgram
     * @param startIndex int the startIndex
     * @param limit int the limit
     * @return List<BillingONCHeader1>
     */
    public List<BillingONCHeader1> findAllByPayProgram(String payProgram, int startIndex, int limit);

}
