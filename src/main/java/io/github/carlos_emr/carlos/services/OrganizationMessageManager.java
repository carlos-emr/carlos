/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.services;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FacilityMessage;

/**
 * Service interface for managing organization-level messages associated with
 * facilities and programs in the CARLOS EMR system.
 *
 * <p>Facility messages are used to communicate announcements, alerts, or notices
 * to healthcare providers within a specific facility or program context.
 * Messages can be scoped to individual facilities, programs, or combinations
 * of both.</p>
 *
 * @see OrganizationMessageManagerImpl
 * @see io.github.carlos_emr.carlos.commn.model.FacilityMessage
 * @since 2026-03-17
 */
public interface OrganizationMessageManager {

    /**
     * Retrieves a single facility message by its identifier.
     *
     * @param messageId String the unique identifier of the message
     * @return FacilityMessage the message record, or null if not found
     */
    FacilityMessage getMessage(String messageId);

    /**
     * Saves or updates a facility message. New messages (with null or zero ID)
     * are persisted; existing messages are merged.
     *
     * @param msg FacilityMessage the message to save or update
     */
    void saveFacilityMessage(FacilityMessage msg);

    /**
     * Retrieves all facility messages in the system.
     *
     * @return List of all FacilityMessage records
     */
    List<FacilityMessage> getMessages();

    /**
     * Retrieves messages associated with a specific facility.
     *
     * @param facilityId Integer the facility identifier to filter by
     * @return List of FacilityMessage records for the facility, or null if facilityId is null or zero
     */
    List<FacilityMessage> getMessagesByFacilityId(Integer facilityId);

    /**
     * Retrieves messages for a specific facility, including messages with
     * a null facility assignment (global messages).
     *
     * @param facilityId Integer the facility identifier to filter by
     * @return List of FacilityMessage records for the facility or with null facility, or null if facilityId is null or zero
     */
    List<FacilityMessage> getMessagesByFacilityIdOrNull(Integer facilityId);

    /**
     * Retrieves messages scoped to both a specific facility and program.
     *
     * @param facilityId Integer the facility identifier to filter by
     * @param programId Integer the program identifier to filter by
     * @return List of FacilityMessage records matching both criteria, or null if facilityId is null or zero
     */
    List<FacilityMessage> getMessagesByFacilityIdAndProgramId(Integer facilityId, Integer programId);

    /**
     * Retrieves messages matching a facility and/or program, including records
     * where either the facility or program assignment is null.
     *
     * @param facilityId Integer the facility identifier to filter by
     * @param programId Integer the program identifier to filter by
     * @return List of FacilityMessage records matching the criteria, or null if facilityId is null or zero
     */
    List<FacilityMessage> getMessagesByFacilityIdOrNullAndProgramIdOrNull(Integer facilityId, Integer programId);
}
