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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanAdjCodes;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link TeleplanAdjCodes} entities.
 * Provides lookup operations for Teleplan adjustment codes used in BC MSP
 * billing claim processing to indicate payment adjustments.
 *
 * @since 2026-03-17
 */
@Repository
public class TeleplanAdjCodesDao extends AbstractDaoImpl<TeleplanAdjCodes> {

    /**
     * Constructs a new {@code TeleplanAdjCodesDao} with the {@link TeleplanAdjCodes} entity class.
     */
    protected TeleplanAdjCodesDao() {
        super(TeleplanAdjCodes.class);
    }

    /**
     * Finds adjustment codes matching the specified code value.
     *
     * @param code String the adjustment code to search for
     * @return List of {@link TeleplanAdjCodes} entities matching the code
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanAdjCodes> findByCode(String code) {
        Query q = createQuery("t", "t.adjCode = ?1");
        q.setParameter(1, code);
        return q.getResultList();
    }

}
