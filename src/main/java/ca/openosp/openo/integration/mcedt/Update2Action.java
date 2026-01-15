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

import ca.ontario.health.edt.*;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.util.ConversionUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.List;

import static ca.openosp.openo.integration.mcedt.ActionUtils.*;
import static ca.openosp.openo.integration.mcedt.McedtConstants.SESSION_KEY_UPLOAD_DETAILS;

public class Update2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("addUpdateRequest".equals(method)) {
            return addUpdateRequest();
        } else if ("cancel".equals(method)) {
            return cancel();
        } else if ("sendUpdateRequest".equals(method)) {
            return sendUpdateRequest();
        }
        List<BigInteger> resourceIds = getResourceIds(request);

        Detail details = (Detail) request.getSession().getAttribute(SESSION_KEY_UPLOAD_DETAILS);
        if (details == null) {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
            try {
                details = delegate.info(resourceIds);
            } catch (Exception e) {
                logger.error("Error loading " + resourceIds, e);
                addActionError(getText("updateAction.unspecified.errorLoading"));

                return "initial";
            }
            for (DetailData d : details.getData()) {
                d.setModifyTimestamp(null);
            }
            request.getSession().setAttribute(SESSION_KEY_UPLOAD_DETAILS, details);
        }
        return "initial";
    }

    public String addUpdateRequest() {
        List<UpdateRequest> updates = getUpdateList(request);

        UpdateRequest update = this.toUpdateRequest();
        updates.add(update);

        Detail details = (Detail) request.getSession().getAttribute(SESSION_KEY_UPLOAD_DETAILS);
        for (DetailData d : details.getData()) {
            if (d.getResourceID().equals(update.getResourceID())) {
                markUpdated(d);
            }
        }
        return "initial";
    }

    private void markUpdated(DetailData d) {
        d.setModifyTimestamp(new DummyXMLGregorianCalendar());
    }

    public String cancel() {
        clearUpdateList(request);
        request.getSession().removeAttribute(SESSION_KEY_UPLOAD_DETAILS);
        return "cancel";
    }

    public String sendUpdateRequest() {

        List<UpdateRequest> updates = getUpdateList(request);
        if (updates.isEmpty()) {
            addActionError(getText("updateAction.sendUpdateRequest.emptyUpdates"));
            return SUCCESS;
        }

        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
            ResourceResult result = delegate.update(updates);
            clearUpdateList(request);
            addActionMessage(getText("updateAction.sendUpdateRequest.success", new String[]{McedtMessageCreator.resourceResultToString(result)}));

            cancel();
        } catch (Exception e) {
            logger.error("Unable to update", e);

            request.setAttribute("message", "Error updating: " + e.getMessage());
            addActionError(getText("updateAction.sendUpdateRequest.failure", new String[]{McedtMessageCreator.exceptionToString(e)}));
        }

        return SUCCESS;
    }

    private String resourceId;
    private File content;

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public File getContent() {
        return content;
    }

    public void setContent(File content) {
        this.content = content;
    }

    public UpdateRequest toUpdateRequest() {
        UpdateRequest result = new UpdateRequest();
        result.setResourceID(BigInteger.valueOf(ConversionUtils.fromIntString(resourceId)));
        try {
            result.setContent(Files.readAllBytes(content.toPath()));
        } catch (Exception e) {
            throw new RuntimeException("Unable to read upload data", e);
        }
        return result;
    }
}
