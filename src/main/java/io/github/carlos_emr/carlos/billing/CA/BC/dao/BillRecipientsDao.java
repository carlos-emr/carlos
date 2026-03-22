/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillRecipients;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link BillRecipients} entities.
 * Provides persistence operations for BC billing recipients,
 * which represent the parties associated with a billing transaction.
 *
 * @since 2026-03-17
 */
@Repository
public class BillRecipientsDao extends AbstractDaoImpl<BillRecipients> {

    /**
     * Constructs a new {@code BillRecipientsDao} with the {@link BillRecipients} entity class.
     */
    public BillRecipientsDao() {
        super(BillRecipients.class);
    }

    /**
     * Finds all billing recipients associated with the specified billing number.
     *
     * @param billingNo int the billing number to search for
     * @return List of {@link BillRecipients} matching the billing number
     */
    public List<BillRecipients> findByBillingNo(int billingNo) {
        Query q = entityManager.createQuery("SELECT b FROM BillRecipients b WHERE b.billingNo=?1");

        q.setParameter(1, billingNo);

        @SuppressWarnings("unchecked")
        List<BillRecipients> results = q.getResultList();

        return results;
    }
}
