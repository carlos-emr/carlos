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

package ca.openosp.openo.encounter.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.OscarCommLocationsDao;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class EctViewAttachment2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    public String execute() throws IOException, ServletException {
        OscarCommLocationsDao dao = SpringUtils.getBean(OscarCommLocationsDao.class);
        for (Object[] o : dao.findFormLocationByMesssageId(mesId)) {
            String thesubject = String.valueOf(o[0]);
            String theime = String.valueOf(o[1]);
            String thedate = String.valueOf(o[2]);
            String attachment = String.valueOf(o[3]);
            String themessage = String.valueOf(o[4]);
            String sentBy = String.valueOf(o[5]);
            String remoteName = null;

            request.setAttribute("remoteName", remoteName);
            request.setAttribute("themessage", themessage);
            request.setAttribute("theime", theime);
            request.setAttribute("thedate", thedate);
            request.setAttribute("attachment", attachment);
            request.setAttribute("thesubject", thesubject);
            request.setAttribute("sentBy", sentBy);
        }

        return SUCCESS;
    }

    String mesId;

    ////mesId///////////////////////////////////////////////////////////////////
    public String getMesId() {
        if (this.mesId == null) {
            this.mesId = new String();
        }
        return this.mesId;
    }

    public void setMesId(String str) {
        this.mesId = str;
    }
}
