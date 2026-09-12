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

package io.github.carlos_emr.carlos.webserv.rest.exceptionMapping;

import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.apache.logging.log4j.Logger;

/**
 * Maps a {@link PatientDirectiveException} to a {@code 403} JSON {@link ErrorResponse}.
 *
 * <p>A patient directive is a patient-authored access restriction (part of the
 * circle-of-care consent model), not a provider authorization failure. It is an expected,
 * policy-driven outcome rather than an error condition, so it is logged at {@code INFO}
 * level — distinguishing it from the {@code WARN}-level privilege denials handled by
 * {@link AccessDeniedExceptionMapper} and {@link SecurityExceptionMapper}.
 *
 * <p><strong>PHI safety:</strong> the client body carries only a generic message; the raw
 * exception message is recorded server-side at INFO.
 *
 * @since 2026-06-21
 */
@Provider
public class PatientDirectiveExceptionMapper implements ExceptionMapper<PatientDirectiveException> {

    private static final Logger logger = MiscUtils.getLogger();

    @Context
    private UriInfo uriInfo;

    /**
     * Maps the directive-based restriction to a {@code 403 Forbidden} JSON
     * {@link ErrorResponse}. This is an expected, policy-driven outcome, so it is logged
     * at {@code INFO} rather than as a privilege denial.
     *
     * @param exception the patient-directive restriction encountered
     * @return a {@code 403} JSON response with code {@code PATIENT_DIRECTIVE}
     */
    @Override
    public Response toResponse(PatientDirectiveException exception) {
        // Expected, policy-driven outcome — INFO, not WARN/ERROR.
        logger.info("Access blocked by patient directive at " + safePath()
                + ": " + LogSafe.sanitize(exception.getMessage()));

        ErrorResponse body = ErrorResponse.of(
                "PATIENT_DIRECTIVE",
                "Access to this record is restricted by a patient directive.");

        return Response.status(Response.Status.FORBIDDEN)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String safePath() {
        return uriInfo == null ? "unknown" : LogSafe.sanitizeUri(uriInfo.getPath());
    }
}
