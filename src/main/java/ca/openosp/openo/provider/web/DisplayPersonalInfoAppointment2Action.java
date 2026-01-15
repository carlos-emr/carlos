//CHECKSTYLE:OFF
/**
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
 */


package ca.openosp.openo.provider.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Toggles variable for displaying reason on appointment page
 *
 * @author rjonasz
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DisplayPersonalInfoAppointment2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    /**
     * Creates a new instance of DisplayPersonalInfoAppointment2Action
     */
    public DisplayPersonalInfoAppointment2Action() {
    }

    public String execute() {
        return toggle();
    }

    public String toggle() {

        Boolean showPersonal = (Boolean) request.getSession().getAttribute("showPersonal");

        if (showPersonal == null) {
            showPersonal = true;
        } else {
            showPersonal = !showPersonal;
        }

        request.getSession().setAttribute("showPersonal", showPersonal);

        return null;
    }

}
