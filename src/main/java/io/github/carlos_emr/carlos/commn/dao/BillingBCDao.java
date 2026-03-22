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

import java.util.List;

/**
 * DAO interface for healthcare billing operations.
 *
 * @since 2001
 */

public interface BillingBCDao extends BillingDao {
    /**
     * Find Billing Services.
     *
     * @param billRegion String the billRegion
     * @param serviceGroup String the serviceGroup
     * @param serviceType String the serviceType
     * @return List<Object[]>
     */
    List<Object[]> findBillingServices(String billRegion, String serviceGroup, String serviceType);

    /**
     * Find Billing Services By Type.
     *
     * @param serviceType String the serviceType
     * @return List<Object[]>
     */
    List<Object[]> findBillingServicesByType(String serviceType);

    /**
     * Find Billing Services.
     *
     * @param billRegion String the billRegion
     * @param serviceGroup String the serviceGroup
     * @param serviceType String the serviceType
     * @param billReferenceDate String the billReferenceDate
     * @return List<Object[]>
     */
    List<Object[]> findBillingServices(String billRegion, String serviceGroup, String serviceType, String billReferenceDate);

    /**
     * Find Billing Locations.
     *
     * @param billRegion String the billRegion
     * @return List<Object[]>
     */
    List<Object[]> findBillingLocations(String billRegion);

    /**
     * Find Billing Visits.
     *
     * @param billRegion String the billRegion
     * @return List<Object[]>
     */
    List<Object[]> findBillingVisits(String billRegion);

    /**
     * Find Injury Locations.
     * @return List<Object[]>
     */
    List<Object[]> findInjuryLocations();
}
