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
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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
    public String execute() throws IOException {
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        if ("edit".equals(request.getParameter("method"))) {
            return edit();
        }
        return this.list();
    }

    public String list() throws IOException {
        Integer demographicNo = positiveInteger(request.getParameter("demographicNo"));
        if (demographicNo == null) return reject(400, "Invalid patient identifier");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        List<Episode> episodes = episodeDao.findAll(demographicNo);
        request.setAttribute("episodes", episodes);
        return "list";
    }

    public String edit() throws IOException {
        Integer demographicNo = positiveInteger(request.getParameter("demographicNo"));
        if (demographicNo == null) return reject(400, "Invalid patient identifier");
        requirePatientAccess(demographicNo, "r");
        String rawId = request.getParameter("episode.id");
        if (rawId != null && !rawId.isBlank()) {
            Integer id = positiveInteger(rawId);
            if (id == null) return reject(400, "Invalid episode identifier");
            Episode stored = episodeDao.find(id);
            if (stored == null || stored.getDemographicNo() != demographicNo) {
                return reject(404, "Episode not found");
            }
            request.setAttribute("episode", stored);
        }
        String[] codingSystems = CarlosProperties.getInstance().getProperty("dxResearch_coding_sys", "").split(",");
        request.setAttribute("codingSystems", Arrays.asList(codingSystems));
        request.setAttribute("demographicNo", demographicNo.toString());
        return "form";
    }

    public String save() throws IOException {
        if (!"POST".equals(request.getMethod())) return reject(405, "POST required");
        if (episode == null || episode.getDemographicNo() <= 0) return reject(400, "Invalid patient identifier");
        requirePatientAccess(episode.getDemographicNo(), "w");
        String rawId = request.getParameter("episode.id");
        boolean creating = rawId == null || rawId.isBlank() || "0".equals(rawId);
        Integer id = creating ? null : positiveInteger(rawId);
        if (!creating && id == null) return reject(400, "Invalid episode identifier");
        Episode stored = creating ? new Episode() : episodeDao.find(id);
        if (stored == null || (!creating && stored.getDemographicNo() != episode.getDemographicNo())) {
            return reject(404, "Episode not found");
        }
        if (!validEpisode()) return reject(400, "Check the episode description, status and dates");

        // Do not copy bound properties onto a managed entity until ownership,
        // permission and validation have all succeeded. Patient identity is immutable.
        if (creating) stored.setDemographicNo(episode.getDemographicNo());
        stored.setStartDate(episode.getStartDate());
        stored.setEndDate(episode.getEndDate());
        stored.setCode(episode.getCode());
        stored.setCodingSystem(episode.getCodingSystem());
        stored.setDescription(episode.getDescription());
        stored.setStatus(episode.getStatus());
        stored.setNotes(episode.getNotes());
        stored.setLastUpdateUser(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo());
        if (creating) episodeDao.persist(stored);
        else episodeDao.merge(stored);
        request.setAttribute("parentAjaxId", "episode");
        return SUCCESS;
    }

    private boolean validEpisode() {
        if (episode.getDescription() == null || episode.getDescription().isBlank() || episode.getStartDate() == null) return false;
        if (episode.getStatus() == null || !List.of("Current", "Complete", "Deleted").contains(episode.getStatus())) return false;
        if ("Complete".equals(episode.getStatus()) && episode.getEndDate() == null) return false;
        if (episode.getEndDate() != null && episode.getEndDate().before(episode.getStartDate())) return false;
        return validDateInput("episode.startDateStr", episode.getStartDateStr())
                && validDateInput("episode.endDateStr", episode.getEndDateStr());
    }

    private boolean validDateInput(String parameter, String formatted) {
        String value = request.getParameter(parameter);
        if (value == null) return true;
        if (value.isBlank()) return formatted.isEmpty();
        try {
            return LocalDate.parse(value).toString().equals(formatted);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private void requirePatientAccess(int demographicNo, String right) {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", right, demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
    }

    private static Integer positiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String reject(int status, String message) throws IOException {
        response.sendError(status, message);
        return NONE;
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
