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

import jakarta.persistence.AssociationOverride;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Durable metadata for an exact outbound email artifact stored in the patient eDoc file.
 *
 * @since 2026-07-07
 */
@Entity
@Table(name = "outboundEmailArchive")
@AttributeOverride(name = "contentType", column = @Column(name = "contentType", nullable = false, length = 100))
@AssociationOverride(name = "document", joinColumns = @JoinColumn(name = "documentNo", nullable = false))
@SuppressWarnings({"java:S2160", "java:S2143"}) // Equality is inherited from AbstractModel id; DATETIME mappings follow CARLOS Hibernate conventions.
public class OutboundEmailArchive extends OutboundEmailArchiveArtifact {

    public static final String ARTIFACT_TYPE_SMTP_RFC822 = "SMTP_RFC822";
    public static final String ARTIFACT_TYPE_API_PAYLOAD = "API_PAYLOAD";
    public static final String STORAGE_TYPE_EDOC = "EDOC";
    public static final String RETENTION_POLICY_PERMANENT = "PERMANENT";
    public static final String SEND_STATUS_ARCHIVED = "ARCHIVED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emailLogId", nullable = false)
    private EmailLog emailLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demographicNo", nullable = false)
    private Demographic demographic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "providerNo")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configId")
    private EmailConfig emailConfig;

    @Column(nullable = false, length = 50)
    private String artifactType;

    @Column(nullable = false, length = 50)
    private String transportType;

    @Column(length = 100)
    private String providerName;

    @Column(length = 255)
    private String providerMessageId;

    @Column(length = 1000)
    private String providerResponse;

    @Column(length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 25)
    private String storageType = STORAGE_TYPE_EDOC;

    @Column(nullable = false, length = 50)
    private String retentionPolicy = RETENTION_POLICY_PERMANENT;

    @Column(nullable = false)
    private boolean legalHold;

    @Column(nullable = false)
    private boolean deleted;

    /**
     * Archive-capture state, not SMTP delivery outcome. Delivery success or failure is retained on
     * the owning {@link EmailLog}, while this immutable artifact records the exact attempted message.
     */
    @Column(nullable = false, length = 25)
    private String sendStatus = SEND_STATUS_ARCHIVED;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date archivedAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    private Date sendAttemptedAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date sentAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date deletedAt;

    @Column(length = 6)
    private String deletedByProviderNo;

    @Column(length = 1000)
    private String deleteReason;

    @OneToMany(mappedBy = "archive", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<OutboundEmailArchiveAttachment> attachments = new ArrayList<OutboundEmailArchiveAttachment>();

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

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public EmailConfig getEmailConfig() {
        return emailConfig;
    }

    public void setEmailConfig(EmailConfig emailConfig) {
        this.emailConfig = emailConfig;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getProviderResponse() {
        return providerResponse;
    }

    public void setProviderResponse(String providerResponse) {
        this.providerResponse = providerResponse;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public boolean isLegalHold() {
        return legalHold;
    }

    public void setLegalHold(boolean legalHold) {
        this.legalHold = legalHold;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(String sendStatus) {
        this.sendStatus = sendStatus;
    }

    public Date getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Date archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Date getSendAttemptedAt() {
        return sendAttemptedAt;
    }

    public void setSendAttemptedAt(Date sendAttemptedAt) {
        this.sendAttemptedAt = sendAttemptedAt;
    }

    public Date getSentAt() {
        return sentAt;
    }

    public void setSentAt(Date sentAt) {
        this.sentAt = sentAt;
    }

    public Date getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Date deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedByProviderNo() {
        return deletedByProviderNo;
    }

    public void setDeletedByProviderNo(String deletedByProviderNo) {
        this.deletedByProviderNo = deletedByProviderNo;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public void setDeleteReason(String deleteReason) {
        this.deleteReason = deleteReason;
    }

    public List<OutboundEmailArchiveAttachment> getAttachments() {
        return attachments;
    }

    /**
     * Adds a non-null attachment and assigns this archive as its owner.
     * Null attachments are ignored.
     *
     * @param attachment attachment to associate with this archive
     */
    public void addAttachment(OutboundEmailArchiveAttachment attachment) {
        if (attachment == null) {
            return;
        }
        attachment.setArchive(this);
        attachments.add(attachment);
    }

    /**
     * Marks this archive as deleted through the controlled tombstone workflow.
     *
     * @param providerNo provider number responsible for the deletion
     * @param reason deletion reason retained for audit
     * @throws IllegalStateException when legal hold is active or the archive is already deleted
     */
    public void markDeleted(String providerNo, String reason) {
        if (legalHold) {
            throw new IllegalStateException("Cannot delete outbound email archive while legal hold is active");
        }
        if (deleted) {
            throw new IllegalStateException("Outbound email archive is already deleted");
        }
        this.deleted = true;
        this.deletedAt = new Date();
        this.deletedByProviderNo = providerNo;
        this.deleteReason = reason;
        setLastUpdateUser(providerNo);
    }

    /**
     * Applies default archive values immediately before the row is first persisted.
     */
    @PrePersist
    protected void prePersist() {
        if (archivedAt == null) {
            archivedAt = new Date();
        }
        if (storageType == null || storageType.isBlank()) {
            storageType = STORAGE_TYPE_EDOC;
        }
        if (retentionPolicy == null || retentionPolicy.isBlank()) {
            retentionPolicy = RETENTION_POLICY_PERMANENT;
        }
        if (sendStatus == null || sendStatus.isBlank()) {
            sendStatus = SEND_STATUS_ARCHIVED;
        }
    }

    /**
     * Blocks physical deletion so archive removal always leaves a tombstone.
     */
    @PreRemove
    protected void preRemove() {
        throw new UnsupportedOperationException("Outbound email archives must be deleted through controlled tombstone workflow");
    }
}
