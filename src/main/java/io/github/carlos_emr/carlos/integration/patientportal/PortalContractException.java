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
package io.github.carlos_emr.carlos.integration.patientportal;

import java.io.Serial;

/**
 * The portal answered with a body that does not match the contract CARLOS was built against.
 *
 * <p>Package-private by intent: {@link PatientPortalService} catches this and re-raises it as a
 * {@link PatientPortalException} with {@link PatientPortalException.Kind#MALFORMED_RESPONSE}, so
 * callers branch on one exception type. Letting a raw parse failure escape — which an earlier
 * revision did for a malformed timestamp — bypasses the {@code Kind} contract the package is built
 * around and surfaces as a generic CARLOS error page.
 *
 * <p>Messages name the offending field only, never its value, because portal payloads carry patient
 * contact details.
 *
 * @since 2026-08-19
 */
class PortalContractException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    PortalContractException(String message) {
        super(message);
    }

    PortalContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
