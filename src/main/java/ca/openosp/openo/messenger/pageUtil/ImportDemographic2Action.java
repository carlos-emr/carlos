//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.messenger.pageUtil;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.managers.MessengerDemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for importing patient demographics from remote facilities.
 * 
 * <p>This action handles the cross-facility patient demographic import process when messages
 * are received from remote facilities through the Integrator. It provides a two-phase import
 * workflow:</p>
 * 
 * <ol>
 *   <li>Phase 1: Automatic import or user selection - When a message references a patient from
 *       a remote facility, the system attempts to match them with a local patient. If no exact
 *       match is found, potential matches are presented to the user for selection.</li>
 *   <li>Phase 2: Manual linking - If the user selects a local patient from the potential matches,
 *       the remote demographic is linked to the selected local patient record.</li>
 * </ol>
 * 
 * <p>The import process ensures that patient records are properly synchronized across facilities
 * while maintaining data integrity and avoiding duplicate records. This is critical for inter-facility
 * messaging where patients may be treated at multiple locations within a healthcare network.</p>
 * 
 * <p>Security considerations:</p>
 * <ul>
 *   <li>Requires Integrator to be enabled for the facility</li>
 *   <li>Uses logged-in user's permissions for demographic access</li>
 *   <li>Validates facility IDs and demographic numbers</li>
 * </ul>
 * 
 * <p>Request parameters:</p>
 * <ul>
 *   <li>remoteFacilityId - The ID of the remote facility</li>
 *   <li>remoteDemographicNo - The patient ID in the remote facility</li>
 *   <li>messageID - The associated message ID</li>
 *   <li>selectedDemographicNo - (Optional) User's selection from potential matches</li>
 * </ul>
 * 
 * @version 2.0
 * @since 2019
 * @see MessengerDemographicManager
 * @see ca.openosp.openo.commn.model.Demographic
 */
public class ImportDemographic2Action extends ActionSupport {
    /**
     * The HTTP request object containing form parameters and session data.
     */
    HttpServletRequest request = ServletActionContext.getRequest();
    
    /**
     * The HTTP response object for sending responses back to the client.
     * Currently not used but maintained for potential future enhancements.
     */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Manager service for handling demographic import and linking operations.
     * Provides business logic for cross-facility patient record management.
     */
    private static MessengerDemographicManager messengerDemographicManager = SpringUtils.getBean(MessengerDemographicManager.class);

    /**
     * Executes the demographic import workflow for cross-facility patient record management.
     * 
     * <p>This method implements a two-phase import process:</p>
     * 
     * <p><b>Phase 1 (Initial Import):</b> When selectedDemographicNo is null</p>
     * <ul>
     *   <li>Attempts to import the demographic from the remote facility</li>
     *   <li>If exact match found, links automatically</li>
     *   <li>If potential matches found, returns list for user selection</li>
     *   <li>If no matches found, creates new local demographic</li>
     * </ul>
     * 
     * <p><b>Phase 2 (User Selection):</b> When selectedDemographicNo is provided</p>
     * <ul>
     *   <li>Links the remote demographic with the user-selected local patient</li>
     *   <li>Creates persistent mapping between facilities</li>
     * </ul>
     * 
     * <p>The method requires the facility to have Integrator enabled for cross-facility
     * communication. Without Integrator, no import operations are performed.</p>
     * 
     * @return SUCCESS constant indicating successful execution and forward to DisplayMessages.jsp
     */
    public String execute() {
        // Extract request parameters for demographic import
        String remoteFacilityId = request.getParameter("remoteFacilityId");
        String remoteDemographicNo = request.getParameter("remoteDemographicNo");
        String messageID = request.getParameter("messageID");
        String selectedDemographicNo = request.getParameter("selectedDemographicNo");

        // Get the logged-in user's information for authorization and facility context
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Only proceed with import if Integrator is enabled for cross-facility communication
        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {

            List<Demographic> demographicList = null;

            // Phase 1: Initial import attempt when no user selection has been made
            if (selectedDemographicNo == null) {
                // Import demographic and get potential matches for user selection
                demographicList = messengerDemographicManager.importDemographic(loggedInInfo,
                        Integer.parseInt(remoteFacilityId), Integer.parseInt(remoteDemographicNo), Integer.parseInt(messageID));
            }

            // If potential matches were found, prepare them for user selection
            if (demographicList != null) {
                // Set the list of potential matches for the JSP to display
                request.setAttribute("demographicUserSelect", demographicList);
                // Preserve the remote demographic number for the selection process
                request.setAttribute("remoteDemographicNo", remoteDemographicNo);
            }

            // Phase 2: Link the selected local demographic with the remote demographic
            if (selectedDemographicNo != null) {
                // Create permanent link between local and remote patient records
                messengerDemographicManager.linkDemographicWithRemote(loggedInInfo,
                        Integer.parseInt(selectedDemographicNo), Integer.parseInt(remoteFacilityId), Integer.parseInt(remoteDemographicNo), Integer.parseInt(messageID));
            }
        }

        // Set attributes for the DisplayMessages.jsp page
        // boxType "0" indicates returning to inbox view
        request.setAttribute("boxType", "0");
        // Preserve message ID for display context
        request.setAttribute("messageID", messageID);

        return SUCCESS;
    }
}
