//CHECKSTYLE:OFF
/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 */

package ca.openosp.openo.commn.web;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.dao.SiteDao;
import ca.openosp.openo.commn.model.Site;
import ca.openosp.openo.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class SitesManage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SiteDao siteDao = SpringUtils.getBean(SiteDao.class);

    @Override
    public String execute() throws Exception {
        if ("add".equals(request.getParameter("method"))) {
            return add();
        }
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        if ("update".equals(request.getParameter("method"))) {
            return update();
        }
        return view();
    }

    public String view() {
        List<Site> sites = siteDao.getAllSites();

        request.setAttribute("sites", sites);
        return "list";
    }

    public String add() {

        Site s = new Site();
        this.setS(s);

        return "details";
    }

    public String save() {
        Site s = this.getS();

        // verify mandatories
        if (StringUtils.isBlank(s.getName()) || StringUtils.isBlank(s.getShortName())) {
            addActionMessage(getText("errors.required", "Site name or short name"));
        }
        if (StringUtils.isBlank(s.getBgColor())) {
            addActionMessage(getText("errors.required", "Theme color"));
        }

        siteDao.save(s);

        return view();
    }

    public String update() throws Exception {

        String siteId = request.getParameter("siteId");
        Site s = siteDao.getById(Integer.valueOf(siteId));

        this.setS(s);
        return "details";
    }

    private Site s;

    public Site getS() {
        return s;
    }

    public void setS(Site s) {
        this.s = s;
    }
}
