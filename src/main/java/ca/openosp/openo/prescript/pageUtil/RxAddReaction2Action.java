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


package ca.openosp.openo.prescript.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class RxAddReaction2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    public String execute()
            throws IOException, ServletException {

        // Setup variables

        String id = request.getParameter("ID");
        String name = request.getParameter("name");
        String type = request.getParameter("type");
        String allergyToArchive = request.getParameter("allergyToArchive");
        String nkdaId = request.getParameter("nkdaId");


        request.setAttribute("drugrefId", id);
        request.setAttribute("name", name);
        request.setAttribute("type", type);
        request.setAttribute("allergyToArchive", allergyToArchive);
        request.setAttribute("nkdaId", nkdaId);


        return SUCCESS;
    }
}
