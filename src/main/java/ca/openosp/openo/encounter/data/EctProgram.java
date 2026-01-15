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

package ca.openosp.openo.encounter.data;

import java.util.List;

import javax.servlet.http.HttpSession;

import ca.openosp.openo.util.LabelValueBean;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
/**
 *
 * @author rjonasz
 */
public class EctProgram {
    private HttpSession se;
    /** Creates a new instance of EctProgram */
    public EctProgram(HttpSession se) {
        this.se = se;
    }

    public String getProgram(String providerNo) {
        EctProgramManager manager = getEctProgramManager();
        List<LabelValueBean> programBeans = manager.getProgramBeans(providerNo, null);

        // If no programs available, return 0
        if (programBeans.isEmpty()) {
            return "0";
        }

        // Get provider's default program ID
        int defaultProgramId = manager.getDefaultProgramId(providerNo);

        // Check if default program exists in available programs
        boolean defaultExists = false;
        for (LabelValueBean bean : programBeans) {
            int id = NumberUtils.toInt(bean.getValue(), 0);
            if (id == defaultProgramId) {
                defaultExists = true;
                break;
            }
        }

        // Return default if it exists in list, otherwise return first available program
        if (defaultExists) {
            return String.valueOf(defaultProgramId);
        } else {
            int firstProgramId = NumberUtils.toInt(programBeans.get(0).getValue(), 0);
            return String.valueOf(firstProgramId);
        }
    }

    public ApplicationContext getAppContext() {
        return WebApplicationContextUtils.getWebApplicationContext(
        		se.getServletContext());
    }

    public EctProgramManager getEctProgramManager() {
		EctProgramManager manager = (EctProgramManager) getAppContext()
				.getBean("ectProgramManager");
		return manager;
    }
}
