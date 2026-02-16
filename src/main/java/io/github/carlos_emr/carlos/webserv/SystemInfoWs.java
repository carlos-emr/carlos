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

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.webserv;

import javax.jws.WebService;

import org.apache.cxf.annotations.GZIP;
import org.springframework.stereotype.Component;

/**
 * Unauthenticated SOAP endpoint for basic health checks only.
 *
 * <p>This service is intentionally minimal. Methods that previously exposed
 * system internals (server time, timezone offset, max list size, timestamps)
 * have been removed because this endpoint requires no authentication and
 * that information aids reconnaissance attacks.</p>
 */
@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class SystemInfoWs extends AbstractWs {

    /**
     * Basic health check indicating the service is running.
     *
     * @return the string "alive"
     */
    public String isAlive() {
        return ("alive");
    }
}
