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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.report.reportByTemplate;

import org.owasp.encoder.Encode;

import io.github.carlos_emr.carlos.util.NativeQueryValues;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author rjonasz
 */
public class DepressionContinuityReporter implements Reporter {
    private StringBuilder rsHtml = new StringBuilder();
    private StringBuilder csv = new StringBuilder();
    private Map<String, StringBuilder> demographics = new LinkedHashMap<>();
    private Map<String, StringBuilder> csvMap = new LinkedHashMap<>();

    /**
     * Creates a new instance of DepressionContinuityReporter
     */
    public DepressionContinuityReporter() {
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public boolean generateReport(HttpServletRequest request) {
        String templateId = request.getParameter("templateId");
        ReportObject curReport = (new ReportManager()).getReportTemplateNoParam(templateId);
        Date diag_date_from = ConversionUtils.fromDateString(request.getParameter("diag_date_from"));
        Date diag_date_to = ConversionUtils.fromDateString(request.getParameter("diag_date_to"));
        Date visit_date_from = ConversionUtils.fromDateString(request.getParameter("visit_date_from"));
        Date visit_date_to = ConversionUtils.fromDateString(request.getParameter("visit_date_to"));
        String strDxCodes = request.getParameter("dxCodes:list");
        List<String> dxCodes = null;
        if (strDxCodes != null) {
            dxCodes = Arrays.asList(strDxCodes.split(","));
        }


        if (diag_date_from == null || diag_date_to == null || visit_date_from == null || visit_date_to == null || dxCodes == null) {
            rsHtml.append("All dates must be set and at least one Dx Code must be set");
            request.setAttribute("errormsg", rsHtml.toString());
            request.setAttribute("templateid", templateId);
            return false;
        }

        BillingONCHeader1Dao bDao = SpringUtils.getBean(BillingONCHeader1Dao.class);

        String cohortSQL = " -- N/A -- Migrated to JPA ";
        String apptSQL = " -- N/A -- Migrated to JPA ";

        Boolean odd = Boolean.valueOf(true);
        try {
            rsHtml = this.makeHTMLHeader();
            csv = this.makeCSVHeader();
            demographics.clear();
            csvMap.clear();
            for (Object[] o : bDao.findDemographicsAndBillingsByDxAndServiceDates(dxCodes, diag_date_from, diag_date_to)) {
                Demographic d = (Demographic) o[0];
                BillingONItem bi = (BillingONItem) o[2];
                String demographicNo = d.getDemographicNo().toString();
                String serviceDate = ConversionUtils.toDateString(bi.getServiceDate());
                demographics.computeIfAbsent(demographicNo, key -> new StringBuilder())
                        .append(addCodeEntry(demographicNo, serviceDate, bi.getDx(), odd));
                csvMap.computeIfAbsent(demographicNo, key -> new StringBuilder())
                        .append(csvCodeEntry(demographicNo, serviceDate, bi.getDx()));
            }

            addAppt(visit_date_from, visit_date_to, odd);

            rsHtml.append("</tbody></table>");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Unable to generate continuity report", e);
            request.setAttribute("errormsg", "Unable to generate the continuity report. Please try again.");
            request.setAttribute("templateid", templateId);
            return false;
        }

        String sql = cohortSQL + ";\n " + apptSQL;
        request.setAttribute("reportobject", curReport);
        request.setAttribute("resultsethtml", rsHtml.toString());
        request.setAttribute("csv", csv.toString());
        request.setAttribute("sql", sql);
        return true;
    }

    private StringBuilder addCodeEntry(String demographic_no, String service_date, String dx, Boolean odd) {
        StringBuilder html = new StringBuilder("<tr class=\"");
        if (odd) {
            html.append("reportRow1\">");
        } else {
            html.append("reportRow2\">");
        }
        odd = !odd;
        html.append("<td>" + demographic_no + "</td><td>" + service_date + "</td><td>" + Encode.forHtml(dx) + "</td>");
        html.append("<td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>");
        html.append("<td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>");
        html.append("</tr>");

        return html;
    }

    private String csvCodeEntry(String demographic_no, String service_date, String dx) {
        String csvCode = demographic_no + "," + service_date + "," + dx + ",,,,,, \n";
        return csvCode;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private void addAppt(Date from, Date to, Boolean odd) {
        OscarAppointmentDao dao = SpringUtils.getBean(OscarAppointmentDao.class);
        Set<String> setDemo = demographics.keySet();
        if (setDemo.isEmpty()) return;
        Set<String> rendered = new HashSet<>();
        for (Object[] o : dao.findAppointmentsByDemographicIds(setDemo, from, to)) {

            String p1 = ConversionUtils.toDateString(NativeQueryValues.asDate(o[0]));    // "a.appointment_date, " +
            String p2 = (String) o[1]; //"concat(pAppt.first_name, ' ', pAppt.last_name), " +
            String p3 = (String) o[2]; //"concat(pFam.first_name, ' ', pFam.last_name), " +
            String p4 = (String) o[3]; // "bi.service_code, " +
            String p5 = (String) o[4]; //"drugs.BN, " +
            String p6 = (String) o[5]; //"concat(pDrug.first_name,' ',pDrug.last_name), " +
            String p7 = String.valueOf(o[6]); //"a.demographic_no, " +
            String p8 = (String) o[7]; //"drugs.GN, " +
            String p9 = (String) o[8]; //"drugs.customName " +

            if (rendered.add(p7)) {
                rsHtml.append(demographics.get(p7));
                csv.append(csvMap.get(p7));
            }
            rsHtml.append("<tr class=\"");
            if (odd) {
                rsHtml.append("reportRow1\">");
            } else {
                rsHtml.append("reportRow2\">");
            }
            odd = !odd;
            rsHtml.append("<td>" + Encode.forHtml(p7) + "</td><td>&nbsp;</td><td>&nbsp;</td>");
            rsHtml.append("<td>").append(Encode.forHtml(p1)).append("</td><td>").append(Encode.forHtml(p2))
                    .append("</td><td>").append(Encode.forHtml(p3)).append("</td>");

            String rxName = medicationName(p5, p8, p9);
            String rxPrescriber = p6 == null ? " " : p6;

            rsHtml.append("<td>").append(Encode.forHtml(p4)).append("</td><td>").append(Encode.forHtml(rxName))
                    .append("</td><td>").append(Encode.forHtml(rxPrescriber)).append("</td>");
            rsHtml.append("</tr>");

            csv.append(p7 + ",, ");
            csv.append("," + p1 + "," + p2 + "," + p3);
            csv.append("," + p4 + "," + rxName + "," + rxPrescriber + "\n");

        }

        for (String demographicNo : setDemo) {
            if (!rendered.contains(demographicNo)) {
                rsHtml.append(demographics.get(demographicNo));
                csv.append(csvMap.get(demographicNo));
            }
        }
    }

    /** Select the stored brand, generic or custom drug name using the legacy NULL markers. */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Case-insensitive comparison of legacy SQL NULL markers, not an authorization decision")
    private static String medicationName(String brand, String generic, String custom) {
        if (brand != null && !brand.equalsIgnoreCase("null")) return brand;
        if (generic != null && !generic.equalsIgnoreCase("null")) return generic;
        return custom == null ? "" : custom;
    }

    private StringBuilder makeHTMLHeader() {
        StringBuilder html = new StringBuilder("<table class=\"reportTable\"><thead><tr>\n");
        html.append("<th class=\"reportHeader\">ID</th><th class=\"reportHeader\">Date of Code</th><th class=\"reportHeader\">Dx Code</th>"
                + "<th class=\"reportHeader\">Date of Visit</th><th class=\"reportHeader\">Provider Seen</th><th class=\"reportHeader\">MRP</th>"
                + "<th class=\"reportHeader\">Billing Code</th><th class=\"reportHeader\">Rx Name</th><th class=\"reportHeader\">Prescriber</th>");

        html.append("</tr></thead><tbody>");
        return html;
    }

    private StringBuilder makeCSVHeader() {
        StringBuilder cvs = new StringBuilder("ID,Date of Code,Dx Code,Date of Visit,Provider Seen,MRP,Billing Code,Rx Name,Prescriber\n");
        return cvs;
    }


}
