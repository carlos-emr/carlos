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


package ca.openosp.openo.dxresearch.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.dxresearch.bean.dxQuickListItemsHandler;
import ca.openosp.openo.dxresearch.util.dxResearchCodingSystem;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class dxResearchLoadQuickListItems2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dxresearch", "r", null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }

        //dxResearchLoadQuickListItemsForm frm = (dxResearchLoadQuickListItemsForm) form;
        //request.getSession().setAttribute("dxResearchLoadQuickListItemsFrm", frm);
        String quickListName = getQuickListName();

        dxResearchCodingSystem codingSys = new dxResearchCodingSystem();

        dxQuickListItemsHandler quicklistItemsHd = new dxQuickListItemsHandler(quickListName);

        HttpSession session = request.getSession();
        session.setAttribute("codingSystem", codingSys);
        session.setAttribute("allQuickListItems", quicklistItemsHd);
        session.setAttribute("quickListName", quickListName);

        return SUCCESS;
    }

    private String quickListName;

    public String getQuickListName() {
        return quickListName;
    }

    public void setQuickListName(String quickListName) {
        this.quickListName = quickListName;
    }
}
