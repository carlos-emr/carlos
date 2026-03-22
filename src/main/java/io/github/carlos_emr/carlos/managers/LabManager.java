/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;


/**
 * Service interface for managing HL7 laboratory results in the CARLOS EMR system.
 *
 * <p>Provides retrieval of HL7 lab messages by patient or identifier, along with
 * PDF rendering capability for lab result display and fax transmission.</p>
 *
 * @see LabManagerImpl
 * @see io.github.carlos_emr.carlos.commn.model.Hl7TextMessage
 * @see io.github.carlos_emr.carlos.commn.model.Hl7TextInfo
 * @since 2026-03-17
 */
public interface LabManager {

    /**
     * Retrieves HL7 lab messages for a patient with pagination.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicNo Integer the patient demographic number
     * @param offset int the starting index for pagination
     * @param limit int the maximum number of messages to return
     * @return List of Hl7TextMessage records for the patient
     */
    public List<Hl7TextMessage> getHl7Messages(LoggedInInfo loggedInInfo, Integer demographicNo, int offset, int limit);

    /**
     * Retrieves HL7 text info metadata for all lab results of a patient.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicNo int the patient demographic number
     * @return List of Hl7TextInfo metadata records
     */
    public List<Hl7TextInfo> getHl7TextInfo(LoggedInInfo loggedInInfo, int demographicNo);

    /**
     * Retrieves a single HL7 lab message by its identifier.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param labId int the lab result identifier
     * @return Hl7TextMessage the lab message, or null if not found
     */
    public Hl7TextMessage getHl7Message(LoggedInInfo loggedInInfo, int labId);

    /**
     * Renders a lab result segment as a PDF document.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param segmentId Integer the lab segment identifier to render
     * @return Path to the generated temporary PDF file
     * @throws PDFGenerationException if PDF rendering fails
     */
    public Path renderLab(LoggedInInfo loggedInInfo, Integer segmentId) throws PDFGenerationException;
}
