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

import io.github.carlos_emr.carlos.billing.CA.BC.model.WcbNoiCode;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link WcbNoiCode} entities.
 * Provides lookup operations for WCB (Workers' Compensation Board) Nature of Injury codes
 * used in BC workers' compensation billing.
 *
 * @since 2026-03-17
 */
@Repository
@SuppressWarnings("unchecked")
public class WcbNoiCodeDao extends AbstractDaoImpl<WcbNoiCode> {

    /**
     * Constructs a new {@code WcbNoiCodeDao} with the {@link WcbNoiCode} entity class.
     */
    public WcbNoiCodeDao() {
        super(WcbNoiCode.class);
    }

    /**
     * Searches for WCB NOI codes by code value or any of the three hierarchy levels.
     * Results are ordered by level1, level2, level3.
     *
     * @param search String the search pattern (supports LIKE wildcards) applied to code, level1, level2, and level3
     * @return List of matching {@link WcbNoiCode} records
     */
    public List<WcbNoiCode> findByCodeOrLevel(String search) {
        Query q = createQuery("w", "w.code like :s OR w.level1 like :s OR w.level2 like :s OR w.level3 like :s ORDER BY w.level1, w.level2, w.level3");
        q.setParameter("s", search);
        return q.getResultList();
    }
}
