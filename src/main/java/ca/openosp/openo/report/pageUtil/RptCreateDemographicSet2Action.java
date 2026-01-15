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


package ca.openosp.openo.report.pageUtil;

import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import ca.openosp.openo.report.data.DemographicSets;

/**
 * @author Jay Gallagher
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class RptCreateDemographicSet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();



    public RptCreateDemographicSet2Action() {
    }

    public String execute() {

        String setName = request.getParameter("setName");
        String sizestr = request.getParameter("size");
        ArrayList list = new ArrayList();
        int size = Integer.parseInt(sizestr);

        for (int i = 0; i < size; i++) {
            list.add(request.getParameter("demoNo" + i));
        }

        if (list.size() > 0) {
            DemographicSets demoSet = new DemographicSets();
            demoSet.addDemographicSet(setName, list);
        }
        return SUCCESS;
    }
}
