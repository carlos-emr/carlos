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

package io.github.carlos_emr.carlos.billing.CA.dao;

import java.util.List;

import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;

/**
 * Data access interface for {@link BillingInr} entities.
 * Defines persistence operations for INR (International Normalized Ratio) billing records,
 * used for billing anticoagulation monitoring services.
 *
 * @since 2026-03-17
 */
public interface BillingInrDao extends AbstractDao<BillingInr> {

    /**
     * Searches for INR billing records joined with demographic data by billing INR number.
     * Excludes deleted records.
     *
     * @param billingInrNo Integer the billing INR record ID
     * @return List of Object arrays containing {@link BillingInr} and Demographic entities
     */
    public List<Object[]> search_inrbilling_dt_billno(Integer billingInrNo);

    /**
     * Finds current (non-deleted) INR billing records for a given provider.
     *
     * @param providerNo String the provider number pattern (supports LIKE wildcards)
     * @return List of active {@link BillingInr} records
     */
    public List<BillingInr> findCurrentByProviderNo(String providerNo);
}
