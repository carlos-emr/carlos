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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanC12;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link TeleplanC12} entities.
 * Provides persistence operations for Teleplan C12 (claim rejection/correction) records
 * in the BC MSP billing system.
 *
 * @since 2026-03-17
 */
@Repository
public class TeleplanC12Dao extends AbstractDaoImpl<TeleplanC12> {

    /**
     * Constructs a new {@code TeleplanC12Dao} with the {@link TeleplanC12} entity class.
     */
    public TeleplanC12Dao() {
        super(TeleplanC12.class);
    }

    /**
     * Finds all current (non-expired) C12 records where status is not 'E'.
     *
     * @return List of current {@link TeleplanC12} records
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanC12> findCurrent() {
        Query query = createQuery("t", "t.status <> 'E'");
        return query.getResultList();
    }

    /**
     * Finds C12 records by office folio claim number.
     *
     * @param claimNo String the office folio claim number
     * @return List of {@link TeleplanC12} records matching the claim number
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanC12> findByOfficeClaimNo(String claimNo) {
        Query query = createQuery("t", "t.officeFolioClaimNo = :claimNo");
        query.setParameter("claimNo", claimNo);
        return query.getResultList();
    }

    /**
     * Finds rejected C12 records joined with their parent S21 records.
     * Returns records where status is not 'E'.
     *
     * @return List of Object arrays containing {@link TeleplanC12} and TeleplanS21 pairs
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findRejected() {
        String sql = "SELECT tc, ts FROM TeleplanC12 tc, TeleplanS21 ts WHERE tc.s21Id = ts.id AND tc.status != 'E'";
        Query query = entityManager.createQuery(sql);
        return query.getResultList();
    }

    /**
     * Finds C12 records matching a specific status and office folio claim number.
     *
     * @param status String the status to filter by
     * @param claimNo String the office folio claim number
     * @return List of matching {@link TeleplanC12} records
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanC12> select_c12_record(String status, String claimNo) {
        Query query = createQuery("t", "t.status = :status and t.officeFolioClaimNo = :claimNo");
        query.setParameter("claimNo", claimNo);
        query.setParameter("status", status);
        return query.getResultList();
    }

}
