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

/**
 * DAO interface for healthcare billing operations.
 *
 * @since 2001
 */

public interface BillingServiceDao extends AbstractDao<BillingService> {
    static public final String BC = "BC";

    /**
     * Get Billing Code Attr.
     *
     * @param serviceCode String the serviceCode
     * @return List<BillingService>
     */
    List<BillingService> getBillingCodeAttr(String serviceCode);

    /**
     * Code Requires S L I.
     *
     * @param code String the code
     * @return boolean
     */
    boolean codeRequiresSLI(String code);

    /**
     * Find Billing Codes By Code.
     *
     * @param code String the code
     * @param region String the region
     * @return List<BillingService>
     */
    List<BillingService> findBillingCodesByCode(String code, String region);

    /**
     * Find By Service Code.
     *
     * @param code String the code
     * @return List<BillingService>
     */
    List<BillingService> findByServiceCode(String code);

    /**
     * Find By Service Code And Date.
     *
     * @param code String the code
     * @param date Date the date
     * @return List<BillingService>
     */
    List<BillingService> findByServiceCodeAndDate(String code, Date date);

    /**
     * Find By Service Codes.
     *
     * @param codes List<String> the codes
     * @return List<BillingService>
     */
    List<BillingService> findByServiceCodes(List<String> codes);

    /**
     * Fin All Private Codes.
     * @return List<BillingService>
     */
    List<BillingService> finAllPrivateCodes();

    /**
     * Find Billing Codes By Code.
     *
     * @param code String the code
     * @param region String the region
     * @param order int the order
     * @return List<BillingService>
     */
    List<BillingService> findBillingCodesByCode(String code, String region, int order);

    /**
     * Find Billing Codes By Code.
     *
     * @param code String the code
     * @param region String the region
     * @param billingDate Date the billingDate
     * @param order int the order
     * @return List<BillingService>
     */
    List<BillingService> findBillingCodesByCode(String code, String region, Date billingDate, int order);

    /**
     * Search Desc Billing Code.
     *
     * @param code String the code
     * @param region String the region
     * @return String
     */
    String searchDescBillingCode(String code, String region);

    /**
     * Search.
     *
     * @param str String the str
     * @param region String the region
     * @param billingDate Date the billingDate
     * @return List<BillingService>
     */
    List<BillingService> search(String str, String region, Date billingDate);

    /**
     * Search Billing Code.
     *
     * @param str String the str
     * @param region String the region
     * @return BillingService
     */
    BillingService searchBillingCode(String str, String region);

    /**
     * Search Billing Code.
     *
     * @param str String the str
     * @param region String the region
     * @param billingDate Date the billingDate
     * @return BillingService
     */
    BillingService searchBillingCode(String str, String region, Date billingDate);

    /**
     * Search Private Billing Code.
     *
     * @param privateCode String the privateCode
     * @param billingDate Date the billingDate
     * @return BillingService
     */
    BillingService searchPrivateBillingCode(String privateCode, Date billingDate);

    /**
     * Edit Billing Code Desc.
     *
     * @param desc String the desc
     * @param val String the val
     * @param codeId Integer the codeId
     * @return boolean
     */
    boolean editBillingCodeDesc(String desc, String val, Integer codeId);

    /**
     * Edit Billing Code.
     *
     * @param val String the val
     * @param codeId Integer the codeId
     * @return boolean
     */
    boolean editBillingCode(String val, Integer codeId);

    /**
     * Insert Billing Code.
     *
     * @param code String the code
     * @param date String the date
     * @param description String the description
     * @param termDate String the termDate
     * @param region String the region
     * @return boolean
     */
    boolean insertBillingCode(String code, String date, String description, String termDate, String region);

    /**
     * Get Latest Service Date.
     *
     * @param endDate Date the endDate
     * @param serviceCode String the serviceCode
     * @return Date
     */
    Date getLatestServiceDate(Date endDate, String serviceCode);

    /**
     * Get Unit Price.
     *
     * @param bcode String the bcode
     * @param date Date the date
     * @return Object[]
     */
    Object[] getUnitPrice(String bcode, Date date);

    /**
     * Get Unit Percentage.
     *
     * @param bcode String the bcode
     * @param date Date the date
     * @return String
     */
    String getUnitPercentage(String bcode, Date date);

    /**
     * Find Billing Codes By Font Style.
     *
     * @param styleId Integer the styleId
     * @return List<BillingService>
     */
    List<BillingService> findBillingCodesByFontStyle(Integer styleId);

    /**
     * Find By Region Group And Type.
     *
     * @param billRegion String the billRegion
     * @param serviceGroup String the serviceGroup
     * @param serviceType String the serviceType
     * @return List<BillingService>
     */
    List<BillingService> findByRegionGroupAndType(String billRegion, String serviceGroup, String serviceType);

    /**
     * Find By Service Code Or Description.
     *
     * @param serviceCode String the serviceCode
     * @return List<BillingService>
     */
    List<BillingService> findByServiceCodeOrDescription(String serviceCode);

    /**
     * Find Most Recent By Service Code.
     *
     * @param serviceCode String the serviceCode
     * @return List<BillingService>
     */
    List<BillingService> findMostRecentByServiceCode(String serviceCode);

    /**
     * Find All.
     * @return List<BillingService>
     */
    List<BillingService> findAll();

    /**
     * Find Something By Billing Id.
     *
     * @param billingNo Integer the billingNo
     * @return List<Object[]>
     */
    List<Object[]> findSomethingByBillingId(Integer billingNo);

    /**
     * Find Gst.
     *
     * @param code String the code
     * @param date Date the date
     * @return List<BillingService>
     */
    List<BillingService> findGst(String code, Date date);

    /**
     * Search_service_code.
     *
     * @param code String the code
     * @param code1 String the code1
     * @param code2 String the code2
     * @param desc String the desc
     * @param desc1 String the desc1
     * @param desc2 String the desc2
     * @return List<BillingService>
     */
    List<BillingService> search_service_code(String code, String code1, String code2, String desc, String desc1, String desc2);

    /**
     * Find By Service Code And Latest Date.
     *
     * @param serviceCode String the serviceCode
     * @param date Date the date
     * @return List<BillingService>
     */
    List<BillingService> findByServiceCodeAndLatestDate(String serviceCode, Date date);

    /**
     * Find Billing Service And Ctl Billing Service By Magic.
     *
     * @param serviceType String the serviceType
     * @param serviceGroup String the serviceGroup
     * @param billReferenceDate Date the billReferenceDate
     * @return List<Object[]>
     */
    List<Object[]> findBillingServiceAndCtlBillingServiceByMagic(String serviceType, String serviceGroup, Date billReferenceDate);

    /**
     * Find Billing Codes By Code And Termination Date.
     *
     * @param serviceCode String the serviceCode
     * @param terminationDate Date the terminationDate
     * @return List<Object>
     */
    List<Object> findBillingCodesByCodeAndTerminationDate(String serviceCode, Date terminationDate);

    /**
     * Get Code Description.
     *
     * @param val String the val
     * @param billReferalDate String the billReferalDate
     * @return String
     */
    String getCodeDescription(String val, String billReferalDate);
}
