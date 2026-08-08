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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.appt.web;

import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.AppointmentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AppointmentType2Action extends ActionSupport {
    private static final String EDIT = "edit";
    private static final String SAVE = "save";
    private static final String DELETE = "del";
    private static final int NAME_MAX_LENGTH = 50;
    private static final int TEXT_MAX_LENGTH = 80;
    private static final int LOCATION_MAX_LENGTH = 255;
    private static final int RESOURCES_MAX_LENGTH = 10;
    private static final String DURATION_ERROR = "appointment.type.duration.error";
    private static final String DELETE_SUCCESS_FLASH =
            AppointmentType2Action.class.getName() + ".deleteSuccess";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private List<Site> activeSites;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    @Override
    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        String sOper = request.getParameter("oper");
        if (isMutation(sOper) && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        restoreDeleteSuccessMessage();

        int typeNo = -1;
        if (EDIT.equals(sOper) || SAVE.equals(sOper) || DELETE.equals(sOper)) {
            typeNo = resolveTypeNumber();
        }
        if ((EDIT.equals(sOper) || DELETE.equals(sOper)) && typeNo <= 0 && !hasActionErrors()) {
            addActionError(getText("appointment.type.number.error"));
        }
        if (hasActionErrors()) {
            return failure();
        }

        if (sOper != null) {
            AppointmentTypeDao appDao = getAppointmentTypeDao();

            if (EDIT.equals(sOper)) {
                AppointmentType dbBean = appDao.find(typeNo);
                if (dbBean != null) {
                    this.setId(dbBean.getId());
                    this.setName(dbBean.getName());
                    this.setDuration(Integer.toString(dbBean.getDuration()));
                    this.setLocation(dbBean.getLocation());
                    this.setNotes(dbBean.getNotes());
                    this.setReason(dbBean.getReason());
                    this.setResources(dbBean.getResources());
                } else {
                    addActionError(getText("appointment.type.notfound.error"));
                    return failure();
                }
            } else if (SAVE.equals(sOper)) {
                Integer parsedDuration = validateSave();
                if (hasActionErrors()) {
                    return failure();
                }

                if (typeNo <= 0) {
                    AppointmentType bean = new AppointmentType();
                    populateBean(bean, parsedDuration);
                    appDao.persist(bean);
                } else {
                    AppointmentType bean = appDao.find(typeNo);
                    if (bean != null) {
                        populateBean(bean, parsedDuration);
                        appDao.merge(bean);
                    } else {
                        addActionError(getText("appointment.type.notfound.error"));
                        return failure();
                    }
                }
                clearForm();
                addActionMessage(getText("appointment.type.saved.message"));
            } else if (DELETE.equals(sOper)) {
                try {
                    appDao.remove(typeNo);
                    request.getSession().setAttribute(DELETE_SUCCESS_FLASH, Boolean.TRUE);
                    return "redirect";
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Could not delete appointment type {}", typeNo, e);
                    addActionError(getText("appointment.type.delete.error"));
                    return failure();
                }
            } else {
                addActionError(getText("appointment.type.oper.error"));
                return failure();
            }
        }

        populateLocations();

        return SUCCESS;
    }

    private void restoreDeleteSuccessMessage() {
        if (Boolean.TRUE.equals(request.getSession().getAttribute(DELETE_SUCCESS_FLASH))) {
            request.getSession().removeAttribute(DELETE_SUCCESS_FLASH);
            addActionMessage(getText("appointment.type.deleted.message"));
        }
    }

    private String failure() {
        populateLocations();
        return "failure";
    }

    private void populateLocations() {
        if (isMultisitesEnabled()) {
            List<LabelValueBean> locations = new ArrayList<LabelValueBean>();
            for (Site site : getActiveSites()) {
                locations.add(new LabelValueBean(site.getName(), Integer.toString(site.getSiteId())));
            }
            request.setAttribute("locationsList", locations);
        }
    }

    private boolean isMutation(String operation) {
        return SAVE.equals(operation) || DELETE.equals(operation);
    }

    private int resolveTypeNumber() {
        String rawNumber = request.getParameter("id");
        if (rawNumber == null || rawNumber.isBlank()) {
            rawNumber = request.getParameter("no");
        }
        if (rawNumber == null || rawNumber.isBlank()) {
            return -1;
        }

        try {
            int parsed = Integer.parseInt(rawNumber);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            addActionError(getText("appointment.type.number.error"));
            return -1;
        }
    }

    private Integer validateSave() {
        if (name == null || name.trim().isEmpty() || name.trim().length() > NAME_MAX_LENGTH) {
            addActionError(getText("appointment.type.name.error"));
        }
        validateLength(normalize(reason), TEXT_MAX_LENGTH, "appointment.type.reason.length.error");
        validateLength(normalize(notes), TEXT_MAX_LENGTH, "appointment.type.notes.length.error");
        validateLength(normalize(location), LOCATION_MAX_LENGTH, "appointment.type.location.length.error");
        validateLength(normalize(resources), RESOURCES_MAX_LENGTH, "appointment.type.resources.length.error");
        validateMultisiteLocation();

        String normalizedDuration = normalize(duration);
        if (normalizedDuration == null || normalizedDuration.isEmpty()
                || !normalizedDuration.matches("\\d+")) {
            addActionError(getText(DURATION_ERROR));
            return null;
        }
        try {
            int parsed = Integer.parseInt(normalizedDuration);
            if (parsed <= 0) {
                addActionError(getText(DURATION_ERROR));
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            addActionError(getText(DURATION_ERROR));
            return null;
        }
    }

    private void validateLength(String value, int maximum, String messageKey) {
        if (value != null && value.length() > maximum) {
            addActionError(getText(messageKey));
        }
    }

    private void validateMultisiteLocation() {
        if (!isMultisitesEnabled()) {
            return;
        }

        List<Site> sites = getActiveSites();
        if (sites.isEmpty()) {
            return;
        }

        String normalizedLocation = normalize(location);
        for (Site site : sites) {
            String siteName = normalize(site.getName());
            if (siteName != null && siteName.equals(normalizedLocation)) {
                return;
            }
        }
        addActionError(getText("appointment.type.location.error"));
    }

    private List<Site> getActiveSites() {
        if (activeSites == null) {
            activeSites = getSiteDao().getAllActiveSites();
        }
        return activeSites;
    }

    private void populateBean(AppointmentType bean, int parsedDuration) {
        bean.setName(normalize(name));
        bean.setDuration(parsedDuration);
        bean.setLocation(normalize(location));
        bean.setNotes(normalize(notes));
        bean.setReason(normalize(reason));
        bean.setResources(normalize(resources));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private void clearForm() {
        id = null;
        name = null;
        notes = null;
        reason = null;
        location = null;
        resources = null;
        duration = null;
    }

    protected AppointmentTypeDao getAppointmentTypeDao() {
        return SpringUtils.getBean(AppointmentTypeDao.class);
    }

    protected SiteDao getSiteDao() {
        return SpringUtils.getBean(SiteDao.class);
    }

    protected boolean isMultisitesEnabled() {
        return IsPropertiesOn.isMultisitesEnable();
    }

    private Integer id;
    private String name;
    private String notes;
    private String reason;
    private String location;
    private String resources;
    private String duration;

    public Integer getId() {
        return id;
    }

    @StrutsParameter
    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    @StrutsParameter
    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    @StrutsParameter
    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getReason() {
        return reason;
    }

    @StrutsParameter
    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getLocation() {
        return location;
    }

    @StrutsParameter
    public void setLocation(String location) {
        this.location = location;
    }

    public String getResources() {
        return resources;
    }

    @StrutsParameter
    public void setResources(String resources) {
        this.resources = resources;
    }

    public String getDuration() {
        return duration;
    }

    @StrutsParameter
    public void setDuration(String duration) {
        this.duration = duration;
    }

}
