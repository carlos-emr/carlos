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

import io.github.carlos_emr.carlos.commn.model.Document;

/**
 * Transfer data for an attachment included with an outbound email archive operation.
 *
 * @since 2026-08-14
 */
public class OutboundEmailArchiveAttachmentDto {

    private String fileName;
    private String contentType;
    private byte[] artifactBytes;
    private String sha256Hash;
    private Long byteSize;
    private String sourceDocumentType;
    private Integer sourceDocumentId;
    private Document document;

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

    public String getSourceDocumentType() {
        return sourceDocumentType;
    }

    public void setSourceDocumentType(String sourceDocumentType) {
        this.sourceDocumentType = sourceDocumentType;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Integer getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(Integer sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public void setByteSize(Long byteSize) {
        this.byteSize = byteSize;
    }
}
