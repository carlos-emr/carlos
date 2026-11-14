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
package io.github.carlos_emr.carlos.prevention.dto;

/**
 * A prevention read failed, so the caller has no list at all: not an empty one and not the rows
 * that happened to be read before the failure.
 *
 * <p>Every caller must turn this into a visible "preventions could not be read" result. It must
 * never be answered with an empty or partial list, because a list with records missing reads as
 * a complete history. The message is fixed text: it carries no patient data and no SQL, so it is
 * safe wherever an exception message can surface. The failure that caused it stays available as
 * the {@linkplain #getCause() cause} and is logged once where the read failed.
 *
 * <p>This is a different refusal from {@link PreventionDatePrecisionException}, which reports
 * records that were read but whose dates need review.
 */
public final class PreventionSourceUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /** The fixed message; also the marker the failed read is logged under. */
    public static final String MESSAGE = "PREVENTION_SOURCE_UNAVAILABLE";

    /**
     * @param cause the failure that stopped the read; kept for diagnosis, never shown to a user
     */
    public PreventionSourceUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
