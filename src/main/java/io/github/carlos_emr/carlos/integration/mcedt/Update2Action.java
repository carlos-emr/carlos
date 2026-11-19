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
package io.github.carlos_emr.carlos.integration.mcedt;

import ca.ontario.health.edt.*;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static io.github.carlos_emr.carlos.integration.mcedt.ActionUtils.*;
import static io.github.carlos_emr.carlos.integration.mcedt.McedtConstants.SESSION_KEY_UPLOAD_DETAILS;

public class Update2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws Exception {
        McedtSecurity.requireRead(request);
        String method = request.getParameter("method");
        if ("addUpdateRequest".equals(method) || "sendUpdateRequest".equals(method) || "cancel".equals(method)) {
            McedtSecurity.requireWrite(request);
            McedtSecurity.requirePost(request);
        }
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
            request.getSession().setAttribute(SESSION_KEY_UPLOAD_DETAILS, details); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- MCEDT resource list from EDT service response
        }
        return "initial";
    }

    public String addUpdateRequest() {
        if (content == null) {
            // No file reached the action: the form was sent without one, or the upload was refused.
            addActionError(getText("updateAction.addUpdateRequest.missingFile"));
            return "initial";
        }
        // Only a resource on the list this page loaded may be replaced. A missing list means the
        // page's session state is gone; nothing is queued in either case.
        Detail details = (Detail) request.getSession().getAttribute(SESSION_KEY_UPLOAD_DETAILS);
        BigInteger requested = BigInteger.valueOf(ConversionUtils.fromIntString(resourceId));
        List<DetailData> listed = new ArrayList<>();
        if (details != null) {
            for (DetailData d : details.getData()) {
                if (requested.equals(d.getResourceID())) {
                    listed.add(d);
                }
            }
        }
        if (listed.isEmpty()) {
            addActionError(getText("updateAction.addUpdateRequest.unknownResource"));
            return "initial";
        }

        UpdateRequest update = this.toUpdateRequest();
        List<UpdateRequest> updates = getUpdateList(request);
        // A repeated add for the same resource (a retry, double click or second tab) replaces the
        // queued file instead of sending two updates for one resource.
        updates.removeIf(queued -> requested.equals(queued.getResourceID()));
        updates.add(update);
        listed.forEach(this::markUpdated);
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

    @StrutsParameter
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public File getContent() {
        return content;
    }

    /**
     * Sets the replacement file directly (tests and {@link #withUploadedFiles}). Deliberately not a
     * {@code @StrutsParameter}: Struts 7 never binds an uploaded file from request parameters, and a
     * request value must never name a server-side file.
     */
    public void setContent(File content) {
        this.content = content;
    }

    /**
     * Receives the Update Upload form's {@code content} file. Struts 7's {@code actionFileUpload}
     * interceptor hands uploads only to {@link UploadedFilesAware} actions; this action relied on the
     * removed parameter binding, so every MCEDT update failed with no file (#2212). The temp file is
     * checked against the allowed upload locations before it is kept.
     *
     * @param uploadedFiles the files of this multipart request, or {@code null} when none were sent
     */
    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles == null) {
            return;
        }
        for (UploadedFile uploaded : uploadedFiles) {
            if (uploaded != null && "content".equals(uploaded.getInputName()) && uploaded.isFile()) {
                this.content = PathValidationUtils.validateUpload(new File(uploaded.getAbsolutePath()));
                return;
            }
        }
    }

    public UpdateRequest toUpdateRequest() {
        UpdateRequest result = new UpdateRequest();
        result.setResourceID(BigInteger.valueOf(ConversionUtils.fromIntString(resourceId)));
        try {
            File validatedContent = PathValidationUtils.validateUpload(content);
            result.setContent(Files.readAllBytes(validatedContent.toPath()));
        } catch (SecurityException e) {
            throw new SecurityException("Invalid upload file path", e);
        } catch (Exception e) {
            throw new RuntimeException("Unable to read upload data", e);
        }
        return result;
    }
}
