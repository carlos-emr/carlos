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
import java.util.Map;


import io.github.carlos_emr.carlos.commn.model.UserProperty;

public interface UserPropertyDAO extends AbstractDao<UserProperty> {
    void delete(UserProperty prop);

    void saveProp(String provider, String userPropertyName, String value);

    void saveProp(UserProperty prop);

    void saveProp(String name, String val);

    String getStringValue(String provider, String propertyName);

    List<UserProperty> getAllProperties(String name, List<String> list);

    List<UserProperty> getPropValues(String name, String value);

    UserProperty getProp(String prov, String name);

    UserProperty getProp(String name);

    /** Clinic-only footer rows, oldest first; provider properties cannot override these. */
    List<UserProperty> findClinicEmailFooter();

    /**
     * Fresh detached snapshots from a locking scalar read after the durable clinic mutex.
     * Strict snapshot conflicts abort instead of returning an earlier transaction view.
     */
    List<UserProperty> findClinicEmailFooterForUpdate();

    /** Update the already locked canonical clinic row through a current write, never a snapshot merge. */
    void updateClinicEmailFooter(UserProperty current, String value);

    /** Delete an already locked duplicate clinic row through a scoped current write. */
    void deleteClinicEmailFooter(Integer id);

    /** Serialize clinic footer saves, including the first save, on the existing clinic row. */
    void lockClinicEmailFooterSettings();

    /** Match a resolved row to this owner's personal footer using the database's name collation. */
    boolean isPersonalEmailFooterRow(String providerNo, Integer propertyId);

    /** Serialize personal footer saves on the existing provider row, including first creation. */
    void lockPersonalEmailFooterOwner(String providerNo);

    /** Fresh detached personal snapshots from a locking scalar read, oldest first. */
    List<UserProperty> findPersonalEmailFooterForUpdate(String providerNo);

    /** Persist only this owner's cleaned personal row, avoiding merge loads from an old RR view. */
    void savePersonalEmailFooterRow(String providerNo, UserProperty property);

    /** Current-row deletion for one locked personal property; owner and name are checked in SQL. */
    void deletePersonalEmailFooterRow(String providerNo, Integer propertyId);

    List<UserProperty> getDemographicProperties(String providerNo);

    Map<String, String> getProviderPropertiesAsMap(String providerNo);

    void saveProperties(String providerNo, Map<String, String> props);

    public final static String COLOR_PROPERTY = "ProviderColour";
}
