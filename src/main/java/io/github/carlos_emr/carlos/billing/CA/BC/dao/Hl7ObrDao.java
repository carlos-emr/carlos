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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link Hl7Obr} entities.
 * Provides persistence operations for HL7 OBR (Observation Request) segments
 * in the BC PathNet lab integration, including queries for lab results,
 * result statuses, and message-based lookups.
 *
 * @since 2026-03-17
 */
@Repository
@SuppressWarnings("unchecked")
public class Hl7ObrDao extends AbstractDaoImpl<Hl7Obr> {

    /**
     * Constructs a new {@code Hl7ObrDao} with the {@link Hl7Obr} entity class.
     */
    public Hl7ObrDao() {
        super(Hl7Obr.class);
    }

    /**
     * Finds all OBR records associated with a given PID ID.
     *
     * @param id int the PID record ID
     * @return List of {@link Hl7Obr} records for the given PID
     */
    public List<Hl7Obr> findByPid(int id) {
        Query query = createQuery("h", "h.pidId = :id");
        query.setParameter("id", id);
        return query.getResultList();
    }

    /**
     * Finds OBR and OBX pairs for a given PID, ordered by diagnostic service section ID.
     *
     * @param pid Integer the PID record ID
     * @return List of Object arrays containing {@link Hl7Obr} and Hl7Obx entities
     */
    public List<Object[]> findLabResultsByPid(Integer pid) {
        String sql = "SELECT hl7_obr, hl7_obx FROM Hl7Obr hl7_obr, Hl7Obx hl7_obx WHERE hl7_obr.id = hl7_obx.obrId AND hl7_obr.pidId = ?1 ORDER BY hl7_obr.diagnosticServiceSectId";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, pid);
        return query.getResultList();
    }

    /**
     * Finds the minimum (earliest/lowest) result status for a given message ID.
     *
     * @param messageId Integer the HL7 message ID
     * @return List containing the minimum result status value
     */
    public List<Object[]> findMinResultStatusByMessageId(Integer messageId) {
        String sql = "SELECT MIN(obr.resultStatus) FROM Hl7Pid pid, Hl7Obr obr, Hl7Obx obx " +
                "WHERE obr.pidId = pid.id " +
                "AND obx.obrId = obr.id " +
                "AND pid.messageId = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, messageId);
        return query.getResultList();
    }

    /**
     * Finds PID, OBR, and OBX triples for a given message ID.
     *
     * @param messageId Integer the HL7 message ID
     * @return List of Object arrays containing Hl7Pid, {@link Hl7Obr}, and Hl7Obx entities
     */
    public List<Object[]> findByMessageId(Integer messageId) {
        String sql = "SELECT pid, obr, obx FROM Hl7Pid pid, Hl7Obr obr, Hl7Obx obx WHERE obr.pidId = pid.id AND obx.obrId = obr.id AND pid.messageId = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, messageId);
        return query.getResultList();
    }

}
