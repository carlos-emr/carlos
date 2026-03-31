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

package io.github.carlos_emr.carlos.messenger.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Struts2 action stub for the demographic import page.
 *
 * <p>This action previously handled cross-facility patient demographic import through
 * the removed Integrator system. It now serves as a no-op stub that preserves the
 * request routing, setting message display attributes and returning SUCCESS.</p>
 *
 * <p>Request parameters:</p>
 * <ul>
 *   <li>messageID - The associated message ID</li>
 * </ul>
 *
 * @version 3.0
 * @since 2019
 */
public class ImportDemographic2Action extends ActionSupport {
    private static final Logger logger = LogManager.getLogger(ImportDemographic2Action.class);

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
     * Sets message display attributes and returns SUCCESS.
     *
     * <p>Cross-facility demographic import functionality has been removed along with
     * the Integrator system. This method now only preserves the message ID and box type
     * for the DisplayMessages.jsp forward.</p>
     *
     * @return SUCCESS constant indicating successful execution and forward to DisplayMessages.jsp
     */
    public String execute() {
        logger.warn("ImportDemographic2Action invoked but cross-facility demographic import has been removed");
        String messageID = request.getParameter("messageID");

        request.setAttribute("boxType", "0");
        request.setAttribute("messageID", messageID);

        return SUCCESS;
    }
}
