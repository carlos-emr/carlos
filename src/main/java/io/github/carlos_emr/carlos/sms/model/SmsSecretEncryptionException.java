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
package io.github.carlos_emr.carlos.sms.model;

/**
 * Thrown when an SMS secret cannot be encrypted for storage, usually because this server has no working
 * {@code encryption.util.secret.key}. It carries neither the secret nor the underlying cause, either of
 * which could put the secret into a log; the settings page shows it as a form error instead.
 *
 * @since 2026-10-06
 */
public class SmsSecretEncryptionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public SmsSecretEncryptionException() {
        super("SMS secret could not be encrypted; check encryption.util.secret.key.");
    }
}
