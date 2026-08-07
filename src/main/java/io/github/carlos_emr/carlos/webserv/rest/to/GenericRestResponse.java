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

import java.io.Serializable;

/**
 * Generic immutable wrapper for all REST API responses in CARLOS EMR.
 *
 * <p>Carries three independent payloads typed by the generic parameters, allowing each
 * concrete subclass to specialise the types it needs while sharing serialization and
 * status semantics:</p>
 * <ul>
 *   <li><strong>H</strong> — header metadata (e.g., build date/tag); populated on every response</li>
 *   <li><strong>B</strong> — the response body (domain data); non-null on {@link ResponseStatus#SUCCESS}</li>
 *   <li><strong>E</strong> — error details; non-null on {@link ResponseStatus#ERROR}</li>
 * </ul>
 *
 * <p>Instances are immutable after construction. This class is not thread-safe by itself, but
 * since all fields are final and types are expected to be immutable DTOs, shared reads are safe.</p>
 *
 * <p>Use the concrete subclass {@link RestResponse} and its static factory methods rather than
 * instantiating this class directly.</p>
 *
 * @param <H> the type of the response headers object
 * @param <B> the type of the response body object (populated on success)
 * @param <E> the type of the response error object (populated on error)
 *
 * @since 2026-03-13
 */
public class GenericRestResponse<H, B, E> implements Serializable
{
	/**
	 * Indicates the overall outcome of the REST operation.
	 *
	 * <ul>
	 *   <li>{@link #SUCCESS} — the request completed successfully; the body is populated and
	 *       the error is {@code null}.</li>
	 *   <li>{@link #ERROR} — the request failed; the error is populated and
	 *       the body is {@code null}.</li>
	 * </ul>
	 */
	public enum ResponseStatus
	{
		SUCCESS, ERROR
	}

	private final H headers;
	private final B body;
	private final E error;
	private final ResponseStatus status;

	/**
	 * Constructs a generic REST response with all fields set.
	 *
	 * <p>For {@link ResponseStatus#SUCCESS} responses, {@code body} should be non-null and
	 * {@code error} should be {@code null}. For {@link ResponseStatus#ERROR} responses,
	 * {@code error} should be non-null and {@code body} should be {@code null}.</p>
	 *
	 * @param headers {@code H} the response header object; should not be {@code null}
	 * @param body    {@code B} the response body; non-null for success, {@code null} for error
	 * @param error   {@code E} the error detail; non-null for error, {@code null} for success
	 * @param status  {@link ResponseStatus} the overall request outcome; must not be {@code null}
	 */
	protected GenericRestResponse(H headers, B body, E error, ResponseStatus status)
	{
		if (status == null) {
			throw new IllegalArgumentException("status must not be null");
		}
		if (status == ResponseStatus.SUCCESS && error != null) {
			throw new IllegalArgumentException("error must be null for SUCCESS responses");
		}
		if (status == ResponseStatus.ERROR && body != null) {
			throw new IllegalArgumentException("body must be null for ERROR responses");
		}
		this.headers = headers;
		this.body = body;
		this.error = error;
		this.status = status;
	}

	/**
	 * Returns the response header metadata.
	 *
	 * @return {@code H} the header object; non-null in normal usage
	 */
	public H getHeaders()
	{
		return headers;
	}

	/**
	 * Returns the response body containing domain data.
	 *
	 * @return {@code B} the body object; non-null on {@link ResponseStatus#SUCCESS},
	 *         {@code null} on {@link ResponseStatus#ERROR}
	 */
	public B getBody()
	{
		return body;
	}

	/**
	 * Returns the response error details.
	 *
	 * @return {@code E} the error object; non-null on {@link ResponseStatus#ERROR},
	 *         {@code null} on {@link ResponseStatus#SUCCESS}
	 */
	public E getError()
	{
		return error;
	}

	/**
	 * Returns the overall status of this response.
	 *
	 * @return {@link ResponseStatus} indicating {@code SUCCESS} or {@code ERROR}; never {@code null}
	 */
	public ResponseStatus getStatus()
	{
		return status;
	}

	/**
	 * Returns a concise, non-sensitive summary of this response for logging purposes.
	 *
	 * <p>Deliberately does not include body or error content to prevent PHI leakage
	 * in application logs.</p>
	 *
	 * @return String a summary string containing only the response status
	 */
	@Override
	public String toString()
	{
		return "GenericRestResponse{status=" + status + "}";
	}
}
