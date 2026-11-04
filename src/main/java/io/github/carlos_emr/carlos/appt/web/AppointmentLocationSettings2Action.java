/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appt.web;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import io.github.carlos_emr.carlos.appt.location.AppointmentLocationChangeRefusedException;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationList;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationResolver;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationService;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Appointment Settings, Locations tab ({@code appointment/apptLocationSetting}): the appointment
 * Location List (issue #3982).
 *
 * <p>Routed on the {@code dispatch} request parameter. Anything other than a change renders the
 * list. The changes, each acting on location {@code id} where it names one:</p>
 * <ul>
 *   <li>{@code add} adds {@code name}; {@code addMany} adds every line of {@code names}.</li>
 *   <li>{@code rename} renames to {@code name}; {@code updateColour} and {@code updateIcon} set
 *       {@code colour} or {@code icon}, a blank value meaning the default chip style.</li>
 *   <li>{@code moveUp} and {@code moveDown} move an active location along the booking order.</li>
 *   <li>{@code deactivate} and {@code restore} take a location out of new bookings and back.</li>
 * </ul>
 *
 * <p>Reading needs read, every change write, and {@code deactivate} delete, on
 * {@code _admin.schedule} or {@code _admin} ({@link AppointmentLocationService}). Changes must be
 * posted: GET and HEAD answer 405 before anything is changed. A refused change renders the page
 * with its reason and saves nothing.</p>
 *
 * @since 2026-11-04
 */
public class AppointmentLocationSettings2Action extends ActionSupport {

    private static final Set<String> MUTATIONS = Set.of(
            "add", "addMany", "rename", "updateColour", "updateIcon", "moveUp", "moveDown", "deactivate", "restore");

    private final SecurityInfoManager securityInfoManager;
    private final AppointmentLocationService locationService;

    private Integer id;
    private String name;
    private String names;
    private String colour;
    private String icon;

    public AppointmentLocationSettings2Action(SecurityInfoManager securityInfoManager,
                                              AppointmentLocationService locationService) {
        this.securityInfoManager = securityInfoManager;
        this.locationService = locationService;
    }

    /**
     * Renders the Location List, or applies the change named by {@code dispatch} and renders it.
     *
     * @return String {@code success}, or {@code none} after answering 405 to an unposted change
     * @throws SecurityException if the user lacks the privilege the request needs
     */
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String dispatch = request.getParameter("dispatch");
        boolean mutation = dispatch != null && MUTATIONS.contains(dispatch);
        String privilege = !mutation ? SecurityInfoManager.READ
                : "deactivate".equals(dispatch) ? SecurityInfoManager.DELETE
                : SecurityInfoManager.WRITE;
        if (!AppointmentLocationService.hasLocationPrivilege(securityInfoManager, loggedInInfo, privilege)) {
            throw new SecurityException("missing required sec object (_admin.schedule)");
        }

        if (mutation && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if (mutation) {
            apply(loggedInInfo, dispatch);
        }

        AppointmentLocationList locations = locationService.load(loggedInInfo);
        request.setAttribute("locations", locations.getItemsActiveFirst());
        request.setAttribute("activeLocationCount", locations.getActiveItems().size());
        request.setAttribute("siteSetup", locations.isSiteSetup());
        request.setAttribute("canChange",
                AppointmentLocationService.hasLocationPrivilege(securityInfoManager, loggedInInfo, SecurityInfoManager.WRITE));
        request.setAttribute("canDeactivate",
                AppointmentLocationService.hasLocationPrivilege(securityInfoManager, loggedInInfo, SecurityInfoManager.DELETE));
        request.setAttribute("locationIcons", AppointmentLocationStyle.ICONS);
        request.setAttribute("defaultLocationColour", AppointmentLocationStyle.DEFAULT_COLOUR);
        request.setAttribute("nameMaxLength", AppointmentLocationResolver.LABEL_MAX_LENGTH);
        request.setAttribute("pasteMaxLines", AppointmentLocationService.PASTE_MAX_LINES);
        return SUCCESS;
    }

    private void apply(LoggedInInfo loggedInInfo, String dispatch) {
        try {
            switch (dispatch) {
                case "add" -> {
                    locationService.addLocation(loggedInInfo, name);
                    addActionMessage(getText("admin.appt.location.msg.added"));
                }
                case "addMany" -> {
                    AppointmentLocationService.AddResult result = locationService.addLocations(loggedInInfo, names);
                    addActionMessage(getText("admin.appt.location.msg.addedMany",
                            new String[]{String.valueOf(result.added()), String.valueOf(result.skipped())}));
                }
                case "rename" -> {
                    locationService.rename(loggedInInfo, requireId(), name);
                    addActionMessage(getText("admin.appt.location.msg.renamed"));
                }
                case "updateColour" -> {
                    locationService.updateColour(loggedInInfo, requireId(), colour);
                    addActionMessage(getText("admin.appt.location.msg.saved"));
                }
                case "updateIcon" -> {
                    locationService.updateIcon(loggedInInfo, requireId(), icon);
                    addActionMessage(getText("admin.appt.location.msg.saved"));
                }
                case "moveUp", "moveDown" -> {
                    if (locationService.move(loggedInInfo, requireId(), "moveUp".equals(dispatch))) {
                        addActionMessage(getText("admin.appt.location.msg.saved"));
                    }
                }
                case "deactivate" -> {
                    locationService.deactivate(loggedInInfo, requireId());
                    addActionMessage(getText("admin.appt.location.msg.deactivated"));
                }
                default -> {
                    locationService.restore(loggedInInfo, requireId());
                    addActionMessage(getText("admin.appt.location.msg.restored"));
                }
            }
        } catch (AppointmentLocationChangeRefusedException e) {
            addActionError(e.getMessageParam() == null ? getText(e.getMessageKey())
                    : getText(e.getMessageKey(), new String[]{e.getMessageParam()}));
        }
    }

    private int requireId() {
        if (id == null || id <= 0) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.notFound", null);
        }
        return id;
    }

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

    public String getNames() {
        return names;
    }

    @StrutsParameter
    public void setNames(String names) {
        this.names = names;
    }

    public String getColour() {
        return colour;
    }

    @StrutsParameter
    public void setColour(String colour) {
        this.colour = colour;
    }

    public String getIcon() {
        return icon;
    }

    @StrutsParameter
    public void setIcon(String icon) {
        this.icon = icon;
    }
}
