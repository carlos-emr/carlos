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

package io.github.carlos_emr.carlos.webserv;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.jws.WebService;

import org.apache.cxf.annotations.GZIP;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Component;

@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class SystemInfoWs extends AbstractWs {
    /**
     * System info www service hello world test method
     */
    public String helloWorld() {
        return ("Hello World! the configuration works! and your client works! :) " + (new java.util.Date()));
    }

    public String isAlive() {
        return ("alive");
    }

    public int getMaxListReturnSize() {
        return (AbstractDaoImpl.MAX_LIST_RETURN_SIZE);
    }

    /**
     * This returns the time on the oscar server
     */
    public Calendar getServerTime() {
        Calendar cal = new GregorianCalendar();
        return (cal);
    }

    public int getServerTimeGmtOffset() {
        TimeZone timeZone = TimeZone.getDefault();
        int offset = timeZone.getOffset(System.currentTimeMillis());
        return (offset);
    }
}
