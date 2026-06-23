/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class JsonResponseWriter {
    public static final String CONTENT_TYPE = "application/json; charset=UTF-8";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonResponseWriter() {
    }

    // FindSecBugs XSS_SERVLET: centralized JSON response writer; callers provide JSON strings or Jackson-serialized objects.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "centralized JSON response writer with application/json content type")
    public static void write(HttpServletResponse response, Object body) throws IOException {
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String json = body instanceof String stringBody ? stringBody : OBJECT_MAPPER.writeValueAsString(body);
        response.getWriter().write(json); // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep, java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- centralized JSON API response writer with application/json content type
    }
}
