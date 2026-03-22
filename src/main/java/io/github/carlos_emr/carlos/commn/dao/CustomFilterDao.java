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

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;

/**
 * DAO interface for custom filter operations.
 *
 * @since 2001
 */

public interface CustomFilterDao extends AbstractDao<CustomFilter> {

    /**
     * Find By Name.
     *
     * @param name String the name
     * @return CustomFilter
     */
    public CustomFilter findByName(String name);

    /**
     * Find By Name And Provider No.
     *
     * @param name String the name
     * @param providerNo String the providerNo
     * @return CustomFilter
     */
    public CustomFilter findByNameAndProviderNo(String name, String providerNo);

    /**
     * Get Custom Filters.
     * @return List<CustomFilter>
     */
    public List<CustomFilter> getCustomFilters();

    /**
     * Find By Provider No.
     *
     * @param providerNo String the providerNo
     * @return List<CustomFilter>
     */
    public List<CustomFilter> findByProviderNo(String providerNo);

    /**
     * Get Custom Filter With Short Cut.
     *
     * @param providerNo String the providerNo
     * @return List<CustomFilter>
     */
    public List<CustomFilter> getCustomFilterWithShortCut(String providerNo);

    /**
     * Delete Custom Filter.
     *
     * @param name String the name
     */
    public void deleteCustomFilter(String name);
}