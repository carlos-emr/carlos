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

import java.io.IOException;
import org.apache.hc.core5.http.ClassicHttpRequest;

/**
 * The single point where a portal request leaves the JVM.
 *
 * <p>Isolating the socket behind one interface keeps every security property of {@link
 * PatientPortalService} — header envelope, URL pinning, status mapping, secret redaction — provable
 * in a unit test rather than only in an environment with a portal running.
 *
 * @since 2026-08-19
 */
@FunctionalInterface
interface PatientPortalHttpExchange {

    /**
     * Sends one request and returns the status and body.
     *
     * @param request an authenticated request built by {@link PatientPortalService#buildRequest}
     * @return the portal's status code and response body
     * @throws IOException if the call never produced a response
     */
    PatientPortalHttpResponse send(ClassicHttpRequest request) throws IOException;
}
