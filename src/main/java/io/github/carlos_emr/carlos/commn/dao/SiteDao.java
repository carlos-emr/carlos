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

import io.github.carlos_emr.carlos.commn.model.Site;

/**
 * DAO interface for site operations.
 *
 * @since 2001
 */

public interface SiteDao extends AbstractDao<Site> {
    /**
     * Save.
     *
     * @param s Site the s
     */
    void save(Site s);

    /**
     * Get All Sites.
     * @return List<Site>
     */
    List<Site> getAllSites();

    /**
     * Get All Active Sites.
     * @return List<Site>
     */
    List<Site> getAllActiveSites();

    /**
     * Get Active Sites By Provider No.
     *
     * @param provider_no String the provider_no
     * @return List<Site>
     */
    List<Site> getActiveSitesByProviderNo(String provider_no);

    /**
     * Get By Id.
     *
     * @param id Integer the id
     * @return Site
     */
    Site getById(Integer id);

    /**
     * Get By Location.
     *
     * @param location String the location
     * @return Site
     */
    Site getByLocation(String location);

    /**
     * Get Group By Site Location.
     *
     * @param location String the location
     * @return List<String>
     */
    List<String> getGroupBySiteLocation(String location);

    /**
     * Get Provider No By Site Location.
     *
     * @param location String the location
     * @return List<String>
     */
    List<String> getProviderNoBySiteLocation(String location);

    /**
     * Get Provider No By Site Manager Provider No.
     *
     * @param providerNo String the providerNo
     * @return List<String>
     */
    List<String> getProviderNoBySiteManagerProviderNo(String providerNo);

    /**
     * Get Group By Site Manager Provider No.
     *
     * @param providerNo String the providerNo
     * @return List<String>
     */
    List<String> getGroupBySiteManagerProviderNo(String providerNo);

    /**
     * Site_searchmygroupcount.
     *
     * @param myGroupNo String the myGroupNo
     * @param siteName String the siteName
     * @return Long
     */
    Long site_searchmygroupcount(String myGroupNo, String siteName);

    /**
     * Get Site Name By Appointment No.
     *
     * @param appointmentNo String the appointmentNo
     * @return String
     */
    String getSiteNameByAppointmentNo(String appointmentNo);

    /**
     * Get Groups By Site Provider No.
     *
     * @param groupNo String the groupNo
     * @return List<String>
     */
    List<String> getGroupsBySiteProviderNo(String groupNo);

    /**
     * Get Groups For All Sites.
     * @return List<String>
     */
    List<String> getGroupsForAllSites();

    /**
     * Find By Name.
     *
     * @param name String the name
     * @return Site
     */
    Site findByName(String name);
}
