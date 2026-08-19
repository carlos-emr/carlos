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
 * Raised when the patient portal channel is absent, incomplete, or configured insecurely.
 *
 * <p>This is deliberately a hard failure rather than a signal to disable portal features quietly. A
 * silent fallback would let a clinic believe invites were being issued while every call was being
 * dropped, and would hide a base URL that had been downgraded to plaintext.
 *
 * <p>Messages name the offending property key so an operator can fix the deployment, and never
 * carry the configured value of a secret.
 *
 * @since 2026-08-19
 */
public class PatientPortalConfigurationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PatientPortalConfigurationException(String message) {
        super(message);
    }

    public PatientPortalConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
