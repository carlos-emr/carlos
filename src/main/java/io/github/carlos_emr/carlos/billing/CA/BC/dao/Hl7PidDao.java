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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.util.List;

/**
 * Data access object for {@link Hl7Pid} entities.
 * Provides persistence operations for HL7 PID (Patient Identification) segments
 * in the BC PathNet lab integration. Supports lookups by message ID, link status,
 * observation result status, filler order number, and signed/doc note queries.
 *
 * @since 2026-03-17
 */
@Repository
@SuppressWarnings("unchecked")
public class Hl7PidDao extends AbstractDaoImpl<Hl7Pid> {

    /**
     * Constructs a new {@code Hl7PidDao} with the {@link Hl7Pid} entity class.
     */
    public Hl7PidDao() {
        super(Hl7Pid.class);
    }

    /**
     * Finds all PID records associated with the given message ID.
     *
     * @param messageId int the HL7 message ID
     * @return List of {@link Hl7Pid} records for the message
     */
    public List<Hl7Pid> findByMessageId(int messageId) {
        Query q = createQuery("h", "h.messageId = :msgId");
        q.setParameter("msgId", messageId);
        return q.getResultList();
    }

    /**
     * Finds PID and Link pairs filtered by link status. Includes records where status
     * matches the given value or is null.
     *
     * @param status String the link status to filter by
     * @return List of Object arrays containing {@link Hl7Pid} and Hl7Link entities
     */
    public List<Object[]> findPidsByStatus(String status) {
        String sql = "SELECT p, l FROM Hl7Pid p, Hl7Link l WHERE p.id = l.id AND ( l.status = :status OR l.status IS NULL)";
        Query query = entityManager.createQuery(sql);
        query.setParameter("status", status);
        return query.getResultList();
    }

    /**
     * Finds PID and MSH pairs for a given message ID.
     *
     * @param messageId Integer the HL7 message ID
     * @return List of Object arrays containing {@link Hl7Pid} and Hl7Msh entities
     */
    public List<Object[]> findPidsAndMshByMessageId(Integer messageId) {
        String sql = "SELECT pid, msh FROM Hl7Pid pid, Hl7Msh msh WHERE pid.messageId = :msgId AND msh.messageId = pid.messageId";
        Query query = entityManager.createQuery(sql);
        query.setParameter("msgId", messageId);
        return query.getResultList();
    }

    /**
     * Finds signed lab results with provider information for a given PID ID.
     *
     * @param pid Integer the PID record ID
     * @return List of Object arrays containing {@link Hl7Pid}, Hl7Link, and Provider entities
     */
    public List<Object[]> findSigned(Integer pid) {
        String sql = "SELECT hl7_pid, hl7_link, provider FROM Hl7Pid hl7_pid, Hl7Link hl7_link, Provider provider WHERE hl7_pid.id = hl7_link.id AND provider.ProviderNo = hl7_link.providerNo AND hl7_pid.id = :pid";
        Query query = entityManager.createQuery(sql);
        query.setParameter("pid", pid);
        return query.getResultList();
    }

    /**
     * Finds PID and HL7 message pairs for a given PID ID, used for retrieving document notes.
     *
     * @param pid Integer the PID record ID
     * @return List of Object arrays containing {@link Hl7Pid} and Hl7Message entities
     */
    public List<Object[]> findDocNotes(Integer pid) {
        String sql = "SELECT hl7_pid, hl7_message FROM Hl7Pid hl7_pid, Hl7Message hl7_message WHERE hl7_pid.id = :pid AND hl7_pid.messageId = hl7_message.id";
        Query query = entityManager.createQuery(sql);
        query.setParameter("pid", pid);
        return query.getResultList();
    }

    /**
     * Finds the latest observation date (max results report status change) for a given message ID.
     *
     * @param messageId Integer the HL7 message ID
     * @return Timestamp the latest observation date, or null if no results found
     */
    public Timestamp findObservationDateByMessageId(Integer messageId) {
        String sql = "SELECT MAX(obr.resultsReportStatusChange) " +
                "FROM Hl7Pid pid, Hl7Obr obr " +
                "WHERE obr.pidId = pid.id " +
                "AND pid.messageId = :messageId";
        Query query = entityManager.createQuery(sql);
        query.setMaxResults(1);
        query.setParameter("messageId", messageId);
        List<Object> resultList = query.getResultList();
        if (resultList.isEmpty()) {
            return null;
        } else {
            return (Timestamp) resultList.get(0);
        }
    }

    /**
     * Finds PID, OBR, and OBX triples filtered by observation result status and message ID.
     *
     * @param observationResultStatus String the observation result status pattern (supports LIKE wildcards)
     * @param messageId Integer the HL7 message ID
     * @return List of Object arrays containing Hl7Pid, Hl7Obr, and Hl7Obx entities
     */
    public List<Object[]> findByObservationResultStatusAndMessageId(String observationResultStatus, Integer messageId) {
        String sql = "SELECT pid, obr, obx FROM Hl7Pid pid, Hl7Obr obr, Hl7Obx obx WHERE obx.observationResultStatus like :observationResultStatus AND obx.obrId = obr.id AND obr.pidId = pid.id AND pid.messageId = :messageId";
        Query query = entityManager.createQuery(sql);
        query.setParameter("observationResultStatus", observationResultStatus);
        query.setParameter("messageId", messageId);
        return query.getResultList();
    }

    /**
     * Finds distinct message IDs and their latest report status change dates for a given filler order number.
     * Groups by message ID and orders by status change date.
     *
     * @param fillerOrderNumber String the filler order number pattern (supports LIKE wildcards)
     * @return List of Object arrays containing message ID and max results report status change date
     */
    public List<Object[]> findByFillerOrderNumber(String fillerOrderNumber) {
        String sql = "SELECT DISTINCT pid.messageId, MAX(obr.resultsReportStatusChange) " +
                "FROM Hl7Pid pid, Hl7Orc orc, Hl7Obr obr " +
                "WHERE orc.fillerOrderNumber like :fillerOrderNumber " +
                "AND orc.pidId = pid.id " +
                "AND obr.pidId = pid.id " +
                "GROUP BY pid.messageId " +
                "ORDER BY obr.resultsReportStatusChange";
        Query query = entityManager.createQuery(sql);
        query.setParameter("fillerOrderNumber", fillerOrderNumber);
        return query.getResultList();
    }
}
