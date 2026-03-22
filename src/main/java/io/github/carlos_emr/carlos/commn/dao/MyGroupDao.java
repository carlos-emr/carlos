/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.Provider;

import java.util.List;

/**
 * DAO interface for provider group operations.
 *
 * @since 2001
 */

public interface MyGroupDao extends AbstractDao<MyGroup> {
    /**
     * Find All.
     * @return List<MyGroup>
     */
    List<MyGroup> findAll();

    /**
     * Get Group Doctors.
     *
     * @param groupNo String the groupNo
     * @return List<String>
     */
    List<String> getGroupDoctors(String groupNo);

    /**
     * Get Groups.
     * @return List<String>
     */
    List<String> getGroups();

    /**
     * Get Group By Group No.
     *
     * @param groupNo String the groupNo
     * @return List<MyGroup>
     */
    List<MyGroup> getGroupByGroupNo(String groupNo);

    /**
     * Delete Group Member.
     *
     * @param myGroupNo String the myGroupNo
     * @param providerNo String the providerNo
     */
    void deleteGroupMember(String myGroupNo, String providerNo);

    /**
     * Get Provider Groups.
     *
     * @param providerNo String the providerNo
     * @return List<MyGroup>
     */
    List<MyGroup> getProviderGroups(String providerNo);

    /**
     * Get Default Billing Form.
     *
     * @param myGroupNo String the myGroupNo
     * @return String
     */
    String getDefaultBillingForm(String myGroupNo);

    /**
     * Search_groupprovider.
     *
     * @param groupNo String the groupNo
     * @return List<Provider>
     */
    List<Provider> search_groupprovider(String groupNo);

    /**
     * Search_mygroup.
     *
     * @param groupNo String the groupNo
     * @return List<MyGroup>
     */
    List<MyGroup> search_mygroup(String groupNo);

    /**
     * Searchmygroupno.
     * @return List<MyGroup>
     */
    List<MyGroup> searchmygroupno();

    /**
     * Search_providersgroup.
     *
     * @param lastName String the lastName
     * @param firstName String the firstName
     * @return List<MyGroup>
     */
    List<MyGroup> search_providersgroup(String lastName, String firstName);
}
