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

package io.github.carlos_emr.carlos.report.data;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

public class PatientListByAppt extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();
    private static final long serialVersionUID = 1L;

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) {
        try {
            response.setContentType("plain/text");
            response.setHeader("Content-disposition", "attachment; filename=patientlist.txt");

            String drNo = request.getParameter("provider_no");
            // clear dr no value for all doc's
            if (drNo != null && drNo.equals("all")) {
                drNo = null;
            }
            String datefrom = request.getParameter("date_from");
            String dateto = request.getParameter("date_to");

            Date from = datefrom != null ? ConversionUtils.fromDateString(datefrom) : null;
            Date to = dateto != null ? ConversionUtils.fromDateString(dateto) : null;

            OscarAppointmentDao dao = SpringUtils.getBean(OscarAppointmentDao.class);

            PrintStream ps = new PrintStream(response.getOutputStream());

            for (Object[] o : dao.findPatientAppointments(drNo, from, to)) {
                Demographic d = (Demographic) o[0];
                Appointment a = (Appointment) o[1];
                Provider p = (Provider) o[2];

                ps.print(d.getLastName() + ",");
                ps.print(d.getFirstName() + ",");
                ps.print(d.getPhone() + ",");
                ps.print(d.getPhone2() + ",");
                ps.print(ConversionUtils.toTimeString(a.getStartTime()) + ",");
                ps.print(ConversionUtils.toDateString(a.getAppointmentDate()) + ",");
                ps.print(a.getType().replaceAll("\r\n", "") + ",");
                ps.print(p.getFirstName() + " ");
                ps.print(p.getLastName() + ",");
                ps.print(a.getLocation());
                ps.print("\n");
            }
            ps.println("");
        } catch (Exception e) {
            logger.error("Error processing patient list by appointment request for {}", request.getRequestURI(), e);
            if (!response.isCommitted()) {
                try {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred processing your request.");
                } catch (IOException ioe) {
                    logger.error("Failed to send error response", ioe);
                }
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Short description";
    }
    // </editor-fold>
}
