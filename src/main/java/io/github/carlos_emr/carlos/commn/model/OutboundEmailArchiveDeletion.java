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
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.Date;

/**
 * Immutable tombstone for a controlled deletion of an outbound email archive record.
 *
 * @since 2026-07-07
 */
@Entity
@Table(name = "outboundEmailArchiveDeletion")
@SuppressWarnings({"java:S2160", "java:S2143"}) // Equality is inherited from AbstractModel id; DATETIME mappings follow CARLOS Hibernate conventions.
public class OutboundEmailArchiveDeletion extends OutboundEmailArchiveArtifact {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archiveId", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private OutboundEmailArchive archive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emailLogId", nullable = false)
    private EmailLog emailLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demographicNo", nullable = false)
    private Demographic demographic;

    @Column(nullable = false, length = 6)
    private String deletedByProviderNo;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date deletedAt;

    @Column(nullable = false, length = 1000)
    private String deleteReason;

    /**
     * Copies archive identity and integrity fields into a new unsaved deletion tombstone.
     *
     * @param archive source archive being marked deleted
     * @param deletedByProviderNo provider number responsible for the controlled deletion
     * @param deleteReason deletion reason retained for audit
     * @return new immutable deletion tombstone ready to persist
     */
    public static OutboundEmailArchiveDeletion fromArchive(OutboundEmailArchive archive, String deletedByProviderNo, String deleteReason) {
        OutboundEmailArchiveDeletion deletion = new OutboundEmailArchiveDeletion();
        deletion.setArchive(archive);
        deletion.setEmailLog(archive.getEmailLog());
        deletion.setDemographic(archive.getDemographic());
        deletion.setDocument(archive.getDocument());
        deletion.setFileName(archive.getFileName());
        deletion.setContentType(archive.getContentType());
        deletion.setSha256Hash(archive.getSha256Hash());
        deletion.setByteSize(archive.getByteSize());
        deletion.setDeletedByProviderNo(deletedByProviderNo);
        deletion.setDeletedAt(archive.getDeletedAt());
        deletion.setDeleteReason(deleteReason);
        deletion.setLastUpdateUser(deletedByProviderNo);
        return deletion;
    }

    public OutboundEmailArchive getArchive() {
        return archive;
    }

    public void setArchive(OutboundEmailArchive archive) {
        this.archive = archive;
    }

    public EmailLog getEmailLog() {
        return emailLog;
    }

    public void setEmailLog(EmailLog emailLog) {
        this.emailLog = emailLog;
    }

    public Demographic getDemographic() {
        return demographic;
    }

    public void setDemographic(Demographic demographic) {
        this.demographic = demographic;
    }

    public String getDeletedByProviderNo() {
        return deletedByProviderNo;
    }

    public void setDeletedByProviderNo(String deletedByProviderNo) {
        this.deletedByProviderNo = deletedByProviderNo;
    }

    public Date getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Date deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public void setDeleteReason(String deleteReason) {
        this.deleteReason = deleteReason;
    }

    /**
     * Initializes the deletion timestamp before insertion when the caller has
     * not supplied one.
     */
    @PrePersist
    protected void prePersist() {
        if (deletedAt == null) {
            deletedAt = new Date();
        }
    }

    /**
     * Prevents mutation of persisted deletion tombstones.
     */
    @PreUpdate
    @PreRemove
    protected void preventMutation() {
        throw new UnsupportedOperationException("Outbound email archive deletion tombstones are immutable");
    }
}
