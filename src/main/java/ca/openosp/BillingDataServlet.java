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


package ca.openosp;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import ca.openosp.openo.utility.MiscUtils;

/**
 * Servlet for processing billing data form submissions.
 * 
 * <p>This servlet handles HTTP POST requests containing billing information
 * and populates a {@link BillingDataBean} with the request parameters.</p>
 * 
 * <p>Expected request parameters:</p>
 * <ul>
 *   <li>billing_no - Billing transaction number</li>
 *   <li>hin - Health insurance number</li>
 *   <li>visittype - Type of visit</li>
 *   <li>visitdate - Date of visit</li>
 *   <li>status - Billing status</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This is a legacy servlet. Modern applications should
 * use Spring MVC controllers or JAX-RS resources instead.</p>
 * 
 * @see BillingDataBean
 */
public class BillingDataServlet extends HttpServlet implements java.io.Serializable {

    /**
     * Processes incoming POST requests for billing data.
     * Extracts billing information from request parameters and populates a BillingDataBean.
     *
     * @param request the HTTP servlet request containing billing data parameters
     * @param response the HTTP servlet response
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    public void doPost(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws IOException, ServletException {
        try {

//          instantiate the beans and store them so they can be accessed by the called page
            BillingDataBean billingDataBean = new BillingDataBean();

//      	 javax.servlet.ServletRequest.setAttribute("billingDataBean", billingDataBean);


            billingDataBean.setBilling_no(request.getParameter("billing_no"));


            billingDataBean.setHin(request.getParameter("hin"));


            billingDataBean.setVisittype(request.getParameter("visittype"));


            billingDataBean.setVisitdate(request.getParameter("visitdate"));

//          Initialize the bean state property from the parameters
            billingDataBean.setStatus(request.getParameter("status"));

//          Initialize the bean zip property from the parameters
//         billingDataBean.setZip(request.getParameter("zip"));

//          Initialize the bean phone property from the parameters
//         billingDataBean.setPhone(request.getParameter("phone"));

//          Initialize the bean email property from the parameters
//         billingDataBean.setEmail(request.getParameter("email"));

// Call the output page.

            //	 RequestDispatcher dispatch = getServletContext().getRequestDispatcher(getInitParameter("result_page"));
            //	 dispatch.forward(request, response);


            response.sendRedirect(getServletConfig().getInitParameter("result_page"));
        } catch (Exception theException) {
            MiscUtils.getLogger().error("Error", theException);
        }
    }
}
