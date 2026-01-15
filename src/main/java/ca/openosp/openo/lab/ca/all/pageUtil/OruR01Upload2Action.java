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


package ca.openosp.openo.lab.ca.all.pageUtil;

import ca.uhn.hl7v2.model.v26.message.ORU_R01;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.ClinicDAO;
import ca.openosp.openo.commn.dao.ProfessionalSpecialistDao;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.OruR01;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.OruR01.ObservationData;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.SendingUtils;
import ca.openosp.openo.commn.model.Clinic;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.utility.WebUtils;
import ca.openosp.openo.util.DateUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.GregorianCalendar;

public class OruR01Upload2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        try {
            File formFile = form.getUploadFile();
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            
            // Validate file before processing
            if (formFile == null) {
                throw new IllegalArgumentException("No file provided");
            }

            // SECURITY: Validate the uploaded file is in temp directory FIRST
            // This must happen before any file operations to prevent path traversal
            if (!PathValidationUtils.isInAllowedTempDirectory(formFile)) {
                logger.error("Attempted path traversal detected - file outside temp directory: " + formFile.getPath());
                throw new SecurityException("Access denied: file outside permitted directory");
            }

            // Now safe to perform file checks after security validation
            if (!formFile.exists()) {
                throw new IllegalArgumentException("File does not exist");
            }

            if (!formFile.isFile()) {
                throw new SecurityException("Path is not a regular file");
            }

            // Ensure file is readable
            if (!formFile.canRead()) {
                throw new SecurityException("File is not readable");
            }

            Demographic demographic = getDemographicObject(form);

            ObservationData observationData = new ObservationData();
            observationData.subject = form.getSubject();
            observationData.textMessage = form.getTextMessage();
            observationData.binaryDataFileName = formFile.getName();
            // Re-validate at point of use for static analysis visibility, then read file
            File validatedFormFile = PathValidationUtils.validateUpload(formFile);
            observationData.binaryData = Files.readAllBytes(validatedFormFile.toPath());

            Provider sendingProvider = loggedInInfo.getLoggedInProvider();

            ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
            ProfessionalSpecialist professionalSpecialist = professionalSpecialistDao.find(form.getProfessionalSpecialistId());

            ClinicDAO clinicDAO = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);
            Clinic clinic = clinicDAO.getClinic();

            ORU_R01 hl7Message = OruR01.makeOruR01(clinic, demographic, observationData, sendingProvider, professionalSpecialist);

            int statusCode = SendingUtils.send(loggedInInfo, hl7Message, professionalSpecialist);

            if (HttpServletResponse.SC_OK == statusCode)
                WebUtils.addInfoMessage(request.getSession(), "Data successfully send.");
            else throw (new ServletException("Error sending data. response code=" + statusCode));

        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            WebUtils.addErrorMessage(request.getSession(), "An error occurred while sending this data, please try again or send this manually.");
        }

        return "result";
    }


    private static Demographic getDemographicObject(OruR01Upload2Form oruR01UploadForm) {
        Demographic demographic = new Demographic();

        demographic.setLastName(oruR01UploadForm.getClientLastName());
        demographic.setFirstName(oruR01UploadForm.getClientFirstName());
        demographic.setSex(oruR01UploadForm.getClientGender());
        demographic.setHin(oruR01UploadForm.getClientHealthNumber());

        try {
            String temp = StringUtils.trimToNull(oruR01UploadForm.getClientBirthDay());
            if (temp != null) {
                GregorianCalendar gregorianCalendar = DateUtils.toGregorianCalendarDate(temp);
                demographic.setBirthDay(gregorianCalendar);
            }
        } catch (Exception e) {
            logger.warn("Error parsing date:" + oruR01UploadForm.getClientBirthDay(), e);
        }

        return (demographic);
    }

    private OruR01Upload2Form form;

    public OruR01Upload2Form getForm() {
        return form;
    }

    public void setForm(OruR01Upload2Form form) {
        this.form = form;
    }
}
