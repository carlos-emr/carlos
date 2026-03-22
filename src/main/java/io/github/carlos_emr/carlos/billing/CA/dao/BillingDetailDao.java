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

import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;

/**
 * Data access interface for {@link BillingDetail} entities.
 * Defines persistence operations for billing detail line items,
 * which represent individual service codes within a billing submission.
 *
 * @since 2026-03-17
 */
public interface BillingDetailDao extends AbstractDao<BillingDetail> {

    /**
     * Finds all billing details for a given billing number.
     *
     * @param billingNo int the billing number
     * @return List of {@link BillingDetail} records
     */
    public List<BillingDetail> findByBillingNo(int billingNo);

    /**
     * Finds billing details for a given billing number and status.
     *
     * @param billingNo Integer the billing number
     * @param status String the status to filter by
     * @return List of matching {@link BillingDetail} records
     */
    public List<BillingDetail> findByBillingNoAndStatus(Integer billingNo, String status);

    /**
     * Finds non-deleted billing details for a given billing number, ordered by service code.
     *
     * @param billingNo Integer the billing number
     * @return List of active {@link BillingDetail} records
     */
    public List<BillingDetail> findByBillingNo(Integer billingNo);
}
