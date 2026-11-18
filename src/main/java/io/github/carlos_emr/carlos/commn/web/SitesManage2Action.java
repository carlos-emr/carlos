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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Administration ▸ Satellite-sites Admin ({@code admin/ManageSites}, shown with {@code multisites=on}):
 * lists the sites, opens one for editing, and adds or saves one.
 *
 * <p>The detail form posts its fields as {@code site.*}, which bind to {@link #getSite()}. The list opens a
 * site with a GET link, so opening it is the read-only {@code method=edit}; {@code HttpMethodGuardFilter}
 * refuses GET for {@code method=update} and {@code method=save}, and {@link #save()} refuses anything but
 * POST on its own as well.
 */
public class SitesManage2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SiteDao siteDao = SpringUtils.getBean(SiteDao.class);

    /** The result that renders the site form (sitesAdminDetail.jsp). */
    private static final String DETAILS = "details";

    /** Bundle keys of the labels of the required fields a save found empty; the form names each one. */
    private final List<String> missingFields = new ArrayList<>();

    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String method = request.getParameter("method");
        if ("add".equals(method)) {
            return add();
        }
        if ("save".equals(method)) {
            return save();
        }
        // "edit" is the list's GET link; "update" is the older name, which the GET guard only lets through on POST.
        if ("edit".equals(method) || "update".equals(method)) {
            return edit();
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
        this.setSite(s);

        return DETAILS;
    }

    /**
     * Stores the posted site: a new one, or the one whose id the form carries. A missing name, short name or
     * theme colour returns to the form with the posted values and stores nothing.
     */
    public String save() throws IOException {
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }
        Site s = this.getSite();
        if (s == null) {
            s = new Site();
            this.setSite(s);
        }

        // verify mandatories
        if (StringUtils.isBlank(s.getName())) {
            missingFields.add("admin.sitesAdminDetail.label.siteName");
        }
        if (StringUtils.isBlank(s.getShortName())) {
            missingFields.add("admin.sitesAdminDetail.label.shortName");
        }
        if (StringUtils.isBlank(s.getBgColor())) {
            missingFields.add("admin.sitesAdminDetail.label.themeColor");
        }
        if (!missingFields.isEmpty()) {
            return DETAILS;
        }

        if (s.getSiteId() != null) {
            Site stored = siteDao.getById(s.getSiteId());
            if (stored == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Site not found");
                return NONE;
            }
            // The form does not carry these, and the saved site replaces the stored row: keep the stored values.
            // SiteDao.save reloads the site's providers itself.
            s.setAddress(stored.getAddress());
            s.setCity(stored.getCity());
            s.setSiteLogoId(stored.getSiteLogoId());
            s.setSiteUrl(stored.getSiteUrl());
            if (!IsPropertiesOn.isProviderFormalizeEnable()) {
                // The provider-id range fields are on the form only while provider-id formalizing is on.
                s.setProviderIdFrom(stored.getProviderIdFrom());
                s.setProviderIdTo(stored.getProviderIdTo());
            }
        }

        siteDao.save(s);

        return view();
    }

    /** Opens the site named by {@code siteId} in the detail form, filled with its stored values. */
    public String edit() throws IOException {
        Integer siteId;
        try {
            siteId = Integer.valueOf(StringUtils.trimToEmpty(request.getParameter("siteId")));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid site");
            return NONE;
        }
        Site s = siteDao.getById(siteId);
        if (s == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Site not found");
            return NONE;
        }

        this.setSite(s);
        return DETAILS;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    private Site site;

    @StrutsParameter(depth = 1)
    public Site getSite() {
        return site;
    }

    @StrutsParameter
    public void setSite(Site site) {
        this.site = site;
    }
}
