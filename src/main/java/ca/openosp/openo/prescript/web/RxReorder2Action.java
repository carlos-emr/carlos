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

package ca.openosp.openo.prescript.web;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.DrugDao;
import ca.openosp.openo.commn.model.Drug;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class RxReorder2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        return update();
    }

    public String update() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "u", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        String demographicNo = request.getParameter("demographicNo");
        int drugId = Integer.parseInt(request.getParameter("drugId"));
        int swapDrugId = Integer.parseInt(request.getParameter("swapDrugId"));

        CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
        List<Drug> drugs = caseManagementManager.getPrescriptions(demographicNo, true);
        DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);

        Drug myDrug = null;
        Drug swapDrug = null;

        for (Drug drug : drugs) {
            if (drug.getId().intValue() == drugId) {
                myDrug = drug;
            }
            if (drug.getId().intValue() == swapDrugId) {
                swapDrug = drug;
            }
        }

        if (myDrug == null || swapDrug == null) {
            MiscUtils.getLogger().warn("Couldn't find the drugs to swap!");
        } else {
            int myPosition = myDrug.getPosition();
            int swapPosition = swapDrug.getPosition();
            myDrug.setPosition(swapPosition);
            swapDrug.setPosition(myPosition);
            drugDao.merge(myDrug);
            drugDao.merge(swapDrug);
        }

        try {
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().println("ok");
        } catch (IOException e) {
            logger.error("error", e);
        }
        return null;
    }
}
