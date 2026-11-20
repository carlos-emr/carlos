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
package io.github.carlos_emr.carlos.sms;

/**
 * The patient consent state an outbound SMS decision relied on, stored with each
 * {@code sms_transaction} row as its consent audit snapshot.
 *
 * @since 2026-09-18
 */
public enum SmsConsentStatus {
    /** The patient has a current, non-deleted, explicit consent record that is not opted out. */
    OPT_IN,
    /** The patient has a current consent record marked opted out. */
    OPT_OUT,
    /** The patient's only current opt-in was implied rather than given directly, so it does not permit SMS. */
    NOT_EXPLICIT,
    /** No consent record exists for the patient, or the request named no patient. */
    UNKNOWN,
    /** No active SMS consent type is configured, so consent cannot be evaluated at all. */
    NOT_CONFIGURED,
    /** A synthetic system-test message; no patient consent record was consulted. */
    SYSTEM_TEST
}
