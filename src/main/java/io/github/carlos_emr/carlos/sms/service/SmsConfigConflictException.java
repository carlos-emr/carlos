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
 * Thrown by {@link SmsConfigService#save} when the save raced another administrator's: both tried to
 * create the settings row, or both changed the same version of it. Nothing from this save is stored,
 * and the other save stands.
 *
 * @since 2026-09-28
 */
public class SmsConfigConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SmsConfigConflictException(Throwable cause) {
        super("SMS settings were changed by another save at the same time.", cause);
    }
}
