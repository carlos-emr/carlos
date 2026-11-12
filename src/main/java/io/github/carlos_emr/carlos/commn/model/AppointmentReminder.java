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

import io.github.carlos_emr.carlos.appointment.reminder.AppointmentReminderStatus;
import io.github.carlos_emr.carlos.appointment.reminder.ReminderChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.util.Date;
import java.util.Objects;

/**
 * A patient reminder requested for one appointment, on one channel (#5887), and the state of its last send.
 *
 * <p>The choice lives here rather than on {@code appointment}: every edit copies the appointment into
 * {@code appointmentArchive}, and several paths insert appointments with raw SQL. {@code sentFor} is the
 * appointment start a reminder last went (or was attempted) for, so a start is reminded once and a moved
 * appointment is due again. Rows are claimed with {@code claimToken} before sending, so two servers or two
 * runs never send the same reminder. The table {@code appointment_reminder} is created by its own migration
 * (#5887); tests build it from this mapping.
 *
 * @since 2026-11-12
 */
@Entity
@Table(name = "appointment_reminder", uniqueConstraints = @UniqueConstraint(
        name = "appointment_reminder_appointment_channel_uidx", columnNames = {"appointment_no", "channel"}),
        // The job's recovery of rows a stopped run left SENDING reads by these two, with FOR UPDATE.
        indexes = @Index(name = "appointment_reminder_channel_status_idx", columnList = "channel, last_status"))
public class AppointmentReminder extends AbstractModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "appointment_no", nullable = false)
    private Integer appointmentNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private ReminderChannel channel;

    @Column(name = "requested", nullable = false)
    private boolean requested;

    @Column(name = "requested_by", length = 6)
    private String requestedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "requested_at")
    private Date requestedAt;

    @Column(name = "updated_by", length = 6)
    private String updatedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "sent_for")
    private Date sentFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", length = 32)
    private AppointmentReminderStatus lastStatus;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_attempt_at")
    private Date lastAttemptAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_email_log_id")
    private Integer lastEmailLogId;

    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "claimed_at")
    private Date claimedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AppointmentReminder() {
    }

    /** A new, requested reminder for {@code appointmentNo} on {@code channel}, chosen by {@code providerNo}. */
    public static AppointmentReminder requested(int appointmentNo, ReminderChannel channel, String providerNo, Date now) {
        AppointmentReminder reminder = new AppointmentReminder();
        reminder.appointmentNo = appointmentNo;
        reminder.channel = Objects.requireNonNull(channel, "channel");
        reminder.request(providerNo, now);
        return reminder;
    }

    /**
     * Staff ticked the box. A start already attempted is not reminded again; if the appointment has moved since,
     * it is reminded for its new start.
     */
    public void request(String providerNo, Date now) {
        requested = true;
        requestedBy = providerNo;
        requestedAt = now;
        updatedBy = providerNo;
        updatedAt = now;
    }

    /** Staff unticked the box. The row stays, with its history. */
    public void withdraw(String providerNo, Date now) {
        requested = false;
        updatedBy = providerNo;
        updatedAt = now;
    }

    /**
     * The appointment was booked again under a new number (Group Update deletes and re-creates it). The choice and
     * the start already reminded for go with it, so the same start is not reminded twice.
     */
    public void moveTo(int newAppointmentNo, String providerNo, Date now) {
        appointmentNo = newAppointmentNo;
        updatedBy = providerNo;
        updatedAt = now;
    }

    /** Claimed by a run. The claim is only a lock: nothing is recorded as attempted yet. */
    public void claim(String token, Date now) {
        claimToken = token;
        claimedAt = now;
    }

    /** The claim found nothing to send after all (the appointment moved or was cancelled meanwhile). */
    public void releaseClaim() {
        claimToken = null;
        claimedAt = null;
    }

    /**
     * About to send for {@code start}. Recorded before the channel is called, so a crash after the send can
     * leave this attempt unconfirmed but can never send the same start twice. The claim is renewed: its age
     * then measures this send, not the batch it was claimed with, so a slow batch's later send is not taken
     * for abandoned.
     */
    public void startSending(Date start, Date now) {
        if (claimToken != null) {
            claimedAt = now;
        }
        // Attempts count per start: a moved appointment starts again from its first attempt. Compared by time,
        // because a value read back from the database is a java.sql.Timestamp, never equal() to a Date.
        if (sentFor == null || start == null || sentFor.getTime() != start.getTime()) {
            attempts = 0;
        }
        sentFor = start;
        lastStatus = AppointmentReminderStatus.SENDING;
        lastAttemptAt = now;
        lastEmailLogId = null;
        attempts++;
        updatedAt = now;
    }

    /** The attempt for the current claim ended with {@code status}. */
    public void finish(AppointmentReminderStatus status, Integer emailLogId, Date now) {
        lastStatus = Objects.requireNonNull(status, "status");
        lastEmailLogId = emailLogId;
        updatedAt = now;
        releaseClaim();
    }

    @Override
    public Long getId() {
        return id;
    }

    public Integer getAppointmentNo() {
        return appointmentNo;
    }

    public ReminderChannel getChannel() {
        return channel;
    }

    public boolean isRequested() {
        return requested;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Date getRequestedAt() {
        return requestedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public Date getSentFor() {
        return sentFor;
    }

    public AppointmentReminderStatus getLastStatus() {
        return lastStatus;
    }

    public Date getLastAttemptAt() {
        return lastAttemptAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Integer getLastEmailLogId() {
        return lastEmailLogId;
    }

    public String getClaimToken() {
        return claimToken;
    }

    public Date getClaimedAt() {
        return claimedAt;
    }

    public long getVersion() {
        return version;
    }
}
