/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsUploadService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsUploadException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** CSRF-protected POST ingress; raw chunks bypass the multipart replay buffer, not security filters. */
@Component("cdsUpload2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsUpload2Action extends ActionSupport {
    private final transient CdsImportAccessService access;
    private final transient CdsUploadService uploads;
    private String operation, uploadId, fileName, sha256;
    private long expectedBytes, offset;
    private int bytes;
    public CdsUpload2Action(CdsImportAccessService access, CdsUploadService uploads) { this.access = access; this.uploads = uploads; }

    @Override public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST"); response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED); return NONE;
        }
        try {
            String operator = CdsWeb.operator(CdsWeb.authorize(request, access));
            CdsWeb.parameters(request, Set.of("operation", "uploadId", "fileName", "expectedBytes", "offset", "bytes", "sha256"));
            Object receipt;
            if ("begin".equals(operation)) receipt = uploads.begin(operator, fileName, expectedBytes);
            else if ("chunk".equals(operation)) {
                String type = request.getContentType();
                if (type == null || !type.split(";", 2)[0].equalsIgnoreCase("application/octet-stream")) {
                    throw new CdsUploadException("UPLOAD_RAW_BODY_REQUIRED", 415);
                }
                if (request.getContentLengthLong() > uploads.chunkBytes()) throw new CdsUploadException("UPLOAD_CHUNK_TOO_LARGE", 413);
                receipt = uploads.append(operator, uploadId, offset, bytes, sha256, request.getInputStream());
            } else if ("finish".equals(operation)) receipt = uploads.finish(operator, uploadId);
            else if ("abandon".equals(operation)) receipt = uploads.abandon(operator, uploadId);
            else throw new CdsUploadException("UPLOAD_OPERATION_INVALID", 400);
            CdsWeb.json(response, 200, Map.of("ok", true, "upload", receipt));
        } catch (Exception failure) { CdsWeb.failure(response, failure); }
        return NONE;
    }
    @StrutsParameter public void setOperation(String value) { operation = value; }
    @StrutsParameter public void setUploadId(String value) { uploadId = value; }
    @StrutsParameter public void setFileName(String value) { fileName = value; }
    @StrutsParameter public void setExpectedBytes(long value) { expectedBytes = value; }
    @StrutsParameter public void setOffset(long value) { offset = value; }
    @StrutsParameter public void setBytes(int value) { bytes = value; }
    @StrutsParameter public void setSha256(String value) { sha256 = value; }
}
