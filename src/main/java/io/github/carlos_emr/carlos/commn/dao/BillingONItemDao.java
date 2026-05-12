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

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;

/**
 * DAO for Ontario claim item rows and the header relationships built from
 * them.
 *
 * <p>Status filtering matters here because many workflows exclude deleted or
 * settled rows while audit/report paths still need broader visibility.</p>
 */
public interface BillingONItemDao extends AbstractDao<BillingONItem> {
    /** Load every item row tied to one billing header, regardless of active/deleted visibility. */
    List<BillingONItem> getBillingItemByCh1Id(Integer ch1_id);

    /** Load the default-visible item rows for one billing header. */
    List<BillingONItem> getActiveBillingItemByCh1Id(Integer ch1_id);

    /** Find headers that have item rows for the given demographic. */
    List<BillingONCHeader1> getCh1ByDemographicNo(Integer demographic_no);

    /** Load non-deleted/non-settled items for a header using the historic correction/report semantics. */
    List<BillingONItem> findByCh1Id(Integer id);

    /** Bulk-load active items for several headers while excluding deleted and settled statuses. */
    List<BillingONItem> findByCh1IdsExcludingDeletedAndSettled(List<Integer> ch1Ids);

    /** Load item rows for one header while excluding a caller-specified status value. */
    List<BillingONItem> findByCh1IdAndStatusNotEqual(Integer chId, String string);

    /** Find headers for a demographic updated since the given cutoff date. */
    List<BillingONCHeader1> getCh1ByDemographicNoSince(Integer demographic_no, Date lastUpdateDate);

    /** Find demographics with any billing-item activity since the given cutoff date. */
    List<Integer> getDemographicNoSince(Date lastUpdateDate);
}
