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
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static io.github.carlos_emr.carlos.integration.mcedt.ActionUtils.*;
import static io.github.carlos_emr.carlos.integration.mcedt.McedtConstants.SESSION_KEY_UPLOAD_DETAILS;

public class Update2Action extends ActionSupport implements UploadedFilesAware {
    private static final String SEND_IN_PROGRESS = Update2Action.class.getName() + ".sendInProgress";
    /** The EDT result code for an accepted resource. */
    private static final String EDT_SUCCESS = "IEDTS0001";

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
        HttpSession session = request.getSession();
        synchronized (WebUtils.getSessionMutex(session)) {
            Detail details = (Detail) session.getAttribute(SESSION_KEY_UPLOAD_DETAILS);
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
            List<UpdateRequest> updates = getUpdateList(session);
            // A repeated add replaces the pending file for this resource.
            updates.removeIf(queued -> requested.equals(queued.getResourceID()));
            updates.add(update);
            listed.forEach(this::markUpdated);
        }
        return "initial";
    }

    private void markUpdated(DetailData d) {
        d.setModifyTimestamp(new DummyXMLGregorianCalendar());
    }

    public String cancel() {
        HttpSession session = request.getSession();
        synchronized (WebUtils.getSessionMutex(session)) {
            clearUpdateList(request);
            session.removeAttribute(SESSION_KEY_UPLOAD_DETAILS);
        }
        return "cancel";
    }

    public String sendUpdateRequest() {
        return sendUpdateRequest(DelegateFactory::getEDTDelegateInstance);
    }

    String sendUpdateRequest(Supplier<EDTDelegate> delegate) {
        HttpSession session = request.getSession();
        Object mutex = WebUtils.getSessionMutex(session);
        List<UpdateRequest> snapshot;
        Detail sentDetails;
        synchronized (mutex) {
            if (session.getAttribute(SEND_IN_PROGRESS) != null) {
                addActionError(getText("updateAction.sendUpdateRequest.inProgress"));
                return SUCCESS;
            }
            snapshot = List.copyOf(getUpdateList(session));
            if (snapshot.isEmpty()) {
                addActionError(getText("updateAction.sendUpdateRequest.emptyUpdates"));
                return SUCCESS;
            }
            sentDetails = (Detail) session.getAttribute(SESSION_KEY_UPLOAD_DETAILS);
            // This request marker must not survive session persistence or failover.
            session.setAttribute(SEND_IN_PROGRESS, new Object());
        }

        try {
            ResourceResult result = delegate.get().update(snapshot);
            // A normal reply can still refuse some resources; only confirmed updates leave the queue.
            List<UpdateRequest> confirmed = confirmedUpdates(snapshot, result);
            synchronized (mutex) {
                acknowledgeSentUpdates(session, sentDetails, confirmed);
            }
            String outcome = result == null ? "" : McedtMessageCreator.resourceResultToString(result);
            if (confirmed.size() == snapshot.size()) {
                addActionMessage(getText("updateAction.sendUpdateRequest.success", new String[]{outcome}));
            } else {
                addActionError(getText("updateAction.sendUpdateRequest.partial", new String[]{
                        String.valueOf(snapshot.size() - confirmed.size()), String.valueOf(snapshot.size()), outcome}));
            }
        } catch (Exception e) {
            logger.error("Unable to update", e);

            request.setAttribute("message", "Error updating: " + e.getMessage());
            addActionError(getText("updateAction.sendUpdateRequest.failure", new String[]{McedtMessageCreator.exceptionToString(e)}));
        } finally {
            synchronized (mutex) {
                session.removeAttribute(SEND_IN_PROGRESS);
            }
        }

        return SUCCESS;
    }

    /**
     * The sent updates the EDT reply confirms with {@code IEDTS0001}. A refused resource, or one
     * the reply does not mention, is not confirmed and stays queued for another send.
     */
    static List<UpdateRequest> confirmedUpdates(List<UpdateRequest> sent, ResourceResult result) {
        Set<BigInteger> confirmedIds = new HashSet<>();
        if (result != null) {
            for (ResponseResult response : result.getResponse()) {
                if (response != null && response.getResourceID() != null && response.getResult() != null
                        && EDT_SUCCESS.equals(response.getResult().getCode())) {
                    confirmedIds.add(response.getResourceID());
                }
            }
        }
        List<UpdateRequest> confirmed = new ArrayList<>();
        for (UpdateRequest update : sent) {
            if (confirmedIds.contains(update.getResourceID())) {
                confirmed.add(update);
            }
        }
        return confirmed;
    }

    private void acknowledgeSentUpdates(HttpSession session, Detail sentDetails, List<UpdateRequest> confirmed) {
        List<UpdateRequest> pending = getUpdateList(session);
        // A replacement for the same resource is a new UpdateRequest and stays queued, as does
        // any sent update the reply did not confirm.
        pending.removeAll(confirmed);
        if (pending.isEmpty()) {
            clearUpdateList(request);
        }
        if (sentDetails != null && sentDetails == session.getAttribute(SESSION_KEY_UPLOAD_DETAILS)) {
            if (pending.isEmpty()) {
                session.removeAttribute(SESSION_KEY_UPLOAD_DETAILS);
            } else {
                for (DetailData detail : sentDetails.getData()) {
                    if (pending.stream().noneMatch(update -> update.getResourceID().equals(detail.getResourceID()))) {
                        detail.setModifyTimestamp(null);
                    }
                }
            }
        }
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
                // The interceptor's own temp file, checked against the allowed upload locations;
                // no path is built from a string, as in the other upload actions.
                this.content = PathValidationUtils.validateUploadContent(uploaded.getContent());
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
