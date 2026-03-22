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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Orc;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link Hl7Orc} entities.
 * Provides persistence operations for HL7 ORC (Common Order) segments
 * in the BC PathNet lab integration, including filler order lookups
 * and message-based queries.
 *
 * @since 2026-03-17
 */
@Repository
@SuppressWarnings("unchecked")
public class Hl7OrcDao extends AbstractDaoImpl<Hl7Orc> {

    /**
     * Constructs a new {@code Hl7OrcDao} with the {@link Hl7Orc} entity class.
     */
    public Hl7OrcDao() {
        super(Hl7Orc.class);
    }

    /**
     * Finds filler order numbers and the latest result status change date for a given message ID.
     * Groups results by message ID.
     *
     * @param messageId Integer the HL7 message ID
     * @return List of Object arrays containing filler order number and max results report status change date
     */
    public List<Object[]> findFillerAndStatusChageByMessageId(Integer messageId) {
        String sql = "SELECT orc.fillerOrderNumber, MAX(obr.resultsReportStatusChange) " +
                "FROM Hl7Orc orc, Hl7Pid pid, Hl7Obr obr " +
                "WHERE obr.pidId = pid.id " +
                "AND orc.pidId = pid.id " +
                "AND pid.messageId = ?1 " +
                "GROUP BY pid.messageId";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, messageId);
        return query.getResultList();
    }

    /**
     * Finds ORC and PID pairs for a given message ID.
     *
     * @param messageId Integer the HL7 message ID
     * @return List of Object arrays containing {@link Hl7Orc} and Hl7Pid entities
     */
    public List<Object[]> findOrcAndPidByMessageId(Integer messageId) {
        String sql = "SELECT orc, pid FROM Hl7Orc orc, Hl7Pid pid WHERE orc.pidId = pid.id AND pid.messageId = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, messageId);
        return query.getResultList();
    }
}
