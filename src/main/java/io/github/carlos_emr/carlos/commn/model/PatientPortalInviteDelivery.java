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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

/**
 * One attempt to deliver a patient portal invitation, and how far it got.
 *
 * <p>The portal activates an invitation token only when CARLOS commits delivery, and CARLOS may
 * commit only once the email carrying the token is durable. This row records each step of that
 * sequence so a crash, timeout or refused send leaves a state that names the step, rather than an
 * email whose fate nobody can reconstruct. The invite code is never stored here.
 *
 * @since 2026-09-22
 */
@Entity
@Table(
        name = "patient_portal_invite_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "ppid_operation_uidx", columnNames = "delivery_operation_id"))
public class PatientPortalInviteDelivery extends AbstractModel<Long> {

    private static final long serialVersionUID = 1L;

    /** How the invitation reaches the patient. */
    public enum Channel {
        EMAIL("email"),
        /** Reserved until CARLOS has an SMS provider; requests for it are refused before any portal call. */
        SMS("sms");

        private final String requestValue;

        Channel(String requestValue) {
            this.requestValue = requestValue;
        }

        /** @return the {@code channel} request value that selects this channel */
        public String requestValue() {
            return requestValue;
        }

        /** Parses the {@code channel} request value, or returns {@code null} when it is not a channel. */
        public static Channel parse(String value) {
            for (Channel channel : values()) {
                if (channel.requestValue.equals(value)) {
                    return channel;
                }
            }
            return null;
        }
    }

    /** Where a delivery attempt stands. Each step is recorded before the next one starts. */
    public enum State {
        /** The row is claimed; the portal has not been asked yet. */
        PREPARING,
        /** The portal prepared an inactive token; no email exists yet. */
        PREPARED,
        /** The email carrying the token is stored and waiting; the portal has not activated the token. */
        QUEUED,
        /** The portal activated the token; the email has not been confirmed sent. */
        COMMITTED,
        /** The mail transport accepted the email. */
        SENT,
        /** The token is live but the transport definitely refused the email; a resend replaces it. */
        SEND_FAILED,
        /** The token is live and the transport could not say whether it accepted the email. */
        SEND_UNCERTAIN,
        /** Stopped before the token was activated; nothing reached the patient. */
        ABANDONED,
        /** Staff confirmed the email never arrived and the invitation was revoked on the portal. */
        REVOKED;

        private static final Set<State> TERMINAL = EnumSet.of(SENT, SEND_FAILED, ABANDONED, REVOKED);

        /** @return whether the attempt has finished, so no step is still owed */
        public boolean isTerminal() {
            return TERMINAL.contains(this);
        }
    }

    /**
     * Why an attempt stands where it does, recorded as a code rather than prose so the staff page can
     * say it in the reader's language. {@code null} while nothing needs explaining, including a send
     * that went through.
     */
    public enum Outcome {
        /** The portal refused to prepare the invitation, so it prepared nothing. */
        PREPARE_REFUSED,
        /** The portal may have prepared a code whose id CARLOS never learned; staff must resolve it. */
        PREPARE_UNCONFIRMED,
        /** The portal refused to activate the code, so the email was never sent. */
        COMMIT_REFUSED,
        /** The portal did not confirm activating the code, so the email was never sent. */
        COMMIT_UNCONFIRMED,
        /** Consent or the email setup stopped the send before the portal was asked to activate the code. */
        SEND_BLOCKED,
        /** The mail server refused the email after the code went live. */
        SEND_REFUSED,
        /** The mail server did not say whether it accepted the email after the code went live. */
        SEND_UNCONFIRMED,
        /** Staff confirmed the email arrived. */
        CONFIRMED_SENT,
        /** Staff confirmed the email did not arrive, and the code was revoked. */
        CONFIRMED_NOT_SENT,
        /** Staff stopped the attempt before its code was activated. */
        ABANDONED_BY_STAFF
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "delivery_operation_id", nullable = false, length = 64, updatable = false)
    private String deliveryOperationId;

    @Column(name = "demographic_no", nullable = false, updatable = false)
    private Integer demographicNo;

    @Column(name = "clinic_id", nullable = false, length = 64, updatable = false)
    private String clinicId;

    @Column(name = "portal_origin", nullable = false, length = 512, updatable = false)
    private String portalOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16, updatable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private State state;

    @Column(name = "portal_invite_id")
    private Long portalInviteId;

    @Column(name = "superseded_invite_id", updatable = false)
    private Long supersededInviteId;

    @Column(name = "email_log_id")
    private Integer emailLogId;

    @Column(name = "requested_by", nullable = false, length = 16, updatable = false)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 32)
    private Outcome outcome;

    /** Whether withdrawing an unused code on the portal failed; the code then expires on its own. */
    @Column(name = "revoke_failed", nullable = false)
    private boolean revokeFailed;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at")
    private Date expiresAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PatientPortalInviteDelivery() {
        // JPA
    }

    /** Starts an attempt in {@link State#PREPARING}. */
    public PatientPortalInviteDelivery(String deliveryOperationId, int demographicNo, String clinicId,
            String portalOrigin, Channel channel, Long supersededInviteId, String requestedBy) {
        this.deliveryOperationId = deliveryOperationId;
        this.demographicNo = demographicNo;
        this.clinicId = clinicId;
        this.portalOrigin = portalOrigin;
        this.channel = channel;
        this.supersededInviteId = supersededInviteId;
        this.requestedBy = requestedBy;
        this.state = State.PREPARING;
    }

    @PrePersist
    void beforePersist() {
        Date now = new Date();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = new Date();
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getDeliveryOperationId() {
        return deliveryOperationId;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public String getClinicId() {
        return clinicId;
    }

    public String getPortalOrigin() {
        return portalOrigin;
    }

    public Channel getChannel() {
        return channel;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Long getPortalInviteId() {
        return portalInviteId;
    }

    public void setPortalInviteId(Long portalInviteId) {
        this.portalInviteId = portalInviteId;
    }

    public Long getSupersededInviteId() {
        return supersededInviteId;
    }

    public Integer getEmailLogId() {
        return emailLogId;
    }

    public void setEmailLogId(Integer emailLogId) {
        this.emailLogId = emailLogId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    public boolean isRevokeFailed() {
        return revokeFailed;
    }

    public void setRevokeFailed(boolean revokeFailed) {
        this.revokeFailed = revokeFailed;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }
}
