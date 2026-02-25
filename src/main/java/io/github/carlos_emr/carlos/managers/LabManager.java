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
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;


public interface LabManager {
    public List<Hl7TextMessage> getHl7Messages(LoggedInInfo loggedInInfo, Integer demographicNo, int offset, int limit);

    public List<Hl7TextInfo> getHl7TextInfo(LoggedInInfo loggedInInfo, int demographicNo);

    public Hl7TextMessage getHl7Message(LoggedInInfo loggedInInfo, int labId);

    public Path renderLab(LoggedInInfo loggedInInfo, Integer segmentId) throws PDFGenerationException;

    /**
     * Returns all {@link ProviderLabRoutingModel} records that match the given lab number,
     * lab type, and provider number.
     *
     * @param loggedInInfo LoggedInInfo the currently logged-in user; used to enforce {@code _lab} read privilege
     * @param labId Integer the unique lab segment ID to look up
     * @param labType String the lab type (e.g. {@code "HL7"}, {@code "MDS"})
     * @param providerNo String the provider number to filter routing records by
     * @return List&lt;ProviderLabRoutingModel&gt; matching routing records; empty list if none exist
     * @throws RuntimeException if the logged-in user lacks {@code _lab} read privilege
     */
    public List<ProviderLabRoutingModel> findByLabNoAndLabTypeAndProviderNo(LoggedInInfo loggedInInfo, Integer labId, String labType, String providerNo);

    /**
     * Files lab results for a provider up to (and including) a specific flagged lab,
     * depending on the fileUpToLabNo flag. Skips acknowledged or already filed results.
     *
     * @param loggedInInfo the currently logged-in user
     * @param providerNo the provider number
     * @param flaggedLabId the lab ID that was flagged (i.e., selected by the user)
     * @param labType the type of the lab
     * @param comment the comment to add while filing
     * @param fileUpToLabNo if true, file all labs up to and including flaggedLabId
     * @param onBehalfOfOtherProvider if true, updates lab status only if it is 'N' (Not Acknowledged)
     */
    public void fileLabsForProviderUpToFlaggedLab(LoggedInInfo loggedInInfo, String providerNo, String flaggedLabId, String labType, String comment, boolean fileUpToLabNo, boolean onBehalfOfOtherProvider);
}
