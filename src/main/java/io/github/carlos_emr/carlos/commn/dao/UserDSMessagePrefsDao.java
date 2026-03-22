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

import java.util.Hashtable;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.UserDSMessagePrefs;

/**
 * DAO interface for user operations.
 *
 * @since 2001
 */

public interface UserDSMessagePrefsDao extends AbstractDao<UserDSMessagePrefs> {
    /**
     * Save Prop.
     *
     * @param prop UserDSMessagePrefs the prop
     */
    void saveProp(UserDSMessagePrefs prop);

    /**
     * Update Prop.
     *
     * @param prop UserDSMessagePrefs the prop
     */
    void updateProp(UserDSMessagePrefs prop);

    /**
     * Get Message Prefs On Type.
     *
     * @param prov String the prov
     * @param name String the name
     * @return UserDSMessagePrefs
     */
    UserDSMessagePrefs getMessagePrefsOnType(String prov, String name);

    /**
     * Get Hashof Messages.
     *
     * @param providerNo String the providerNo
     * @param name String the name
     * @return Hashtable<String, Long>
     */
    Hashtable<String, Long> getHashofMessages(String providerNo, String name);

    /**
     * Find Messages.
     *
     * @param providerNo String the providerNo
     * @param resourceType String the resourceType
     * @param resourceId String the resourceId
     * @param archived boolean the archived
     * @return List<UserDSMessagePrefs>
     */
    List<UserDSMessagePrefs> findMessages(String providerNo, String resourceType, String resourceId, boolean archived);

    /**
     * Get Ds Message.
     *
     * @param providerNo String the providerNo
     * @param resourceType String the resourceType
     * @param resourceId String the resourceId
     * @param archived boolean the archived
     * @return UserDSMessagePrefs
     */
    UserDSMessagePrefs getDsMessage(String providerNo, String resourceType, String resourceId, boolean archived);

    /**
     * Find All By Resource Id.
     *
     * @param resourceId String the resourceId
     * @return List<UserDSMessagePrefs>
     */
    List<UserDSMessagePrefs> findAllByResourceId(String resourceId);
}
