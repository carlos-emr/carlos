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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/** Returns a stable client-safe error; diagnostic messages, paths and identifiers stay redacted. */
@Provider
public class PatientDirectiveExceptionMapper implements ExceptionMapper<PatientDirectiveException> {
    private static final Logger logger = MiscUtils.getLogger();

    @Override
    public Response toResponse(PatientDirectiveException exception) {
        RestErrorDiagnostics.log(logger, Level.INFO, exception);
        ErrorResponse body = ErrorResponse.of("PATIENT_DIRECTIVE", "Access to this record is restricted by a patient directive.");
        return Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
