/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The JSON replies of the personal footer editor window (issue #3981, Ben's demo of 15 Oct 2026):
 * what Preferences and the email screen read and save the user's own footer with. Built with
 * Jackson, never by joining strings; never cached, never sniffed. An error reply carries a fixed
 * code and no footer text.
 */
final class FooterJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FooterJson() {
    }

    /** @return an empty reply body to fill */
    static ObjectNode body() {
        return MAPPER.createObjectNode();
    }

    /**
     * Writes {@code body} as the whole reply.
     *
     * @param response the reply
     * @param status the HTTP status
     * @param body the JSON object
     * @throws IOException when the reply cannot be written
     */
    static void write(HttpServletResponse response, int status, ObjectNode body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    /**
     * Writes a refusal: a fixed code, nothing the user typed.
     *
     * @param response the reply
     * @param status the HTTP status
     * @param code the fixed error code
     * @throws IOException when the reply cannot be written
     */
    static void error(HttpServletResponse response, int status, String code) throws IOException {
        ObjectNode body = body();
        body.put("error", code);
        write(response, status, body);
    }
}
