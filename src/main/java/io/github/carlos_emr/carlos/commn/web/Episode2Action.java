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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.web;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.EpisodeDao;
import io.github.carlos_emr.carlos.commn.model.Episode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.BeanUtils;

import io.github.carlos_emr.CarlosProperties;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class Episode2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private EpisodeDao episodeDao = SpringUtils.getBean(EpisodeDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        if ("edit".equals(request.getParameter("method"))) {
            return edit();
        }
        return this.list();
    }

    public String list() {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        List<Episode> episodes = episodeDao.findAll(demographicNo);
        request.setAttribute("episodes", episodes);
        return "list";
    }

    public String edit() {
        String id = request.getParameter("episode.id");

        if (id != null) {
            Episode e = episodeDao.find(Integer.valueOf(id));
            request.setAttribute("episode", e);
        }

        String[] codingSystems = CarlosProperties.getInstance().getProperty("dxResearch_coding_sys", "").split(",");
        List<String> cs = Arrays.asList(codingSystems);
        request.setAttribute("codingSystems", cs);
        request.setAttribute("demographicNo", request.getParameter("demographicNo"));
        return "form";
    }

    public String save() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        Integer id = null;
        try {
            id = Integer.parseInt(request.getParameter("episode.id"));
        } catch (NumberFormatException e) {/*empty*/}
        Episode e = null;
        if (id != null && id.intValue() > 0) {
            e = episodeDao.find(Integer.valueOf(id));
        } else {
            e = new Episode();
        }
        BeanUtils.copyProperties(episode, e, new String[]{"id", "lastUpdateTime", "lastUpdateUser"});
        e.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", e.getDemographicNo())) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (id != null && id.intValue() > 0) {
            episodeDao.merge(e);
        } else {
            episodeDao.persist(e);
        }
        request.setAttribute("parentAjaxId", "episode");
        return SUCCESS;
    }

    private Episode episode;

    @StrutsParameter(depth = 1)
    public Episode getEpisode() {
        return episode;
    }

    @StrutsParameter
    public void setEpisode(Episode episode) {
        this.episode = episode;
    }
}
