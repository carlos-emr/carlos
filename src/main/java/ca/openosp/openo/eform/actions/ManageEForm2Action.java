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


package ca.openosp.openo.eform.actions;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.eform.EFormExportZip;
import ca.openosp.openo.eform.data.EForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManageEForm2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws Exception {
        if ("importEForm".equals(request.getParameter("method"))) {
            return importEForm();
        }
        return exportEForm();
    }

    public String exportEForm() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "r", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        String fid = request.getParameter("fid");
        MiscUtils.getLogger().debug("fid: " + fid);
        response.setContentType("application/zip");  //octet-stream
        EForm eForm = new EForm(fid, "1");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + eForm.getFormName().replaceAll("\\s", fid) + ".zip\"");
        EFormExportZip eFormExportZip = new EFormExportZip();
        List<EForm> eForms = new ArrayList<EForm>();
        eForms.add(eForm);
        eFormExportZip.exportForms(eForms, response.getOutputStream());
        return null;
    }

    public String importEForm() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        List<String> errors = Collections.emptyList();
        try (InputStream zippedFormStream = Files.newInputStream(zippedForm.toPath())) {
            request.setAttribute("input", "import");
            EFormExportZip eFormExportZip = new EFormExportZip();
            errors = eFormExportZip.importForm(zippedFormStream);
        }
        if (!errors.isEmpty()) {
            request.setAttribute("importErrors", errors);
            return "fail";
        } else {
            request.setAttribute("status", "success");
            return SUCCESS;
        }
    }

    private File zippedForm; // 接收上传的文件

    public File getZippedForm() {
        return zippedForm;
    }

    public void setZippedForm(File zippedForm) {
        this.zippedForm = zippedForm;
    }
}
