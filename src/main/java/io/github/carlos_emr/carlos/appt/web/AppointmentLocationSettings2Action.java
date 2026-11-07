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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.appt.location.AppointmentLocationChangeRefusedException;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationList;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationResolver;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationService;
import io.github.carlos_emr.carlos.appt.location.AppointmentLocationStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Appointment Settings, Locations tab ({@code appointment/apptLocationSetting}): the appointment
 * Location List (issue #3982).
 *
 * <p>Routed on the {@code dispatch} request parameter. A request without one renders the list;
 * any {@code dispatch} asks for a change and must be posted, even one naming no change. The
 * changes, each acting on location {@code id} where it names one:</p>
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

    private static final String ADD_MANY = "addMany";
    private static final String MOVE_UP = "moveUp";
    private static final String DEACTIVATE = "deactivate";
    private static final String SAVED_MESSAGE = "admin.appt.location.msg.saved";

    private static final Set<String> MUTATIONS = Set.of(
            "add", ADD_MANY, "rename", "updateColour", "updateIcon", MOVE_UP, "moveDown", DEACTIVATE, "restore");

    private static final String ADDED_ONE = "add";
    private static final String ADDED_MANY = "addMany:";

    // Struts creates this constructor-injected action per request; it is never session state.
    // Its inherited Serializable marker must not turn live Spring services into serialized data.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient AppointmentLocationService locationService;
    private final transient TransactionOperations transactions;

    private Integer id;
    private Integer position;
    private String name;
    private String names;
    private String colour;
    private String icon;

    public AppointmentLocationSettings2Action(SecurityInfoManager securityInfoManager,
                                              AppointmentLocationService locationService,
                                              PlatformTransactionManager transactionManager) {
        this.securityInfoManager = securityInfoManager;
        this.locationService = locationService;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        // The service's own READ COMMITTED is ignored once it joins this transaction (see
        // AppointmentLocationService.lockList), so the add starts at that level here.
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactions = template;
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
        // The page is opened without a dispatch, so any dispatch asks for a change: it needs write
        // and a POST even when it names none (HttpMethodGuardFilter lists the route by it).
        boolean writeIntent = dispatch != null;
        boolean mutation = writeIntent && MUTATIONS.contains(dispatch);
        String privilege = SecurityInfoManager.READ;
        if (writeIntent) {
            privilege = DEACTIVATE.equals(dispatch) ? SecurityInfoManager.DELETE : SecurityInfoManager.WRITE;
        }
        if (!AppointmentLocationService.hasLocationPrivilege(securityInfoManager, loggedInInfo, privilege)) {
            throw new SecurityException("missing required sec object (_admin.schedule)");
        }

        if (writeIntent && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if (mutation) {
            String refused = apply(loggedInInfo, request, response, dispatch);
            if (refused != null) {
                return refused;
            }
        }

        AppointmentLocationList locations = locationService.load(loggedInInfo);
        request.setAttribute("locations", locations.getItemsActiveFirst());
        request.setAttribute("activeLocationCount", locations.getActiveItems().size());
        request.setAttribute("siteSetup", locations.isSiteSetup());
        boolean canChange = AppointmentLocationService.hasLocationPrivilege(securityInfoManager, loggedInInfo,
                SecurityInfoManager.WRITE);
        request.setAttribute("canChange", canChange);
        if (canChange) {
            // One token per form, bound to its dispatch: a paste sent from a rendering whose add
            // already saved is refused, rather than answered with the add's result.
            request.setAttribute("locationAddToken", FormSubmissionGuard.issue(request.getSession(),
                    FormSubmissionGuard.Scope.APPOINTMENT_LOCATION_ADD, "add"));
            request.setAttribute("locationPasteToken", FormSubmissionGuard.issue(request.getSession(),
                    FormSubmissionGuard.Scope.APPOINTMENT_LOCATION_ADD, ADD_MANY));
        }
        request.setAttribute("canDeactivate",
                AppointmentLocationService.hasLocationPrivilege(securityInfoManager, loggedInInfo, SecurityInfoManager.DELETE));
        request.setAttribute("locationIcons", AppointmentLocationStyle.ICONS);
        request.setAttribute("defaultLocationColour", AppointmentLocationStyle.DEFAULT_COLOUR);
        request.setAttribute("defaultLocationIcon", AppointmentLocationStyle.DEFAULT_ICON);
        request.setAttribute("nameMaxLength", AppointmentLocationResolver.LABEL_MAX_LENGTH);
        request.setAttribute("pasteMaxLines", AppointmentLocationService.PASTE_MAX_LINES);
        request.setAttribute("pasteMaxLength", AppointmentLocationService.PASTE_MAX_LENGTH);
        return SUCCESS;
    }

    /* Applies one change; returns the result to end on instead of the page, or null to render it. */
    private String apply(LoggedInInfo loggedInInfo, HttpServletRequest request, HttpServletResponse response,
                         String dispatch) {
        try {
            switch (dispatch) {
                case "add", ADD_MANY -> {
                    return add(loggedInInfo, request, response, ADD_MANY.equals(dispatch));
                }
                case "rename" -> {
                    locationService.rename(loggedInInfo, requireId(), name);
                    addActionMessage(getText("admin.appt.location.msg.renamed"));
                }
                case "updateColour" -> {
                    locationService.updateColour(loggedInInfo, requireId(), colour);
                    addActionMessage(getText(SAVED_MESSAGE));
                }
                case "updateIcon" -> {
                    locationService.updateIcon(loggedInInfo, requireId(), icon);
                    addActionMessage(getText(SAVED_MESSAGE));
                }
                case MOVE_UP, "moveDown" -> move(loggedInInfo, dispatch);
                case DEACTIVATE -> {
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
            // The page offers the refused entry again, so a long paste is not lost.
            request.setAttribute("refusedDispatch", dispatch);
            request.setAttribute("refusedId", id);
        }
        return null;
    }

    private void move(LoggedInInfo loggedInInfo, String dispatch) {
        if (locationService.move(loggedInInfo, requireId(), MOVE_UP.equals(dispatch), position)) {
            addActionMessage(getText(SAVED_MESSAGE));
        }
    }

    /*
     * Adds one location or a pasted list once per rendering of the page (FormSubmissionGuard,
     * #4225): a double click, a re-click while the first is saving or a reload of the result shows
     * what the first submission added instead of adding again or reporting the name as taken.
     */
    private String add(LoggedInInfo loggedInInfo, HttpServletRequest request, HttpServletResponse response,
                       boolean many) {
        FormSubmissionGuard.Attempt attempt = FormSubmissionGuard.attempt(request.getSession(),
                request.getParameter(FormSubmissionGuard.PARAMETER), FormSubmissionGuard.Scope.APPOINTMENT_LOCATION_ADD,
                many ? ADD_MANY : "add", FormSubmissionGuard.DEFAULT_WAIT_MILLIS);
        switch (attempt.verdict()) {
            case ABSENT, PROCEED -> { /* Continue with a new or legacy submission. */ }
            case ALREADY_SAVED -> {
                if (!addSavedMessage(attempt.result())) {
                    return FormSubmissionGuard.refuse(request, response, FormSubmissionGuard.Verdict.UNCONFIRMED);
                }
                return null;
            }
            default -> {
                return FormSubmissionGuard.refuse(request, response, attempt.verdict());
            }
        }
        try (FormSubmissionGuard.Claim claim = attempt.claim()) {
            // The add commits in a transaction the claim observes: a refused add rolls back and
            // frees the token for a corrected retry.
            String saved = transactions.execute(status -> {
                if (claim != null) {
                    claim.storageStarted();
                }
                if (!many) {
                    locationService.addLocation(loggedInInfo, name);
                    return ADDED_ONE;
                }
                AppointmentLocationService.AddResult result = locationService.addLocations(loggedInInfo, names);
                return ADDED_MANY + result.added() + ":" + result.skipped();
            });
            if (claim != null) {
                claim.saved(saved);
            }
            addSavedMessage(saved);
        }
        return null;
    }

    /* Reports what an add saved, from what it recorded; false if the record cannot be read. */
    private boolean addSavedMessage(String saved) {
        if (ADDED_ONE.equals(saved)) {
            addActionMessage(getText("admin.appt.location.msg.added"));
            return true;
        }
        String[] counts = saved == null || !saved.startsWith(ADDED_MANY) ? new String[0]
                : saved.substring(ADDED_MANY.length()).split(":");
        if (counts.length != 2) {
            return false;
        }
        addActionMessage(getText("admin.appt.location.msg.addedMany", counts));
        return true;
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

    public Integer getPosition() {
        return position;
    }

    @StrutsParameter
    public void setPosition(Integer position) {
        this.position = position;
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
