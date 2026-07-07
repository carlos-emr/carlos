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

package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.Date;

/**
 * Metadata for an attachment that was included with an archived outbound email.
 *
 * @since 2026-07-07
 */
@Entity
@Table(name = "outboundEmailArchiveAttachment")
@SuppressWarnings({"java:S2160", "java:S2143"}) // Equality is inherited from AbstractModel id; DATETIME mappings follow CARLOS Hibernate conventions.
public class OutboundEmailArchiveAttachment extends OutboundEmailArchiveArtifact {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archiveId", nullable = false)
    private OutboundEmailArchive archive;

    @Column(length = 50)
    private String sourceDocumentType;

    private Integer sourceDocumentId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt;

    public OutboundEmailArchive getArchive() {
        return archive;
    }

    public void setArchive(OutboundEmailArchive archive) {
        this.archive = archive;
    }

    public String getSourceDocumentType() {
        return sourceDocumentType;
    }

    public void setSourceDocumentType(String sourceDocumentType) {
        this.sourceDocumentType = sourceDocumentType;
    }

    public Integer getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(Integer sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void prePersist() {
        createdAt = new Date();
    }
}
