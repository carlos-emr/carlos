/**
 * Copyright (c) 2012- Centre de Medecine Integree
 * <p>
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
 * Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
 * as part of the OSCAR McMaster EMR System
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.report.pageUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.util.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.OscarDocumentCreator;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class PrintAppointmentReceipt2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();

    public PrintAppointmentReceipt2Action() {
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }


        String classpath = (String) request.getSession().getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
        if (classpath == null) {
            classpath = (String) request.getSession().getServletContext().getAttribute("com.ibm.websphere.servlet.application.classpath");
        }

        System.setProperty("jasper.reports.compile.class.path", classpath);
        String curUser_no = loggedInInfo.getLoggedInProviderNo();
        OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
        DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
        ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);

        Appointment appt = appointmentDao.find(Integer.parseInt(request.getParameter("appointment_no")));
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");
        SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String printedDateTime = dateTimeFormatter.format(new Date());
        Demographic demographic = demographicDao.getDemographic(Integer.toString(appt.getDemographicNo()));
        ProviderData provider = providerDao.findByProviderNo(appt.getProviderNo());

        ResourceBundle oscarResources;
        String DOB = "";
        String lang = "";
        if (demographic != null) {
            DOB = demographic.getFormattedDob();
            lang = StringUtils.noNull(demographic.getOfficialLanguage());
        }
        if (lang.equals("French")) {
            oscarResources = ResourceBundle.getBundle("oscarResources", Locale.FRENCH);
        } else {
            oscarResources = ResourceBundle.getBundle("oscarResources", request.getLocale());
        }

        ClinicDAO clinicDao = SpringUtils.getBean(ClinicDAO.class);
        Clinic clinic = clinicDao.getClinic();
        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterName = "";
        Boolean silentPrint = false;
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_APPOINTMENT_RECEIPT);
        if (prop != null) {
            defaultPrinterName = prop.getValue();
        }
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_APPOINTMENT_RECEIPT_SILENT_PRINT);
        if (prop != null) {
            if (prop.getValue().equalsIgnoreCase("yes")) {
                silentPrint = true;
            }
        }
        String exportPdfJavascript = null;
        if (defaultPrinterName != null && !defaultPrinterName.isEmpty()) {
            exportPdfJavascript = "var params = this.getPrintParams();"
                    + "params.pageHandling=params.constants.handling.none;"
                    + "params.printerName='" + defaultPrinterName + "';";
            if (silentPrint == true) {
                exportPdfJavascript += "params.interactive=params.constants.interactionLevel.silent;";
            }
            exportPdfJavascript += "this.print(params);";
        }

        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("clinicName", clinic.getClinicName());
        parameters.put("clinicAddress", clinic.getClinicAddress());
        parameters.put("clinicCity", clinic.getClinicCity());
        parameters.put("clinicProvince", clinic.getClinicProvince());
        parameters.put("clinicPostal", clinic.getClinicPostal());
        parameters.put("clinicPhone", clinic.getClinicPhone());
        parameters.put("clinicFax", clinic.getClinicFax());
        parameters.put("apptDate", appt.getAppointmentDate().toString());
        parameters.put("apptName", appt.getName());
        parameters.put("apptTime", timeFormatter.format(appt.getStartTime()));
        parameters.put("DOB", DOB);
        parameters.put("apptId", appt.getId().toString());
        parameters.put("printedDateTime", printedDateTime);
        parameters.put("providerName", provider.getLastName() + " " + provider.getFirstName());
        parameters.put("report.appointmentReceipt.Name", oscarResources.getString("report.appointmentReceipt.Name"));
        parameters.put("report.appointmentReceipt.Date", oscarResources.getString("report.appointmentReceipt.Date"));
        parameters.put("report.appointmentReceipt.DOB", oscarResources.getString("report.appointmentReceipt.DOB"));
        parameters.put("report.appointmentReceipt.Time", oscarResources.getString("report.appointmentReceipt.Time"));
        parameters.put("report.appointmentReceipt.With", oscarResources.getString("report.appointmentReceipt.With"));
        parameters.put("report.appointmentReceipt.Printed", oscarResources.getString("report.appointmentReceipt.Printed"));

        ServletOutputStream sos = null;
        InputStream ins = null;

        logger.error("user home: " + System.getProperty("user.home"));

        try {
            ins = new FileInputStream(PathValidationUtils.resolveTrustedPath(new File(System.getProperty("user.home") + "/AppointmentReceipt.xml")));
        } catch (FileNotFoundException | SecurityException ex1) {
            // SecurityException covers a non-canonicalizable user.home path; like a missing file it
            // falls back to the bundled default receipt template below rather than escaping uncaught.
            logger.debug("AppointmentReceipt.xml not found in user's home directory. Using default instead");
        }

        if (ins == null) {
            try {

                ins = getClass().getResourceAsStream("/oscar/oscarDemographic/AppointmentReceipt.xml");
                logger.debug("loading from : /oscar/oscarDemographic/AppointmentReceipt.xml " + ins);
            } catch (Exception ex1) {
                MiscUtils.getLogger().error("Error", ex1);
            }
        }

        try {
            sos = response.getOutputStream();
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }

        response.setHeader("Content-disposition", getHeader(response).toString());
        OscarDocumentCreator osc = new OscarDocumentCreator();
        try {
            osc.fillDocumentStream(parameters, sos, "pdf", ins, null, exportPdfJavascript);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            try {
                if (ins != null) {
                    ins.close();
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }
        // This action streams the PDF directly and its Struts mapping has no success result.
        // Returning SUCCESS makes Struts render the global error page over the PDF as "0".
        return NONE;
    }

    private StringBuilder getHeader(HttpServletResponse response) {
        StringBuilder strHeader = new StringBuilder();
        strHeader.append("receipt_");
        strHeader.append(".pdf");
        response.setHeader("Cache-Control", "max-age=0");
        response.setDateHeader("Expires", 0);
        response.setContentType("application/pdf");
        StringBuilder sbContentDispValue = new StringBuilder();
        sbContentDispValue.append("inline; filename="); //inline - display
        sbContentDispValue.append(strHeader);
        return sbContentDispValue;
    }
}
