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

/**
 * Backs the Appointment Types list and editor reached from
 * <em>Administration &gt; Appointment Types</em>. Requires {@code _appointment} write rights.
 *
 * <p>A single {@code oper} request parameter selects the operation, and the same value drives
 * both the HTTP method guard and the dispatch below it, so a duplicated or case-shifted
 * {@code oper} cannot reach a mutation through the guard:
 * <ul>
 *   <li>absent — renders the list with an empty Add form ({@code success})</li>
 *   <li>{@code edit} — loads one record into the form; read-only, so GET is permitted</li>
 *   <li>{@code save} — creates or updates, then {@code redirect}; POST-only</li>
 *   <li>{@code del} — deletes, then {@code redirect}; POST-only</li>
 *   <li>anything else — rejected rather than silently ignored</li>
 * </ul>
 *
 * <p>{@code save} and {@code del} answer 405 with {@code Allow: POST} before any DAO call, and
 * both validate before mutating: invalid input returns {@code failure}, which re-renders the
 * form with the submitted values intact instead of surfacing a bare {@code CARLOS Error: 0}.
 * A successful mutation redirects and carries its notice on a single-shot session flash, so a
 * refresh cannot repeat it; only the two read renders consume that flash.
 * Field limits mirror the {@code appointmentType} columns, and under multisite a non-blank
 * location must name an active site — except the value already stored on the record being
 * edited, which is accepted so a renamed or deactivated site still round-trips.
 *
 * <p>The DAO and multisite lookups are exposed as {@code protected} seams so unit tests can
 * substitute them without a Spring context; they are not an extension point for callers.
 *
 * @since 2024-11-20
 */
