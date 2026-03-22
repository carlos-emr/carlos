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

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.ProviderData;

/**
 * DAO interface for healthcare provider operations.
 *
 * @since 2001
 */

public interface ProviderDataDao extends AbstractDao<ProviderData> {
    /**
     * Find By Ohip Number.
     *
     * @param ohipNumber String the ohipNumber
     * @return ProviderData
     */
    ProviderData findByOhipNumber(String ohipNumber);

    /**
     * Find By Provider No.
     *
     * @param providerNo String the providerNo
     * @return ProviderData
     */
    ProviderData findByProviderNo(String providerNo);

    /**
     * Find By Provider No.
     *
     * @param providerNo String the providerNo
     * @param status String the status
     * @param limit int the limit
     * @param offset int the offset
     * @return List<ProviderData>
     */
    List<ProviderData> findByProviderNo(String providerNo, String status, int limit, int offset);

    /**
     * Find By Provider Name.
     *
     * @param searchStr String the searchStr
     * @param status String the status
     * @param limit int the limit
     * @param offset int the offset
     * @return List<ProviderData>
     */
    List<ProviderData> findByProviderName(String searchStr, String status, int limit, int offset);

    /**
     * Find All Order By Last Name.
     * @return List<ProviderData>
     */
    List<ProviderData> findAllOrderByLastName();

    /**
     * Find By Provider Site.
     *
     * @param providerNo String the providerNo
     * @return List<ProviderData>
     */
    List<ProviderData> findByProviderSite(String providerNo);

    /**
     * Find Provider Sec User Roles.
     *
     * @param lastName String the lastName
     * @param firstName String the firstName
     * @return List<Object[]>
     */
    List<Object[]> findProviderSecUserRoles(String lastName, String firstName);

    /**
     * Find By Provider Team.
     *
     * @param providerNo String the providerNo
     * @return List<ProviderData>
     */
    List<ProviderData> findByProviderTeam(String providerNo);

    /**
     * Find All Billing.
     *
     * @param active String the active
     * @return List<ProviderData>
     */
    List<ProviderData> findAllBilling(String active);

    /**
     * Find By Type And Ohip.
     *
     * @param providerType String the providerType
     * @param insuranceNo String the insuranceNo
     * @return List<ProviderData>
     */
    List<ProviderData> findByTypeAndOhip(String providerType, String insuranceNo);

    /**
     * Find By Type.
     *
     * @param providerType String the providerType
     * @return List<ProviderData>
     */
    List<ProviderData> findByType(String providerType);

    /**
     * Find By Name.
     *
     * @param firstName String the firstName
     * @param lastName String the lastName
     * @param onlyActive boolean the onlyActive
     * @return List<ProviderData>
     */
    List<ProviderData> findByName(String firstName, String lastName, boolean onlyActive);

    /**
     * Find All.
     * @return List<ProviderData>
     */
    List<ProviderData> findAll();

    /**
     * Find All.
     *
     * @param inactive boolean the inactive
     * @return List<ProviderData>
     */
    List<ProviderData> findAll(boolean inactive);

    /**
     * Get Last Id.
     * @return Integer
     */
    Integer getLastId();
}
