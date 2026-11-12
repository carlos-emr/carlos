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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementCSSLocation;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

import org.owasp.encoder.Encode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class EctDeleteMeasurementStyleSheet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws ServletException, IOException {
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {

            MeasurementGroupStyleDao dao = SpringUtils.getBean(MeasurementGroupStyleDao.class);
            MeasurementCSSLocationDao lDao = SpringUtils.getBean(MeasurementCSSLocationDao.class);

            if (deleteCheckbox != null) {
                // Refuse the whole request before deleting anything if any selected
                // style sheet is still mapped by a measurement group.
                for (String id : deleteCheckbox) {
                    Integer cssId = ConversionUtils.fromIntString(id);
                    MeasurementCSSLocation location = lDao.find(cssId);
                    if (location != null && !dao.findByCssId(cssId).isEmpty()) {
                        List<String> errors = new ArrayList<>();
                        // Error bundle contains embedded <li>...</li> and the JSP renders
                        // actionErrors unencoded, so HTML-encode the DB-sourced {0} value
                        // before substitution to prevent breakout of the list-item context.
                        errors.add(getText("error.encounter.Measurements.cannotDeleteStyleSheet",
                                new String[]{Encode.forHtml(location.getLocation())}));
                        request.setAttribute("actionErrors", errors);
                        return "error";
                    }
                }

                for (String id : deleteCheckbox) {
                    Integer cssId = ConversionUtils.fromIntString(id);
                    MeasurementCSSLocation location = lDao.find(cssId);
                    if (location == null) {
                        // Orphaned group mappings whose style sheet is already gone.
                        for (MeasurementGroupStyle style : dao.findByCssId(cssId)) {
                            // The query's DAO transaction has ended. Reload by ID
                            // inside remove's transaction instead of deleting its detached result.
                            dao.remove(style.getId());
                        }
                    } else {
                        // No group maps it (checked above): remove the style sheet itself.
                        lDao.remove(location.getId());
                        deleteStoredFile(location.getLocation());
                    }
                }
            }

            return SUCCESS;

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }
    }

    /**
     * Best-effort removal of the uploaded CSS file so the same name can be uploaded again
     * ({@code EctAddMeasurementStyleSheet2Action} refuses to overwrite an existing file).
     * The name comes from the database but is still validated as a path component.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: the directory is trusted configuration and the file name is validated
    // as a single path component and re-checked against that directory by PathValidationUtils below.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "directory from trusted configuration; file name validated with PathValidationUtils")
    private void deleteStoredFile(String location) {
        try {
            String uploadPath = CarlosProperties.getInstance().getProperty("oscarMeasurement_css_upload_path");
            if (location == null || uploadPath == null || uploadPath.trim().isEmpty()) {
                return;
            }
            File uploadDir = new File(uploadPath);
            File target = PathValidationUtils.validatePath(
                    PathValidationUtils.validatePathComponent(location, "styleSheet"), uploadDir);
            Files.deleteIfExists(target.toPath());
        } catch (IOException | RuntimeException e) {
            MiscUtils.getLogger().warn("Could not remove uploaded measurement style sheet file", e);
        }
    }

    private String[] deleteCheckbox;

    public String[] getDeleteCheckbox() {
        return deleteCheckbox;
    }

    @StrutsParameter
    public void setDeleteCheckbox(String[] deleteCheckbox) {
        this.deleteCheckbox = deleteCheckbox;
    }
}
