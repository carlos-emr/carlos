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

package ca.openosp.openo.webserv.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ca.openosp.openo.webserv.oauth.AbstractServiceImpl;
import org.owasp.csrfguard.CsrfGuard;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/csrf")
@Component("csrfTokenService")
@Consumes(MediaType.APPLICATION_JSON)
public class CsrfTokenService extends AbstractServiceImpl {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @POST
    @Path("/getToken")
    @Produces("application/json")
    public String getToken() {
        CsrfGuard csrfGuard = CsrfGuard.getInstance();
        ObjectNode token = objectMapper.createObjectNode();
        token.put("name", csrfGuard.getTokenName());
        token.put("value", csrfGuard.getTokenValue(getHttpServletRequest()));
        return token.toString();
    }
}
