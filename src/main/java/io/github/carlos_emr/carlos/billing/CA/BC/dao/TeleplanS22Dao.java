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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS22;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link TeleplanS22} entities.
 * Provides persistence operations for Teleplan S22 (remittance claim detail) records,
 * used in BC MSP Teleplan billing response processing.
 *
 * @since 2026-03-17
 */
@Repository
public class TeleplanS22Dao extends AbstractDaoImpl<TeleplanS22> {

    /**
     * Constructs a new {@code TeleplanS22Dao} with the {@link TeleplanS22} entity class.
     */
    public TeleplanS22Dao() {
        super(TeleplanS22.class);
    }

    /**
     * Searches S22 records by parent S21 ID, excluding a specified type, and filtering by practitioner number.
     * Results are ordered by ID.
     *
     * @param s21Id Integer the parent S21 record ID
     * @param type String the S22 type to exclude from results
     * @param practitionerNo String the practitioner number pattern (supports LIKE wildcards)
     * @return List of matching {@link TeleplanS22} records
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanS22> search_taS22(Integer s21Id, String type, String practitionerNo) {
        Query q = entityManager.createQuery("select t from TeleplanS22 t where t.s21Id=?1 and t.s22Type<>?2 and t.practitionerNo like ?3 order by t.id");
        q.setParameter(1, s21Id);
        q.setParameter(2, type);
        q.setParameter(3, practitionerNo);
        return q.getResultList();
    }
}
