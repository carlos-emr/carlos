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


package ca.openosp.openo.providers.pageUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.LoggedInInfo;

import ca.openosp.openo.providers.data.ProviderColourUpdater;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for updating a healthcare provider's display colour preference.
 * <p>
 * This action allows providers to customize their calendar/schedule display colour
 * for better visual identification in multi-provider schedules and appointment views.
 * The colour preference is stored in the provider's profile and used throughout
 * the application for consistent visual representation.
 * <p>
 * The colour property is automatically populated by Struts 2 property injection
 * from the form submission.
 *
 * @since 2001-11-01
 */
public class ProEditColour2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    /**
     * Updates the logged-in provider's display colour preference.
     * <p>
     * This method:
     * <ul>
     * <li>Retrieves the logged-in provider number from the session</li>
     * <li>Validates that a provider is logged in (returns "eject" if not)</li>
     * <li>Updates the provider's colour preference using ProviderColourUpdater</li>
     * <li>Sets status attribute to "complete" on success</li>
     * <li>Returns success or error result based on update outcome</li>
     * </ul>
     * <p>
     * The colour property is populated automatically by Struts 2 from the
     * form submission before this method is called.
     *
     * Expected request parameters (via Struts 2 property injection):
     * <ul>
     * <li>colour - String hexadecimal colour code (e.g., "#FF5733")</li>
     * </ul>
     *
     * @return String "success" if colour updated successfully, "error" if update failed,
     *         or "eject" if no provider is logged in
     * @throws Exception if colour update encounters an error
     */
    public String execute()
            throws Exception {
        String forward;
        String providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        if (providerNo == null)
            return "eject";

        ProviderColourUpdater colourUpdater = new ProviderColourUpdater(providerNo);
        if (colourUpdater.setColour(colour)) {
            request.setAttribute("status", new String("complete"));
            forward = SUCCESS;
        } else {
            forward = ERROR;
        }
        return forward;
    }

    /**
     * The display colour code for the provider's calendar/schedule.
     * <p>
     * This property is automatically populated by Struts 2 from the
     * "colour" form parameter. Typically a hexadecimal colour code.
     */
    private String colour;

    /**
     * Gets the provider's display colour code.
     *
     * @return String colour code (e.g., "#FF5733")
     */
    public String getColour() {
        return colour;
    }

    /**
     * Sets the provider's display colour code.
     * <p>
     * Called automatically by Struts 2 when the colour parameter
     * is submitted in the form.
     *
     * @param colour String colour code to set
     */
    public void setColour(String colour) {
        this.colour = colour;
    }
}
