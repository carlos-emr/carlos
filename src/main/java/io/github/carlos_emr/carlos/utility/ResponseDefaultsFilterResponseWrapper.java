/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Response wrapper used by {@link ResponseDefaultsFilter} to intercept and optionally
 * modify outgoing headers before they reach the client.
 *
 * <p>Two behaviours are controlled by constructor flags:
 * <ul>
 *   <li><b>forceStrongETag</b> — strips the {@code W/} prefix from weak ETags so that
 *       intermediate proxies treat them as strong validators. This works around reverse
 *       proxies that refuse to cache responses with weak ETags.</li>
 *   <li><b>warnCharsetCacheChange</b> — logs a warning (with stack trace) whenever
 *       downstream code changes the character encoding or cache-control headers after
 *       {@link ResponseDefaultsFilter} has already set them. Useful for diagnosing
 *       encoding conflicts in JSPs and actions.</li>
 * </ul>
 *
 * @since 2012 (OSCAR McMaster heritage)
 * @see ResponseDefaultsFilter
 */
public class ResponseDefaultsFilterResponseWrapper extends HttpServletResponseWrapper {
    private static Logger logger = MiscUtils.getLogger();
    private boolean forceStrongETag;
    private boolean warnCharsetCacheChange;

    /**
     * Wraps the given response with optional ETag and charset-change monitoring.
     *
     * @param response              HttpServletResponse the original response to wrap
     * @param forceStrongETag       boolean if {@code true}, weak ETag prefixes ({@code W/}) are stripped
     * @param warnCharsetCacheChange boolean if {@code true}, log warnings on encoding/cache changes
     */
    public ResponseDefaultsFilterResponseWrapper(HttpServletResponse response, boolean forceStrongETag, boolean warnCharsetCacheChange) {
        super(response);
        this.forceStrongETag = forceStrongETag;
        this.warnCharsetCacheChange = warnCharsetCacheChange;
    }

    /**
     * Logs a warning with a synthetic stack trace to identify the caller.
     *
     * @param message String the warning message to log
     */
    private static void warnWithStackTrace(String message) {
        logger.warn(message, new Exception(message));
    }

    /** {@inheritDoc} */
    public void setCharacterEncoding(String encoding) {
        super.setCharacterEncoding(encoding);
        if (this.warnCharsetCacheChange) {
            warnWithStackTrace("Some one is switching the encoding on me! : " + encoding);
        }

    }

    /** {@inheritDoc} */
    public void setContentType(String contentType) {
        super.setContentType(contentType);
        if (this.warnCharsetCacheChange && contentType.contains("charset")) {
            warnWithStackTrace("Some one is switching the encoding on me! : " + contentType);
        }

    }

    /**
     * Intercepts header writes to strip weak ETag prefixes and warn on encoding/cache changes.
     *
     * @param key   String the header name
     * @param value String the header value
     */
    public void setHeader(String key, String value) {
        if (this.forceStrongETag && "ETag".equals(key) && value != null && value.startsWith("W/")) {
            value = value.substring(2);
        }

        super.setHeader(key, value);
        if (this.warnCharsetCacheChange) {
            if ("Content-Type".equals(key) && value.contains("charset") && !value.contains("charset=UTF-8")) {
                warnWithStackTrace("Some one is switching the encoding : " + value);
            } else if ("Cache-Control".equals(key)) {
                warnWithStackTrace("Some one is setting the cache control. " + value);
            }
        }

    }
}