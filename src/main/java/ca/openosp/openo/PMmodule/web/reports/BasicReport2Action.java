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

package ca.openosp.openo.PMmodule.web.reports;

import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.service.ProviderManager;
import ca.openosp.openo.utility.SpringUtils;

/**
 * Will report some basic statistics out of the PMM
 * <p>
 * 1) # of programs
 * 2) # of service programs
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class BasicReport2Action extends ActionSupport {
    private HttpServletRequest request = ServletActionContext.getRequest();

    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
    private ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);

    public String execute() {
        return form();
    }

    public String form() {

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -12);

        request.setAttribute("programStatistics", this.getProgramStatistics());
        request.setAttribute("providerStatistics", this.getProviderStatistics());


        return "form";
    }

    protected Map getProgramStatistics() {
        Map map = new LinkedHashMap();
        int total = 0, totalService = 0;

        List programs = programManager.getPrograms();

        for (Iterator iter = programs.iterator(); iter.hasNext(); ) {
            Program p = (Program) iter.next();
            if (p.getType().equalsIgnoreCase("service")) {
                totalService++;
            }
            total++;
        }

        map.put("Total number of programs", Long.valueOf(total));
        map.put("Total number of service programs", Long.valueOf(totalService));
        return map;
    }

    protected Map getProviderStatistics() {
        Map map = new LinkedHashMap();

        map.put("Total number of providers", Long.valueOf(providerManager.getProviders().size()));
		/*
		List roles = roleManager.getRoles();
		for(Iterator iter=roles.iterator();iter.hasNext();) {
			Role role = (Role)iter.next();
			
		}
		*/
        return map;
    }
}
