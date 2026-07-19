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

import io.github.carlos_emr.carlos.commn.model.BillingService;

public interface BillingServiceDao extends AbstractDao<BillingService> {
    static public final String BC = "BC";

    List<BillingService> getBillingCodeAttr(String serviceCode);

    boolean codeRequiresSLI(String code);

    List<BillingService> findBillingCodesByCode(String code, String region);

    List<BillingService> findByServiceCode(String code);

    List<BillingService> findByServiceCodeAndDate(String code, Date date);

    List<BillingService> findByServiceCodes(List<String> codes);

    List<BillingService> finAllPrivateCodes();

    List<BillingService> findBillingCodesByCode(String code, String region, int order);

    List<BillingService> findBillingCodesByCode(String code, String region, Date billingDate, int order);

    String searchDescBillingCode(String code, String region);

    List<BillingService> search(String str, String region, Date billingDate);

    BillingService searchBillingCode(String str, String region);

    BillingService searchBillingCode(String str, String region, Date billingDate);

    BillingService searchPrivateBillingCode(String privateCode, Date billingDate);

    boolean editBillingCodeDesc(String desc, String val, Integer codeId);

    boolean editBillingCode(String val, Integer codeId);

    boolean insertBillingCode(String code, String date, String description, String termDate, String region);

    Date getLatestServiceDate(Date endDate, String serviceCode);

    Object[] getUnitPrice(String bcode, Date date);

    String getUnitPercentage(String bcode, Date date);

    List<BillingService> findBillingCodesByFontStyle(Integer styleId);

    List<BillingService> findByRegionGroupAndType(String billRegion, String serviceGroup, String serviceType);

    List<BillingService> findByServiceCodeOrDescription(String serviceCode);

    List<BillingService> findMostRecentByServiceCode(String serviceCode);

    List<BillingService> findAll();

    List<Object[]> findSomethingByBillingId(Integer billingNo);

    List<BillingService> findGst(String code, Date date);

    List<BillingService> search_service_code(String code, String code1, String code2, String desc, String desc1, String desc2);

    List<BillingService> findByServiceCodeAndLatestDate(String serviceCode, Date date);

    List<Object[]> findBillingServiceAndCtlBillingServiceByMagic(String serviceType, String serviceGroup, Date billReferenceDate);

    List<Object> findBillingCodesByCodeAndTerminationDate(String serviceCode, Date terminationDate);

    String getCodeDescription(String val, String billReferalDate);
}
