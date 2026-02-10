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
 * Type-safe REST response wrapper for single-item or list responses.
 *
 * <p>Provides a consistent response format with status, build headers, typed body,
 * and error fields. Use the static factory methods to construct responses:</p>
 *
 * <pre>{@code
 * // Success response
 * RestResponse<MyDto> response = RestResponse.successResponse(myDto);
 *
 * // Error response
 * RestResponse<MyDto> response = RestResponse.errorResponse("Something went wrong");
 * }</pre>
 *
 * @param <T> the type of the response body
 * @see GenericRestResponse
 * @see RestResponseHeaders
 * @see RestResponseError
 * @since 2026-02-10
 */
public class RestResponse<T> extends GenericRestResponse<RestResponseHeaders, T, RestResponseError> {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a successful response containing the given body.
     *
     * @param <T> the type of the response body
     * @param body T the response payload
     * @return RestResponse&lt;T&gt; a success response with build headers and the given body
     */
    public static <T> RestResponse<T> successResponse(T body) {
        RestResponse<T> response = new RestResponse<>();
        response.setStatus(ResponseStatus.SUCCESS);
        response.setHeaders(new RestResponseHeaders());
        response.setBody(body);
        return response;
    }

    /**
     * Creates an error response with the given message.
     *
     * @param <T> the type of the response body (will be null in the response)
     * @param errorMessage String the error message describing what went wrong
     * @return RestResponse&lt;T&gt; an error response with build headers and the given error
     */
    public static <T> RestResponse<T> errorResponse(String errorMessage) {
        RestResponse<T> response = new RestResponse<>();
        response.setStatus(ResponseStatus.ERROR);
        response.setHeaders(new RestResponseHeaders());
        response.setError(new RestResponseError(errorMessage));
        return response;
    }

    /**
     * Creates an error response with a message and additional data.
     *
     * @param <T> the type of the response body (will be null in the response)
     * @param errorMessage String the error message describing what went wrong
     * @param data Serializable optional additional context about the error
     * @return RestResponse&lt;T&gt; an error response with build headers and the given error
     */
    public static <T> RestResponse<T> errorResponse(String errorMessage, Serializable data) {
        RestResponse<T> response = new RestResponse<>();
        response.setStatus(ResponseStatus.ERROR);
        response.setHeaders(new RestResponseHeaders());
        response.setError(new RestResponseError(errorMessage, data));
        return response;
    }
}
