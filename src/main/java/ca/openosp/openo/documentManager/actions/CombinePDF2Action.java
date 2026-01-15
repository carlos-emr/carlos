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


package ca.openosp.openo.documentManager.actions;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.util.ConcatPDF;
import ca.openosp.openo.util.UtilDateUtilities;

/**
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class CombinePDF2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_doc)");
        }

        String[] files = request.getParameterValues("docNo");
        String ContentDisposition = request.getParameter("ContentDisposition");
        ArrayList<Object> alist = new ArrayList<Object>();
        if (files != null) {
            MiscUtils.getLogger().debug("size = " + files.length);
            EDocUtil docData = new EDocUtil();
            String path = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
            Path filePath;
            for (int i = 0; i < files.length; i++) {
                String filename = docData.getDocumentName(files[i]);
                filePath = Paths.get(path, filename);
                alist.add(filePath.toAbsolutePath().toString());
            }
            if (alist.size() > 0) {
                response.setContentType("application/pdf");  //octet-stream
                if (ContentDisposition != null && ContentDisposition.equals("inline")) {
                    response.setHeader("Transfer-Encoding", "chunked");
                    response.setHeader("Cache-Control", "cache, must-revalidate"); // IE workaround
                    response.setHeader("Pragma", "public"); // IE workaround
                    response.setHeader("Content-Disposition", "inline; filename=\"combinedPDF-" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
                } else {

                    response.setHeader("Content-Disposition", "attachment; filename=\"combinedPDF-" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
                }
                try {
                    ConcatPDF.concat(alist, response.getOutputStream());
                } catch (IOException ex) {
                    MiscUtils.getLogger().error("Error", ex);
                }
                return null;
            }
        }
        return SUCCESS;
    }

    /**
     * Creates a new instance of CombinePDF2Action
     */
    public CombinePDF2Action() {
    }

}
