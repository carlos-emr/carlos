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
package ca.openosp.openo.integration.mcedt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.util.ConversionUtils;
import ca.ontario.health.edt.EDTDelegate;
import ca.ontario.health.edt.ResourceResult;
import ca.ontario.health.edt.UploadData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class Upload2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("cancelUpload".equals(method)) {
            return cancelUpload();
        } else if ("addNew".equals(method)) {
            return addNew();
        } else if ("removeSelected".equals(method)) {
            return removeSelected();
        } else if ("uploadToMcedt".equals(method)) {
            return uploadToMcedt();
        } 
        return SUCCESS;
    }

    public String cancelUpload() {
        clearSession(request);

        addActionMessage(getText("global.cancel"));

        return "cancel";
    }

    private void clearSession(HttpServletRequest request) {
        for (String attrName : new String[]{McedtConstants.SESSION_KEY_RESOURCE_LIST,
                McedtConstants.SESSION_KEY_MCEDT_UPLOADS}) {
            request.getSession().removeAttribute(attrName);
        }
    }

    public String addNew() {
        return "addNew";
    }

    public String removeSelected() {

        List<UploadData> uploads = ActionUtils.getUploadList(request.getSession());

        List<Integer> indices = getIndices(request);
        java.util.Collections.sort(indices, Collections.reverseOrder());
        for (Integer i : indices) {
            if (i < uploads.size()) {
                uploads.remove(i.intValue());
            }
        }
        return SUCCESS;
    }

    private List<Integer> getIndices(HttpServletRequest request) {
        List<Integer> result = new ArrayList<Integer>();
        for (String resource : request.getParameterValues("resourceId")) {
            Integer i = ConversionUtils.fromIntString(resource);
            if (i != null) {
                result.add(i);
            }
        }
        return result;
    }

    public String uploadToMcedt() {

        List<UploadData> uploads = ActionUtils.getUploadList(request.getSession());

        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
            ResourceResult result = delegate.upload(uploads);

            clearSession(request);

            addActionMessage(getText("resourceAction.submit.success", new String[]{McedtMessageCreator.resourceResultToString(result)}));

            return "done";
        } catch (Exception e) {
            logger.error("Unable to upload to MCEDT", e);
            addActionMessage(getText("resourceAction.submit.failure", new String[]{McedtMessageCreator.exceptionToString(e)}));
        }

        return SUCCESS;
    }

}
