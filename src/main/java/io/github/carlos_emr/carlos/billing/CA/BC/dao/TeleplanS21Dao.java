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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS21;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link TeleplanS21} entities.
 * Provides persistence operations for Teleplan S21 (remittance summary header) records,
 * which are part of the BC MSP Teleplan billing response processing.
 *
 * @since 2026-03-17
 */
@Repository
public class TeleplanS21Dao extends AbstractDaoImpl<TeleplanS21> {

    /**
     * Constructs a new {@code TeleplanS21Dao} with the {@link TeleplanS21} entity class.
     */
    public TeleplanS21Dao() {
        super(TeleplanS21.class);
    }

    /**
     * Finds S21 records matching the given filename, payment, and payee number,
     * ordered by payment.
     *
     * @param filename String the Teleplan response filename
     * @param payment String the payment identifier
     * @param payeeNo String the payee number
     * @return List of matching {@link TeleplanS21} records
     */
    public List<TeleplanS21> findByFilenamePaymentPayeeNo(String filename, String payment, String payeeNo) {
        Query q = entityManager.createQuery("SELECT t from TeleplanS21 t WHERE t.fileName=?1 AND t.payment=?2 AND t.payeeNo=?3 ORDER BY t.payment");
        q.setParameter(1, filename);
        q.setParameter(2, payment);
        q.setParameter(3, payeeNo);

        @SuppressWarnings("unchecked")
        List<TeleplanS21> results = q.getResultList();

        return results;
    }

    /**
     * Searches all S21 records excluding those with the given status, ordered by payment descending.
     *
     * @param excludeStatus String the status value to exclude from results
     * @return List of {@link TeleplanS21} records not matching the excluded status
     */
    public List<TeleplanS21> search_all_tahd(String excludeStatus) {
        Query q = entityManager.createQuery("SELECT t from TeleplanS21 t WHERE t.status <> ?1 ORDER BY t.payment desc");
        q.setParameter(1, excludeStatus);


        @SuppressWarnings("unchecked")
        List<TeleplanS21> results = q.getResultList();

        return results;
    }
}
