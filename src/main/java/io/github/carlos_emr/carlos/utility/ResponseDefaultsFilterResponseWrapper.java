//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class ResponseDefaultsFilterResponseWrapper extends HttpServletResponseWrapper {
    private static Logger logger = MiscUtils.getLogger();
    private boolean forceStrongETag;
    private boolean warnCharsetCacheChange;

    public ResponseDefaultsFilterResponseWrapper(HttpServletResponse response, boolean forceStrongETag, boolean warnCharsetCacheChange) {
        super(response);
        this.forceStrongETag = forceStrongETag;
        this.warnCharsetCacheChange = warnCharsetCacheChange;
    }

    private static void warnWithStackTrace(String message) {
        logger.warn(message, new Exception(message));
    }

    public void setCharacterEncoding(String encoding) {
        super.setCharacterEncoding(encoding);
        if (this.warnCharsetCacheChange) {
            warnWithStackTrace("Some one is switching the encoding on me! : " + encoding);
        }

    }

    public void setContentType(String contentType) {
        super.setContentType(contentType);
        if (this.warnCharsetCacheChange && contentType.contains("charset")) {
            warnWithStackTrace("Some one is switching the encoding on me! : " + contentType);
        }

    }

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