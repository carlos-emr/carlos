//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package ca.openosp.openo.form;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public abstract class JSONUtil {

    private static final String ENCODING = "UTF-8";
    private static final String CONTENT_TYPE = "application/json";
    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void jsonResponse(HttpServletResponse response, ObjectNode jsonObject) {
        try (PrintWriter out = response.getWriter()) {
            response.setContentType(CONTENT_TYPE);
            response.setCharacterEncoding(ENCODING);
            out.print(jsonObject.toString());
            out.flush();
        } catch (IOException e) {
            logger.error("Error while creating JSON response", e);
        }
    }

    public static void jsonResponse(HttpServletResponse response, String jsonString) {
        try (PrintWriter out = response.getWriter()) {
            response.setContentType(CONTENT_TYPE);
            response.setCharacterEncoding(ENCODING);
            out.print(jsonString);
            out.flush();
        } catch (IOException e) {
            logger.error("Error while creating JSON response", e);
        }
    }

    public static void jsonResponse(HttpServletResponse response, String name, String value) {
        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put(name, value);
        jsonResponse(response, jsonObject);
    }

    public static void errorResponse(HttpServletResponse response, String name, String value) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        jsonResponse(response, name, value);
    }
}
