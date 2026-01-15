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

import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.lab.ca.all.parsers.Factory;
import org.apache.commons.codec.binary.Base64;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.ca.all.parsers.MessageHandler;
import ca.openosp.openo.lab.ca.all.parsers.PATHL7Handler;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DownloadEmbeddedDocumentFromLab2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        String labNo = request.getParameter("labNo");
        String segment = request.getParameter("segment");
        String group = request.getParameter("group");
        String legacy = request.getParameter("legacy");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        //String hl7 = ca.openosp.openo.lab.ca.all.parsers.Factory.getHL7Body(labNo);

        MessageHandler handler = Factory.getHandler(labNo);

        String result = null;
        if (legacy != null && "true".equals(legacy)) {
            result = ((PATHL7Handler) handler).getLegacyOBXResult(Integer.parseInt(segment), Integer.parseInt(group));
        } else {
            result = handler.getOBXResult(Integer.parseInt(segment), Integer.parseInt(group));
        }

        byte[] decodedData = Base64.decodeBase64(result);

        response.setContentType("application/pdf"); // Check http://www.iana.org/assignments/media-types for all types. Use if necessary ServletContext#getMimeType() for auto-detection based on filename.
        response.setHeader("Content-disposition", "attachment; filename=\"Lab-" + labNo + ".pdf\""); // The Save As popup magic is done here. You can give it any filename you want, this only won't work in MSIE, it will use current request URL as filename instead.

        // Write file to response.
        OutputStream output = response.getOutputStream();
        output.write(decodedData);
        output.close();


        return null;
    }
}
