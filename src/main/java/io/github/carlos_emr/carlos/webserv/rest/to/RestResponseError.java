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
 * Immutable error detail object carried in REST error responses.
 *
 * <p>Contains a human-readable error message and an optional serializable data payload
 * that may provide additional context (e.g., a list of invalid field names). Neither
 * field should contain PHI (Patient Health Information).</p>
 *
 * <p>Instances are immutable after construction and safe for shared reads across threads.</p>
 *
 * @since 2018-01-01
 */
@Schema(description = "Response wrapper object for error information")
public class RestResponseError implements Serializable
{
	private final String message;
	private final Serializable data;

	/**
	 * Constructs an error with no message and no additional data.
	 *
	 * <p>Both {@link #getMessage()} and {@link #getData()} will return {@code null}.</p>
	 */
	public RestResponseError()
	{
		this(null, null);
	}

	/**
	 * Constructs an error with a descriptive message and no additional data.
	 *
	 * @param message String a human-readable description of the error; may be {@code null};
	 *                must not contain PHI
	 */
	public RestResponseError(String message)
	{
		this(message, null);
	}

	/**
	 * Constructs an error with a descriptive message and optional additional data.
	 *
	 * @param message String a human-readable description of the error; may be {@code null};
	 *                must not contain PHI
	 * @param data    {@link Serializable} optional supplementary context about the error
	 *                (e.g., field names that failed validation); may be {@code null};
	 *                must not contain PHI
	 */
	public RestResponseError(String message, Serializable data)
	{
		this.message = message;
		this.data = data;
	}

	/**
	 * Returns the human-readable error description.
	 *
	 * @return String the error message; may be {@code null} if none was provided
	 */
	public String getMessage()
	{
		return message;
	}

	/**
	 * Returns optional supplementary context about the error.
	 *
	 * @return {@link Serializable} additional error data; may be {@code null} if none was provided;
	 *         the concrete type is determined by the endpoint that produced this error
	 */
	public Serializable getData()
	{
		return this.data;
	}
}
