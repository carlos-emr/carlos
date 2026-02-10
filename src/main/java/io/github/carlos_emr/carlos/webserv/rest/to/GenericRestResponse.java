/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Ported from JunoEMR to the CARLOS EMR Project, 2026.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest.to;

import java.io.Serializable;

/**
 * Base generic REST response wrapper providing consistent structure for all API responses.
 *
 * <p>Encapsulates a response status, typed headers, body, and error fields to ensure
 * uniform response formatting across all REST endpoints. This replaces ad-hoc response
 * patterns with a single, type-safe structure.</p>
 *
 * @param <H> the type of the response headers
 * @param <B> the type of the response body
 * @param <E> the type of the error payload
 * @see RestResponse
 * @since 2026-02-10
 */
public class GenericRestResponse<H, B, E> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Indicates whether the response represents a successful or failed operation.
     */
    public enum ResponseStatus {
        SUCCESS,
        ERROR
    }

    private ResponseStatus status;
    private H headers;
    private B body;
    private E error;

    /**
     * Default constructor for serialization frameworks.
     */
    public GenericRestResponse() {
    }

    /**
     * Returns the response status indicating success or error.
     *
     * @return ResponseStatus the current response status
     */
    public ResponseStatus getStatus() {
        return status;
    }

    /**
     * Sets the response status.
     *
     * @param status ResponseStatus the status to set
     */
    public void setStatus(ResponseStatus status) {
        this.status = status;
    }

    /**
     * Returns the response headers containing metadata such as build information.
     *
     * @return H the response headers
     */
    public H getHeaders() {
        return headers;
    }

    /**
     * Sets the response headers.
     *
     * @param headers H the headers to set
     */
    public void setHeaders(H headers) {
        this.headers = headers;
    }

    /**
     * Returns the response body containing the requested data.
     *
     * @return B the response body, or null if the request resulted in an error
     */
    public B getBody() {
        return body;
    }

    /**
     * Sets the response body.
     *
     * @param body B the body to set
     */
    public void setBody(B body) {
        this.body = body;
    }

    /**
     * Returns the error payload if the request failed.
     *
     * @return E the error details, or null if the request was successful
     */
    public E getError() {
        return error;
    }

    /**
     * Sets the error payload.
     *
     * @param error E the error to set
     */
    public void setError(E error) {
        this.error = error;
    }
}
