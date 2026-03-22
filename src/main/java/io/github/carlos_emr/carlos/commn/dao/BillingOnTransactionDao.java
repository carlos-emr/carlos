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

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;

/**
 * DAO interface for healthcare billing operations.
 *
 * @since 2001
 */

public interface BillingOnTransactionDao extends AbstractDao<BillingOnTransaction> {
    /**
     * Get Trans Template.
     *
     * @param cheader1 BillingONCHeader1 the cheader1
     * @param billItem BillingONItem the billItem
     * @param billPayment BillingONPayment the billPayment
     * @param curProviderNo String the curProviderNo
     * @param itempaymentId int the itempaymentId
     * @return BillingOnTransaction
     */
    BillingOnTransaction getTransTemplate(BillingONCHeader1 cheader1, BillingONItem billItem, BillingONPayment billPayment, String curProviderNo, int itempaymentId);

    /**
     * Get Update Cheader1 Trans Template.
     *
     * @param cheader1 BillingClaimHeader1Data the cheader1
     * @param curProviderNo String the curProviderNo
     * @return BillingOnTransaction
     */
    BillingOnTransaction getUpdateCheader1TransTemplate(BillingClaimHeader1Data cheader1, String curProviderNo);
}