public class AppointmentType2Action extends ActionSupport {
    private static final String EDIT = "edit";
    private static final String SAVE = "save";
    private static final String DELETE = "del";
    private static final int NAME_MAX_LENGTH = 50;
    private static final int TEXT_MAX_LENGTH = 80;
    private static final int LOCATION_MAX_LENGTH = 255;
    private static final int RESOURCES_MAX_LENGTH = 10;
    /**
     * An appointment is stored as one {@code appointment_date} plus {@code start_time} and
     * {@code end_time}, so it cannot run past the end of its day. A type longer than one day
     * could never be booked, which makes 24h the ceiling rather than an arbitrary limit.
     */
    private static final int DURATION_MAX_MINUTES = 1440;
    private static final String DURATION_ERROR = "appointment.type.duration.error";
    private static final String FLASH_MESSAGE_KEY =
            AppointmentType2Action.class.getName() + ".flashMessage";

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
        if (isMutation(sOper) && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // Only the two read renders may consume the flash, and this must be an allow-list rather
        // than "not a mutation": an unrecognised oper is not a mutation either, and would swallow
        // a pending notice on its way to being rejected, losing it for the list render that
        // follows. A mutation must not consume one either — a stale page posting a save, or a
        // delete sent by XHR that never follows the redirect, would surface the previous
        // operation's notice on this one's result.
        if (sOper == null || EDIT.equals(sOper)) {
            restoreFlashMessage();
        }

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
                AppointmentType existing = typeNo > 0 ? appDao.find(typeNo) : null;
                if (typeNo > 0 && existing == null) {
                    addActionError(getText("appointment.type.notfound.error"));
                    return failure();
                }

                // The record is loaded before validation so the multisite location check can
                // grandfather the value already stored on it: sites get renamed and deactivated,
                // and an edit to an unrelated field must not be blocked by a location the user
                // never touched.
                Integer parsedDuration = validateSave(existing == null ? null : existing.getLocation());
                if (hasActionErrors()) {
                    return failure();
                }

                if (existing == null) {
                    AppointmentType bean = new AppointmentType();
                    populateBean(bean, parsedDuration);
                    appDao.persist(bean);
                } else {
                    populateBean(existing, parsedDuration);
                    appDao.merge(existing);
                }
                // Redirect rather than render: a refresh on the save response would otherwise
                // re-post and create a duplicate type. The notice is carried across on the flash.
                setFlashMessage("appointment.type.saved.message");
                return "redirect";
            } else if (DELETE.equals(sOper)) {
                // Scoped to the DAO call alone: a wider try would let a failure inside failure()
                // itself be caught, logged as a delete fault, and then re-thrown uncaught by the
                // handler's own second failure() call.
                boolean removed;
                try {
                    removed = appDao.remove(typeNo);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Could not delete appointment type {}", typeNo, e);
                    addActionError(getText("appointment.type.delete.error"));
                    return failure();
                }

                // remove(id) returns false for an already-deleted record instead of throwing, so a
                // stale list page or a double submit would otherwise report success for a delete
                // that never happened.
                if (!removed) {
                    addActionError(getText("appointment.type.notfound.error"));
                    return failure();
                }
                setFlashMessage("appointment.type.deleted.message");
                return "redirect";
            } else {
                addActionError(getText("appointment.type.oper.error"));
                return failure();
            }
        }

        populateLocations();

        return SUCCESS;
    }

    private void setFlashMessage(String messageKey) {
        request.getSession().setAttribute(FLASH_MESSAGE_KEY, messageKey);
    }

    private void restoreFlashMessage() {
        Object pending = request.getSession().getAttribute(FLASH_MESSAGE_KEY);
        if (pending instanceof String messageKey) {
            request.getSession().removeAttribute(FLASH_MESSAGE_KEY);
            addActionMessage(getText(messageKey));
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

    /**
     * @param storedLocation location currently persisted on the record being updated, or
     *                       {@code null} when creating; accepted as-is by the multisite check
     *                       so a retired site name still round-trips through an edit
     */
    private Integer validateSave(String storedLocation) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > NAME_MAX_LENGTH) {
            addActionError(getText("appointment.type.name.error"));
        }
        validateLength(normalize(reason), TEXT_MAX_LENGTH, "appointment.type.reason.length.error");
        validateLength(normalize(notes), TEXT_MAX_LENGTH, "appointment.type.notes.length.error");
        validateLength(normalize(location), LOCATION_MAX_LENGTH, "appointment.type.location.length.error");
        validateLength(normalize(resources), RESOURCES_MAX_LENGTH, "appointment.type.resources.length.error");
        validateMultisiteLocation(storedLocation);

        String normalizedDuration = normalize(duration);
        if (normalizedDuration == null || normalizedDuration.isEmpty()
                || !normalizedDuration.matches("\\d+")) {
            addActionError(getText(DURATION_ERROR));
            return null;
        }
        try {
            int parsed = Integer.parseInt(normalizedDuration);
            if (parsed <= 0 || parsed > DURATION_MAX_MINUTES) {
                addActionError(getText(DURATION_ERROR));
                return null;
            }
            // Re-render the canonical form, so padding this accepted ("  0030 ") comes back as
            // "30". The field shows exactly what would be saved, and the input's pattern needs to
            // admit only canonical values rather than grow a padding grammar to match this method.
            // A value that failed to parse is left as typed so the user can see what was wrong.
            duration = Integer.toString(parsed);
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

    private void validateMultisiteLocation(String storedLocation) {
        if (!isMultisitesEnabled()) {
            return;
        }

        // Location stays optional under multisite: the list page submits an empty value for the
        // "Select Location" placeholder, and requiring a site would make the form unsubmittable
        // for clinics that do not tag appointment types by site.
        String normalizedLocation = normalize(location);
        if (normalizedLocation == null || normalizedLocation.isEmpty()) {
            return;
        }

        List<Site> sites = getActiveSites();
        if (sites.isEmpty()) {
            return;
        }

        if (normalizedLocation.equals(normalize(storedLocation))) {
            return;
        }

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
