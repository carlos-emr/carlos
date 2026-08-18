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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Immutable record of a legal hold being placed on, or released from, an
 * outbound email archive.
 *
 * <p>Legal hold is on by default for every archive, so releasing it is the only
 * route to controlled deletion and is therefore the privileged act worth
 * attributing. Without this record a release would be invisible: the archive row
 * would simply show {@code legalHold = false} with no indication of who cleared it
 * or why, and the subsequent deletion tombstone would name the deleting provider
 * while saying nothing about who authorised the deletion to be possible at all.
 * Those are frequently different people.</p>
 *
 * <p>Rows are append-only. Mutation and removal are blocked at the JPA lifecycle
 * level, matching {@link OutboundEmailArchiveDeletion}.</p>
 *
 * @since 2026-08-17
 */
@Entity
@Table(name = "outboundEmailArchiveLegalHoldEvent")
@SuppressWarnings({"java:S2160", "java:S2143"}) // Equality is inherited from AbstractModel id; DATETIME mappings follow CARLOS Hibernate conventions.
public class OutboundEmailArchiveLegalHoldEvent extends AbstractModel<Integer> {

    public static final String ACTION_PLACED = "PLACED";
    public static final String ACTION_RELEASED = "RELEASED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Constrained at the database level, mirroring {@link OutboundEmailArchiveDeletion}.
     * Archives are never hard-deleted -- {@link OutboundEmailArchive#preRemove} refuses
     * removal and retirement is a soft flag plus a tombstone -- so the foreign key can
     * never block anything the design permits, and it makes an orphaned event
     * impossible. That matters because this association is navigable: unconstrained, a
     * stale {@code archiveId} would surface as {@code EntityNotFoundException} on the
     * first dereference rather than being rejected at insert.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archiveId", nullable = false)
    private OutboundEmailArchive archive;

    @Column(nullable = false, length = 25)
    private String action;

    @Column(nullable = false, length = 6)
    private String providerNo;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date eventAt;

    @Column(nullable = false, length = 6)
    private String lastUpdateUser;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date lastUpdateDate = new Date();

    /**
     * Builds an unsaved legal hold event for an archive.
     *
     * @param archive archive whose hold state changed
     * @param action one of {@link #ACTION_PLACED} or {@link #ACTION_RELEASED}
     * @param providerNo provider number responsible for the change
     * @param reason operator-supplied justification retained for audit
     * @return new immutable event ready to persist
     */
    public static OutboundEmailArchiveLegalHoldEvent of(
            OutboundEmailArchive archive, String action, String providerNo, String reason) {
        OutboundEmailArchiveLegalHoldEvent event = new OutboundEmailArchiveLegalHoldEvent();
        event.setArchive(archive);
        event.setAction(action);
        event.setProviderNo(providerNo);
        event.setReason(reason);
        event.setLastUpdateUser(providerNo);
        return event;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public OutboundEmailArchive getArchive() {
        return archive;
    }

    public void setArchive(OutboundEmailArchive archive) {
        this.archive = archive;
    }

    public String getAction() {
        return action;
    }

    /**
     * Sets the transition this event records.
     *
     * <p>Rejects anything other than the two known actions. The column is the only
     * thing that says which way the hold moved, and these rows are append-only, so a
     * value that slipped through here could never be corrected afterwards.</p>
     *
     * @param action one of {@link #ACTION_PLACED} or {@link #ACTION_RELEASED}
     * @throws IllegalArgumentException when the action is not one of those two
     */
    public void setAction(String action) {
        if (!ACTION_PLACED.equals(action) && !ACTION_RELEASED.equals(action)) {
            throw new IllegalArgumentException("Unsupported outbound email archive legal hold action: " + action);
        }
        this.action = action;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Date getEventAt() {
        return eventAt;
    }

    public void setEventAt(Date eventAt) {
        this.eventAt = eventAt;
    }

    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    /**
     * Stamps the event and audit timestamps before insertion when the caller has
     * not supplied them.
     */
    @PrePersist
    protected void prePersist() {
        if (eventAt == null) {
            eventAt = new Date();
        }
        lastUpdateDate = new Date();
    }

    /**
     * Prevents mutation of persisted legal hold events.
     */
    @PreUpdate
    @PreRemove
    protected void preventMutation() {
        throw new UnsupportedOperationException("Outbound email archive legal hold events are immutable");
    }
}
