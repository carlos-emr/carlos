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
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.io.Serializable;

@Schema(description = "Generic response wrapper object")
public class GenericRestResponse<H, B, E> implements Serializable
{
	public enum ResponseStatus
	{
		SUCCESS, ERROR
	}

	private final H headers;
	private final B body;
	private final E error;
	private final ResponseStatus status;

	protected GenericRestResponse(H headers, B body, E error, ResponseStatus status)
	{
		this.headers = headers;
		this.body= body;
		this.error = error;
		this.status = status;
	}

	public H getHeaders()
	{
		return headers;
	}

	public B getBody()
	{
		return body;
	}

	public E getError()
	{
		return error;
	}

	public ResponseStatus getStatus()
	{
		return status;
	}

	/**
	 * override the toString on this object to write it as a JSON object string
	 */
	@Override
	public String toString()
	{
		return ReflectionToStringBuilder.toString(this);
	}
}
