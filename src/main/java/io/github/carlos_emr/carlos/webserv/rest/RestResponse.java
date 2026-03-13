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
 */

package org.oscarehr.ws.rest.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "Response wrapper object for single results")
public class RestResponse<T> extends GenericRestResponse<RestResponseHeaders, T, RestResponseError>
{
	protected RestResponse(RestResponseHeaders headers, T body, RestResponseError error, ResponseStatus status)
	{
		super(headers, body, error, status);
	}

	public static <T> RestResponse<T> successResponse(RestResponseHeaders headers, T body)
	{
		return new RestResponse<>(headers, body, null, ResponseStatus.SUCCESS);
	}

	public static <T> RestResponse<T> successResponse(T body)
	{
		return successResponse(new RestResponseHeaders(), body);
	}

	public static <T> RestResponse<T> errorResponse(RestResponseHeaders headers, RestResponseError error)
	{
		return new RestResponse<>(headers, null, error, ResponseStatus.ERROR);
	}

	public static <T> RestResponse<T> errorResponse(RestResponseError error)
	{
		return errorResponse(new RestResponseHeaders(), error);
	}

	public static <T> RestResponse<T> errorResponse(String errorMessage)
	{
		return errorResponse(new RestResponseError(errorMessage));
	}

	public static <T> RestResponse<T> errorResponse(String errorMessage, Serializable data)
	{
		return errorResponse(new RestResponseError(errorMessage, data));
	}
}
