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

    public List<BillingONCHeader1> getBillCheader1ByDemographicNo(int demographic_no);

    public int getNumberOfDemographicsWithInvoicesForProvider(String providerNo, Date startDate, Date endDate,
                                                              boolean distinct);

    public void createBills(List<BillingONCHeader1> lBills);

    public String createBill(String provider, Integer demographic, String code, String clinicRefCode, Date serviceDate,
                             String curUser);

    public String createBill(String provider, Integer demographic, String code, String dxCode, String clinicRefCode,
                             Date serviceDate, String curUser);

    public String createBills(String provider, List<String> demographic_nos, List<String> codes, List<String> dxcodes,
                              String clinicRefCode, Date serviceDate, String curUser);

    public boolean billedBetweenTheseDays(String serviceCode, Integer demographicNo, Date startDate, Date endDate);

    public int getDaysSinceBilled(String serviceCode, Integer demographicNo);

    public int getDaysSincePaid(String serviceCode, Integer demographic_no);

    public List<BillingONCHeader1> getInvoices(Integer demographicNo, Integer limit);

    public List<BillingONCHeader1> getInvoices(Integer demographicNo);

    public List<BillingONCHeader1> getInvoicesByIds(List<Integer> ids);

    public List<Map<String, Object>> getInvoicesMeta(Integer demographicNo);

    // public GstControlDao getGstControlDao();

    // public void setGstControlDao(GstControlDao gstControlDao);

    public BillingONItem findBillingONItemByServiceCode(BillingONCHeader1 ch1, String serviceCode);

    public List<BillingONCHeader1> get3rdPartyInvoiceByProvider(Provider p, Date start, Date end, Locale locale);

    public List<BillingONCHeader1> get3rdPartyInvoiceByDate(Date start, Date end, Locale locale);

    public BillingONCHeader1 getLastOHIPBillingDateForServiceCode(Integer demographicNo, String serviceCode);

    public List<BillingONCHeader1> findByAppointmentNo(Integer appointmentNo);

    public List<Object[]> countBillingVisitsByProvider(String providerNo, Date dateBegin, Date dateEnd);

    public List<Object[]> countBillingVisitsByCreator(String providerNo, Date dateBegin, Date dateEnd);

    public List<Long> count_larrykain_clinic(String facilityNum, Date startDate, Date endDate);

    public List<Long> count_larrykain_hospital(String facilityNum1, String facilityNum2, String facilityNum3,
                                               String facilityNum4, Date startDate, Date endDate);

    public List<Long> count_larrykain_other(String facilityNum1, String facilityNum2, String facilityNum3,
                                            String facilityNum4, String facilityNum5, Date startDate, Date endDate);

    public List<BillingONCHeader1> findBillingsByManyThings(String status, String providerNo, Date startDate,
                                                            Date endDate, Integer demoNo);

    public List<BillingONCHeader1> findByProviderStatusAndDateRange(String providerNo, List<String> statuses,
                                                                    DateRange dateRange);

    public List<Object[]> findBillingsAndDemographicsById(Integer id);

    public List<BillingONCHeader1> findByMagic(List<String> payPrograms, String statusType, String providerNo,
                                               Date startDate, Date endDate, Integer demoNo, String visitLocation, Date paymentStartDate,
                                               Date paymentEndDate);

    public List<BillingONCHeader1> getBillingItemByDxCode(Integer demographicNo, String dxCode);

    public List<Object[]> findByMagic2(List<String> payPrograms, String statusType, String providerNo, Date startDate,
                                       Date endDate, Integer demoNo, List<String> serviceCodes, String dx, String visitType, String visitLocation,
                                       Date paymentStartDate, Date paymentEndDate);

    public List<Object[]> findByMagic2(List<String> payPrograms, String statusType, String providerNo, Date startDate,
                                       Date endDate, Integer demoNo, List<String> serviceCodes, String dx, String visitType, String visitLocation,
                                       Date paymentStartDate, Date paymentEndDate, String claimNo);

    public List<BillingONCHeader1> findByDemoNo(Integer demoNo, int iOffSet, int pageSize);

    public List<BillingONCHeader1> findByDemoNoAndDates(Integer demoNo, DateRange dateRange, int iOffSet, int pageSize);

    public List<Object[]> findBillingsAndDemographicsByDemoIdAndDates(Integer demoNo, String payProgram, Date fromDate,
                                                                      Date toDate);

    public List<Object[]> findDemographicsAndBillingsByDxAndServiceDates(List<String> dxCodes, Date from, Date to);

    public List<BillingONCHeader1> findBillingsByDemoNoCh1HeaderServiceCodeAndDate(Integer demoNo,
                                                                                   List<String> serviceCodes, Date from, Date to);

    public List<String[]> findBillingData(String conditions);

    public List<BillingONCHeader1> findAllByPayProgram(String payProgram, int startIndex, int limit);

}
