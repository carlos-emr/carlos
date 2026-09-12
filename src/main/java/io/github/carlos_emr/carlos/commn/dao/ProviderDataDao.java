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

public interface ProviderDataDao extends AbstractDao<ProviderData> {
    ProviderData findByOhipNumber(String ohipNumber);

    ProviderData findByProviderNo(String providerNo);

    List<ProviderData> findByProviderNo(String providerNo, String status, int limit, int offset);

    List<ProviderData> findByProviderName(String searchStr, String status, int limit, int offset);

    List<ProviderData> findAllOrderByLastName();

    List<ProviderData> findByProviderSite(String providerNo);

    /**
     * Finds active providers matching a name search, paired with each of their security role
     * assignments.
     *
     * <p>Rows come from a LEFT JOIN, so a provider with no role assignment is still returned,
     * with every {@code secUserRole} column {@code null}. A provider holding several roles
     * appears once per role.</p>
     *
     * <p>Each element is a positional tuple:</p>
     * <ol start="0">
     *   <li>{@code secUserRole.id} — assignment id; {@code null} when the provider has no role</li>
     *   <li>{@code secUserRole.role_name} — role name; {@code null} when the provider has no role</li>
     *   <li>{@code provider.provider_no}</li>
     *   <li>{@code provider.first_name}</li>
     *   <li>{@code provider.last_name}</li>
     *   <li>{@code secUserRole.activeyn} — {@code 1} when the assignment is active. Anything
     *       else, including {@code null} on legacy rows, means inactive. Inactive assignments
     *       are excluded from authorization (see
     *       {@link io.github.carlos_emr.carlos.daos.security.SecuserroleDao#findActiveByProviderNo(Object)}),
     *       so callers must not treat them as conferring the role.</li>
     * </ol>
     *
     * <p>The query filters providers to {@code status = '1'} but deliberately does not filter on
     * {@code activeyn}: administration screens list every assignment so inactive ones stay
     * manageable, and use index 5 to tell them apart.</p>
     *
     * @param lastName  last-name SQL LIKE pattern; callers supply their own wildcards
     * @param firstName first-name SQL LIKE pattern; callers supply their own wildcards
     * @return one tuple per provider/role pairing, ordered by first name, last name, role name
     * @since 2026-02-02
     */
    List<Object[]> findProviderSecUserRoles(String lastName, String firstName);

    List<ProviderData> findByProviderTeam(String providerNo);

    List<ProviderData> findAllBilling(String active);

    List<ProviderData> findByTypeAndOhip(String providerType, String insuranceNo);

    List<ProviderData> findByType(String providerType);

    List<ProviderData> findByName(String firstName, String lastName, boolean onlyActive);

    List<ProviderData> findAll();

    List<ProviderData> findAll(boolean inactive);

    Integer getLastId();
}
