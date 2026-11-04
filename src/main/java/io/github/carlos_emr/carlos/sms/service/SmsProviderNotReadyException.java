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
package io.github.carlos_emr.carlos.sms.service;

/**
 * The clinic's SMS provider cannot send yet: a stored credential cannot be read (for example after the encryption
 * key changed), or a credential or the sender number it requires is missing. Nothing is sent or recorded; an
 * administrator fixes it in Administration &gt; SMS. The message names the reason only, never a value.
 *
 * @since 2026-10-08
 */
public class SmsProviderNotReadyException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    SmsProviderNotReadyException(String reason) {
        super(reason);
    }

    SmsProviderNotReadyException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
