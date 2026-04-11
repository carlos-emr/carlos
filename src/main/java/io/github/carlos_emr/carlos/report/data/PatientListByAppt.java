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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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

    private static final Logger log = MiscUtils.getLogger();

    private static final long serialVersionUID = 1L;

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            response.setContentType("text/plain; charset=UTF-8");
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

            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    response.getOutputStream(), StandardCharsets.UTF_8), true)) {

                for (Object[] o : dao.findPatientAppointments(drNo, from, to)) {
                    Demographic d = (Demographic) o[0];
                    Appointment a = (Appointment) o[1];
                    Provider p = (Provider) o[2];

                    // nosemgrep: no-direct-response-writer -- text/plain CSV download with Content-Disposition: attachment, not HTML
                    pw.print(escapeCsv(d.getLastName()) + ",");
                    pw.print(escapeCsv(d.getFirstName()) + ",");
                    pw.print(escapeCsv(d.getPhone()) + ",");
                    pw.print(escapeCsv(d.getPhone2()) + ",");
                    pw.print(ConversionUtils.toTimeString(a.getStartTime()) + ",");
                    pw.print(ConversionUtils.toDateString(a.getAppointmentDate()) + ",");
                    pw.print(escapeCsv(a.getType().replaceAll("\r\n", "")) + ",");
                    pw.print(escapeCsv(p.getFirstName() + " " + p.getLastName()) + ",");
                    pw.print(escapeCsv(a.getLocation()));
                    pw.print("\n");
                }
                pw.println("");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in PatientListByAppt", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }

    /**
     * Escapes a value for RFC 4180 CSV output. Wraps the value in double-quotes
     * if it contains commas, double-quotes, or newlines, and escapes embedded
     * double-quotes by doubling them.
     *
     * @param value the raw field value; null is treated as an empty string
     * @return the RFC 4180 escaped field value
     */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
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
