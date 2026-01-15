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
package ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.InstitutitionDepartmentDao;
import ca.openosp.openo.commn.model.InstitutionDepartment;
import ca.openosp.openo.commn.model.InstitutionDepartmentPK;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctConDisplayInstitution2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private InstitutitionDepartmentDao dao = SpringUtils.getBean(InstitutitionDepartmentDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String id = this.getId();
        String specialists[] = this.getSpecialists();

        for (InstitutionDepartment s : dao.findByInstitutionId(Integer.parseInt(id))) {
            dao.remove(s.getId());
        }
        for (int i = 0; i < specialists.length; i++) {
            InstitutionDepartment ss = new InstitutionDepartment();
            ss.setId(new InstitutionDepartmentPK(Integer.parseInt(id), Integer.parseInt(specialists[i])));
            dao.persist(ss);
        }


        // EctConConstructSpecialistsScriptsFile constructSpecialistsScriptsFile = new EctConConstructSpecialistsScriptsFile();
        // constructSpecialistsScriptsFile.makeString(request.getLocale());
        return SUCCESS;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String[] getSpecialists() {
        if (specialists == null)
            specialists = new String[0];
        return specialists;
    }

    public void setSpecialists(String str[]) {
        specialists = str;
    }

    String id;
    String specialists[];
}
