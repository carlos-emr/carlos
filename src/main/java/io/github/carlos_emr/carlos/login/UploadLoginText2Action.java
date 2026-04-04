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


package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.service.AcceptableUseAgreementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import io.github.carlos_emr.carlos.utility.LogSanitizer;

public class UploadLoginText2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger _logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        InputStream fis = null;
        FileOutputStream fos = null;
        boolean error = false;

        String validDurationNumber = request.getParameter("validDurationNumber"); // verify it's a number
        String validDurationPeriod = request.getParameter("validDurationPeriod"); //verify it's one of these year month weeks days
        String validForever = request.getParameter("validForever");
        String foreverFrom = request.getParameter("foreverFrom");

        _logger.debug("validDurationNumber={} validDurationPeriod={} validForever={} foreverFrom={}", LogSanitizer.sanitize(validDurationNumber), LogSanitizer.sanitize(validDurationPeriod), LogSanitizer.sanitize(validForever), LogSanitizer.sanitize(foreverFrom));

        PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);
        Property prop = null;

        if (validForever != null && validForever.equals("forever")) {
            prop = new Property();
            prop.setName("aua_valid_from");
            prop.setValue(foreverFrom);
        } else { //time period was selected
            try {
                Integer.parseInt(validDurationNumber);
            } catch (Exception e) {
                _logger.error("Not an Int:{}", LogSanitizer.sanitize(validDurationNumber), e);
            }

            if (validDurationPeriod != null && ("year".equals(validDurationPeriod) || "month".equals(validDurationPeriod) || "weeks".equals(validDurationPeriod) || "days".equals(validDurationPeriod))) {
                prop = new Property();
                prop.setName("aua_valid_duration");
                prop.setValue(validDurationNumber + " " + validDurationPeriod);
            } else {
                _logger.error("Not a valid Period :{}", LogSanitizer.sanitize(validDurationPeriod));
            }
        }

        if (prop != null) {
            //Check to see if prop is still the same as last time.
            Property latestProperty = AcceptableUseAgreementManager.findLatestProperty();
            if (latestProperty == null || !prop.getValue().equals(latestProperty.getValue())) {
                propertyDao.persist(prop);
            } else {
                _logger.debug("No need to update. Same AcceptableUse Property as it was before");
            }
        }


        try {
            if (importFile.getName().length() > 0) {
                fis = Files.newInputStream(importFile.toPath());
                String savePath = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR") + "/OSCARloginText.txt";
                fos = new FileOutputStream(savePath);
                byte[] buf = new byte[128 * 1024];
                int i = 0;
                while ((i = fis.read(buf)) != -1) {
                    fos.write(buf, 0, i);
                }
                error = false;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            error = true;
        }

        request.setAttribute("error", error);
        return SUCCESS;
    }

    private File importFile;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.importFile = PathValidationUtils.validateUpload(new File(uploaded.getAbsolutePath()));
        }
    }

    public File getImportFile() {
        return importFile;
    }

    @StrutsParameter
    public void setImportFile(File importFile) {
        this.importFile = importFile;
    }
}
