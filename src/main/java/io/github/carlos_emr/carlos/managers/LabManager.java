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
 * Core service interface for managing laboratory results in the EMR.
 * Provides capabilities to retrieve parsed HL7 lab messages for a specific patient,
 * fetch metadata/summaries, and generate PDF renderings of lab reports.
 */
public interface LabManager {

    /**
     * Retrieves a paginated list of full HL7 text messages (lab reports) for a specific patient.
     * 
     * @param loggedInInfo Security context of the logged-in user.
     * @param demographicNo The patient's demographic ID.
     * @param offset The starting index for pagination.
     * @param limit The maximum number of records to return.
     * @return List of {@link Hl7TextMessage} representing parsed lab reports.
     */
    public List<Hl7TextMessage> getHl7Messages(LoggedInInfo loggedInInfo, Integer demographicNo, int offset, int limit);

    /**
     * Retrieves lightweight summary/metadata records for all lab reports tied to a patient.
     * Useful for building list views without loading full message bodies.
     * 
     * @param loggedInInfo Security context.
     * @param demographicNo The patient's demographic ID.
     * @return List of {@link Hl7TextInfo} summaries.
     */
    public List<Hl7TextInfo> getHl7TextInfo(LoggedInInfo loggedInInfo, int demographicNo);

    /**
     * Retrieves a specific HL7 lab message by its unique ID.
     * 
     * @param loggedInInfo Security context.
     * @param labId The unique identifier of the lab message.
     * @return The requested {@link Hl7TextMessage}.
     */
    public Hl7TextMessage getHl7Message(LoggedInInfo loggedInInfo, int labId);

    /**
     * Renders a specific lab report segment into a printable PDF file.
     * Typically used for printing or downloading official lab results.
     * 
     * @param loggedInInfo Security context.
     * @param segmentId The ID of the specific lab segment to render.
     * @return A {@link Path} pointing to the generated PDF file in the temporary directory.
     * @throws PDFGenerationException if the PDF engine fails to construct the document.
     */
    public Path renderLab(LoggedInInfo loggedInInfo, Integer segmentId) throws PDFGenerationException;
}
