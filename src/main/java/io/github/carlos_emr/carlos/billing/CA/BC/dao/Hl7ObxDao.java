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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obx;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link Hl7Obx} entities.
 * Provides persistence operations for HL7 OBX (Observation Result) segments
 * in the BC PathNet lab integration, including queries by OBR ID, message ID,
 * and abnormal flag filtering.
 *
 * @since 2026-03-17
 */
@Repository
@SuppressWarnings("unchecked")
public class Hl7ObxDao extends AbstractDaoImpl<Hl7Obx> {

    /**
     * Constructs a new {@code Hl7ObxDao} with the {@link Hl7Obx} entity class.
     */
    public Hl7ObxDao() {
        super(Hl7Obx.class);
    }

    /**
     * Finds all OBX (observation result) records associated with a given OBR ID.
     *
     * @param obrId int the OBR record ID
     * @return List of {@link Hl7Obx} records for the OBR
     */
    public List<Hl7Obx> findByObrId(int obrId) {
        Query q = entityManager.createQuery("select h from Hl7Obx h where h.obrId = ?1");
        q.setParameter(1, obrId);

        List<Hl7Obx> results = q.getResultList();
        return results;
    }

    /**
     * Finds OBX and OBR pairs for a given OBR ID.
     *
     * @param id Integer the OBR record ID
     * @return List of Object arrays containing {@link Hl7Obx} and Hl7Obr entities
     */
    public List<Object[]> findObxAndObrByObrId(Integer id) {
        String sql = "SELECT obx, obr FROM Hl7Obx obx, Hl7Obr obr WHERE obr.id = :id AND obr.id = obx.obrId";
        Query query = entityManager.createQuery(sql);
        query.setParameter("id", id);
        return query.getResultList();

    }

    /**
     * Finds PID, OBR, and OBX triples filtered by message ID and abnormal flags.
     * Used to identify abnormal lab results within a specific message.
     *
     * @param messageId Integer the HL7 message ID
     * @param abnormalFlags List of String abnormal flag values to filter by
     * @return List of Object arrays containing Hl7Pid, Hl7Obr, and {@link Hl7Obx} entities
     */
    public List<Object[]> findByMessageIdAndAbnormalFlags(Integer messageId, List<String> abnormalFlags) {
        String sql = "SELECT pid, obr, obx FROM Hl7Pid pid, Hl7Obr obr, Hl7Obx obx WHERE obr.pidId = pid.id AND obx.obrId = obr.id AND obx.abnormalFlags IN (:abnormalFlags) AND pid.messageId = :messageId";

        Query query = entityManager.createQuery(sql);
        query.setParameter("abnormalFlags", abnormalFlags);
        query.setParameter("messageId", messageId);
        return query.getResultList();
    }
}
