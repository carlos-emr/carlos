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

package io.github.carlos_emr.carlos.email.archive;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;

import java.util.ArrayList;
import java.util.List;

/**
 * Transfer data for storing an exact outbound email artifact in the patient file.
 *
 * @since 2026-07-07
 */
public class OutboundEmailArchiveDto {

    private EmailLog emailLog;
    private byte[] artifactBytes;
    private String fileName;
    private String contentType;
    private String artifactType = OutboundEmailArchive.ARTIFACT_TYPE_SMTP_RFC822;
    private String transportType;
    private String providerName;
    private String providerMessageId;
    private String providerResponse;
    private List<OutboundEmailArchiveAttachmentDto> attachments = new ArrayList<OutboundEmailArchiveAttachmentDto>();

    public EmailLog getEmailLog() {
        return emailLog;
    }

    public void setEmailLog(EmailLog emailLog) {
        this.emailLog = emailLog;
    }

    public byte[] getArtifactBytes() {
        return artifactBytes != null ? artifactBytes.clone() : null;
    }

    public void setArtifactBytes(byte[] artifactBytes) {
        this.artifactBytes = artifactBytes != null ? artifactBytes.clone() : null;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getProviderResponse() {
        return providerResponse;
    }

    public void setProviderResponse(String providerResponse) {
        this.providerResponse = providerResponse;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public List<OutboundEmailArchiveAttachmentDto> getAttachments() {
        return new ArrayList<OutboundEmailArchiveAttachmentDto>(attachments);
    }

    public void setAttachments(List<OutboundEmailArchiveAttachmentDto> attachments) {
        this.attachments = attachments != null
                ? new ArrayList<OutboundEmailArchiveAttachmentDto>(attachments)
                : new ArrayList<OutboundEmailArchiveAttachmentDto>();
    }

    /**
     * Adds a non-null attachment to this archive request. Null attachments are
     * ignored.
     *
     * @param attachment attachment metadata to add
     */
    public void addAttachment(OutboundEmailArchiveAttachmentDto attachment) {
        if (attachment != null) {
            attachments.add(attachment);
        }
    }
}
