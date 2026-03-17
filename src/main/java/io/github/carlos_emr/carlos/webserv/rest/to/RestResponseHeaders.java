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
import io.github.carlos_emr.OscarProperties;

import java.io.Serializable;

/**
 * Serializable DTO representing metadata headers included in every REST API response.
 *
 * <p>Contains build information sourced from {@link OscarProperties} at construction time,
 * allowing API consumers to correlate responses with specific CARLOS EMR build versions.
 * Instances are immutable after construction.</p>
 *
 * @since 2026-03-13
 */
@Schema(description = "Generic response header object")
public class RestResponseHeaders implements Serializable
{
	private final String buildDate;
	private final String buildTag;

	/**
	 * Constructs response headers populated from {@link OscarProperties}.
	 *
	 * <p>Reads {@code buildDate} from {@link OscarProperties#getBuildDate()} and
	 * {@code buildTag} from {@link OscarProperties#getBuildTag()} at construction time.
	 * The values reflect the current application build metadata and may be {@code null}
	 * if the properties are not configured.</p>
	 */
	public RestResponseHeaders()
	{
		this.buildDate = OscarProperties.getBuildDate();
		this.buildTag = OscarProperties.getBuildTag();
	}

	/**
	 * Returns the application build date string.
	 *
	 * @return String the build date as provided by {@link OscarProperties#getBuildDate()};
	 *         may be {@code null} if the build date property is not set
	 */
	public String getBuildDate()
	{
		return buildDate;
	}

	/**
	 * Returns the application build tag string.
	 *
	 * @return String the build tag (e.g., a version label or git tag) as provided by
	 *         {@link OscarProperties#getBuildTag()}; may be {@code null} if the property is not set
	 */
	public String getBuildTag()
	{
		return buildTag;
	}
}
