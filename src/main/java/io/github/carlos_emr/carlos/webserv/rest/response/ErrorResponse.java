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
package io.github.carlos_emr.carlos.webserv.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured JSON error body returned by the REST exception mappers in
 * {@code io.github.carlos_emr.carlos.webserv.rest.exceptionMapping}.
 *
 * <p>The shape is a stable client contract:
 * <pre>
 * {
 *   "code": "VALIDATION_ERROR",
 *   "message": "Unknown drug status: active",
 *   "details": {"parameter": "status"},
 *   "timestamp": "2026-02-05T14:30:00Z"
 * }
 * </pre>
 *
 * <p>This object carries only client-safe data. Mappers are responsible for
 * keeping stack traces and PHI-correlating identifiers (e.g. {@code demographicNo})
 * out of the {@code message}/{@code details} and confining them to server-side logs.
 *
 * <p>{@code details} is omitted from the serialized output when empty.
 *
 * @since 2026-06-21
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"code", "message", "details", "timestamp"})
public class ErrorResponse {

    private String code;
    private String message;
    private Map<String, Object> details;
    private String timestamp;

    /** No-argument constructor required for Jackson (de)serialization. */
    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message, Map<String, Object> details, String timestamp) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.timestamp = timestamp;
    }

    /**
     * Creates an error body stamped with the current UTC time, truncated to whole
     * seconds so the serialized form is {@code yyyy-MM-ddTHH:mm:ssZ}.
     *
     * @param code    machine-readable error code (e.g. {@code ACCESS_DENIED})
     * @param message client-safe human-readable description
     * @return a new error response with no details and a current timestamp
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
    }

    /**
     * Returns a copy-style mutation of this response carrying the supplied detail
     * map. A {@code null} or empty map leaves {@code details} unset so it is omitted
     * from serialization.
     *
     * @param details supplementary client-safe context; may be {@code null}
     * @return this instance for fluent chaining
     */
    public ErrorResponse withDetails(Map<String, Object> details) {
        if (details != null && !details.isEmpty()) {
            this.details = new LinkedHashMap<>(details);
        }
        return this;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
