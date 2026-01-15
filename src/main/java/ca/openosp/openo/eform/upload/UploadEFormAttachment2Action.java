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

package ca.openosp.openo.eform.upload;

import java.io.IOException;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.CtlDocumentDao;
import ca.openosp.openo.commn.dao.DocumentDao;
import ca.openosp.openo.commn.model.CtlDocument;
import ca.openosp.openo.commn.model.CtlDocumentPK;
import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

public class UploadEFormAttachment2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }
        String docFileName = this.getUploadFileName();
        try {
            Date eformUploadDate = new Date();
            String user = (String) request.getSession().getAttribute("user");
            String demographicNo = request.getParameter("efmdemographic_no");

            Document document = new Document();
            document.setDocdesc("EForm attachment document");
            document.setContenttype("image/jpeg");
            document.setDocfilename(docFileName);
            document.setDoccreator(user);
            document.setPublic1(Byte.valueOf("0"));
            document.setStatus('A');
            document.setObservationdate(eformUploadDate);
            document.setUpdatedatetime(eformUploadDate);
            document.setDoctype("others");

            DocumentDao documentDao = (DocumentDao) SpringUtils.getBean(DocumentDao.class);
            CtlDocumentDao ctlDocumentDao = SpringUtils.getBean(CtlDocumentDao.class);
            documentDao.persist(document);

            CtlDocumentPK ctlDocumentPK = new CtlDocumentPK(Integer.parseInt("" + document.getId()), "demographic");

            CtlDocument ctlDocument = new CtlDocument();
            ctlDocument.setId(ctlDocumentPK);
            ctlDocument.getId().setModuleId(Integer.parseInt(demographicNo));
            ctlDocument.setStatus("A");
            ctlDocumentDao.persist(ctlDocument);


            String successMsg = "<div id=\"status\">success</div> <div id=\"message\">Uploaded Successfully</div> <div id=\"fileName\">" + Encode.forHtml(docFileName) + "</div> <div id=\"docId\">" + document.getId() + "</div>";
            response.getOutputStream().write(successMsg.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            String errorMsg = "<div id=\"error\">error</div><div id=\"message\">Error in file upload</div>";
            try {
                response.getOutputStream().write(errorMsg.getBytes());
                response.getOutputStream().flush();
                response.getOutputStream().close();
            } catch (IOException e1) {
                //ignore
            }
        }

        return null;
    }
    private String uploadFileName = null;

    public String getUploadFileName() {
        return uploadFileName;
    }

    public void setUploadFileName(String uploadFileName) {
        this.uploadFileName = uploadFileName;
    }
}
