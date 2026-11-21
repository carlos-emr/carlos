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
package io.github.carlos_emr.carlos.appointment.reminder;

/**
 * What happened to the last reminder attempt for one appointment start (#5887). Shown to staff on the
 * appointment; a send that reached the mail server also has its own email log row.
 *
 * @since 2026-11-12
 */
public enum AppointmentReminderStatus {
    /** Claimed and handed to the channel; stays here if the outcome was never recorded. */
    SENDING,
    /** The channel accepted it (for email: accepted by the mail server). */
    SENT,
    /**
     * Handed to the SMS queue (#884), which sends, retries and recovers it; its text's row says how it ended.
     * Never retried by the reminder job, so a text is never queued twice for one start.
     */
    QUEUED,
    /** Not sent in time: the text's limit (the start minus the cutoff) passed while it waited (#884). */
    EXPIRED,
    /** The channel tried and definitely did not send it. */
    FAILED,
    /**
     * A text reminder that would not fit one SMS (#884): the clinic's name or phone made it too long. It is never
     * cut or split, and never retried, since a retry would build the same text.
     */
    TOO_LONG,
    /** The channel tried but could not confirm whether it went; never retried. */
    UNCONFIRMED,
    /** The patient has not opted in to this channel, or has opted out. */
    BLOCKED_CONSENT,
    /** The patient has no address for this channel. */
    NO_ADDRESS,
    /** The patient's address for this channel is not valid. */
    INVALID_ADDRESS,
    /** The clinic has not set up a sender for this channel. */
    NO_SENDER,
    /** The clinic has not set up the footer every patient email must carry. */
    NO_FOOTER,
    /** The sender may not read this patient's record. */
    NOT_PERMITTED,
    /**
     * Not sent: another booking for the same visit (the same patient at the same start, as Group Appt books)
     * carries the reminder, so the patient gets one, not one per booking.
     */
    DUPLICATE
}
