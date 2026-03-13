/**
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
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
 * This software was written for
 * CloudPractice Inc.
 * Victoria, British Columbia
 * Canada
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.webserv.rest.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * Concrete REST response wrapper for single-result endpoints in CARLOS EMR.
 *
 * <p>Specialises {@link GenericRestResponse} with {@link RestResponseHeaders} as headers,
 * a typed body {@code T}, and {@link RestResponseError} as the error container.
 * Use the static factory methods to construct instances — do not call the constructor directly.</p>
 *
 * <p>On {@link ResponseStatus#SUCCESS}: {@code body} is non-null, {@code error} is {@code null}.<br>
 * On {@link ResponseStatus#ERROR}: {@code error} is non-null, {@code body} is {@code null}.</p>
 *
 * @param <T> the type of the domain object carried in the response body
 *
 * @since 2018-01-01
 */
@Schema(description = "Response wrapper object for single results")
public class RestResponse<T> extends GenericRestResponse<RestResponseHeaders, T, RestResponseError>
{
	/**
	 * Protected constructor; use the static factory methods to create instances.
	 *
	 * @param headers {@link RestResponseHeaders} the response metadata headers; should not be {@code null}
	 * @param body    {@code T} the response body; non-null for success responses, {@code null} for errors
	 * @param error   {@link RestResponseError} the error detail; non-null for error responses, {@code null} for success
	 * @param status  {@link ResponseStatus} the overall request outcome; must not be {@code null}
	 */
	protected RestResponse(RestResponseHeaders headers, T body, RestResponseError error, ResponseStatus status)
	{
		super(headers, body, error, status);
	}

	/**
	 * Creates a success response with custom headers and a body.
	 *
	 * @param <T>     the type of the body object
	 * @param headers {@link RestResponseHeaders} custom response headers; must not be {@code null}
	 * @param body    {@code T} the domain data to return to the caller; may be {@code null} if the
	 *                operation succeeded with no result
	 * @return {@link RestResponse}{@code <T>} with {@link ResponseStatus#SUCCESS}, the given body,
	 *         and no error
	 */
	public static <T> RestResponse<T> successResponse(RestResponseHeaders headers, T body)
	{
		return new RestResponse<>(headers, body, null, ResponseStatus.SUCCESS);
	}

	/**
	 * Creates a success response with default headers and a body.
	 *
	 * <p>Equivalent to {@code successResponse(new RestResponseHeaders(), body)}.</p>
	 *
	 * @param <T>  the type of the body object
	 * @param body {@code T} the domain data to return to the caller; may be {@code null}
	 * @return {@link RestResponse}{@code <T>} with {@link ResponseStatus#SUCCESS}, the given body,
	 *         default headers, and no error
	 */
	public static <T> RestResponse<T> successResponse(T body)
	{
		return successResponse(new RestResponseHeaders(), body);
	}

	/**
	 * Creates an error response with custom headers and a structured error object.
	 *
	 * @param <T>     the declared body type (body will be {@code null})
	 * @param headers {@link RestResponseHeaders} custom response headers; must not be {@code null}
	 * @param error   {@link RestResponseError} the error detail; must not be {@code null}
	 * @return {@link RestResponse}{@code <T>} with {@link ResponseStatus#ERROR}, no body,
	 *         and the given error
	 */
	public static <T> RestResponse<T> errorResponse(RestResponseHeaders headers, RestResponseError error)
	{
		return new RestResponse<>(headers, null, error, ResponseStatus.ERROR);
	}

	/**
	 * Creates an error response with default headers and a structured error object.
	 *
	 * @param <T>   the declared body type (body will be {@code null})
	 * @param error {@link RestResponseError} the error detail; must not be {@code null}
	 * @return {@link RestResponse}{@code <T>} with {@link ResponseStatus#ERROR}, no body,
	 *         default headers, and the given error
	 */
	public static <T> RestResponse<T> errorResponse(RestResponseError error)
	{
		return errorResponse(new RestResponseHeaders(), error);
	}

	/**
	 * Creates an error response with default headers and a plain error message.
	 *
	 * @param <T>          the declared body type (body will be {@code null})
	 * @param errorMessage String the human-readable error description; must not include PHI
	 * @return {@link RestResponse}{@code <T>} with {@link ResponseStatus#ERROR}, no body,
	 *         default headers, and a {@link RestResponseError} wrapping the message
	 */
	public static <T> RestResponse<T> errorResponse(String errorMessage)
	{
		return errorResponse(new RestResponseError(errorMessage));
	}

	/**
	 * Creates an error response with default headers, a plain error message, and additional data.
	 *
	 * @param <T>          the declared body type (body will be {@code null})
	 * @param errorMessage String the human-readable error description; must not include PHI
	 * @param data         {@link Serializable} optional additional context about the error;
	 *                     may be {@code null}; must not contain PHI
	 * @return {@link RestResponse}{@code <T>} with {@link ResponseStatus#ERROR}, no body,
	 *         default headers, and a {@link RestResponseError} wrapping both the message and data
	 */
	public static <T> RestResponse<T> errorResponse(String errorMessage, Serializable data)
	{
		return errorResponse(new RestResponseError(errorMessage, data));
	}
}
