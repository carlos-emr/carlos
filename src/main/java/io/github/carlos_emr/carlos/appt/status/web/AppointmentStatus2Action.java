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

package io.github.carlos_emr.carlos.appt.status.web;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;
import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusStyleException;
import io.github.carlos_emr.carlos.appt.status.service.impl.AppointmentStatusMgrImpl;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Appointment Settings, Status tab ({@code appointment/apptStatusSetting}): lists the appointment
 * statuses and edits them inline.
 *
 * <p>Routed on the {@code dispatch} request parameter:</p>
 * <ul>
 *   <li>{@code view}, or anything else: render the list. {@code modify}, the retired separate edit
 *       page, also renders the list so bookmarked links keep working.</li>
 *   <li>{@code updateDescription}, {@code updateColour}, {@code updateIcon}: set status {@code id}'s
 *       description ({@code apptDesc}), colour ({@code apptColor}) or icon ({@code apptIcon}). Each
 *       field is its own form, so saving one never rewrites another, in particular a legacy
 *       non-hexadecimal colour (#3381) is only replaced by an explicit colour save.</li>
 *   <li>{@code changestatus}: enable or disable status {@code id} ({@code active} 1 or 0). The page
 *       asks for confirmation before it posts a disable.</li>
 *   <li>{@code reset}: restore the seeded description, colour and icon of every seeded status.</li>
 * </ul>
 *
 * <p>Style edits apply to every status, including the core statuses seeded with
 * {@code editable=0}: in CARLOS that flag gates only enabling and disabling (#3888).</p>
 *
 * <p>Reads need read and every change write on {@code _admin.schedule} or {@code _admin}, as on the
 * Locations tab; every change must
 * be posted (GET and HEAD answer 405 before anything is changed) and writes an audit row that names
 * the status id and the field, never patient data.</p>
 */
public class AppointmentStatus2Action extends ActionSupport {
    private static final String SCHEDULE_ADMIN_SECURITY_OBJECT = "_admin.schedule";
    private static final String ADMIN_SECURITY_OBJECT = "_admin";
    private static final String READ = "r";
    private static final String WRITE = "w";
    private static final Set<String> MUTATIONS = Set.of(
            "reset", "changestatus", "updateDescription", "updateColour", "updateIcon");

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private Integer id;
    private Integer active;
    private String apptDesc;
    private String apptColor;
    private String apptIcon;

    /**
     * Renders the status list, or applies the change named by {@code dispatch}.
     *
     * @return String {@code success} to render the list, or {@code none} after answering 405
     * @throws SecurityException if the user lacks read (or write, for a change) on both
     *         {@code _admin.schedule} and {@code _admin}
     */
    @Override
    public String execute() {
        String dispatch = request.getParameter("dispatch");
        boolean mutation = dispatch != null && MUTATIONS.contains(dispatch);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String requiredPrivilege = mutation ? WRITE : READ;
        if (!securityInfoManager.hasPrivilege(loggedInInfo, SCHEDULE_ADMIN_SECURITY_OBJECT, requiredPrivilege, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, ADMIN_SECURITY_OBJECT, requiredPrivilege, null)) {
            throw new SecurityException("missing required sec object (_admin.schedule)");
        }

        if (mutation && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if (!mutation) {
            return view();
        }
        switch (dispatch) {
            case "reset" -> reset();
            case "changestatus" -> changestatus();
            case "updateDescription" -> changeStyle(dispatch, "description",
                    () -> getApptStatusMgr().updateDescription(id, apptDesc));
            case "updateColour" -> changeStyle(dispatch, "colour",
                    () -> getApptStatusMgr().updateColour(id, apptColor));
            default -> changeStyle(dispatch, "icon",
                    () -> getApptStatusMgr().updateIcon(id, apptIcon));
        }
        populateAllStatus(request);
        return SUCCESS;
    }

    /**
     * Renders the status list.
     *
     * @return String {@code success}
     */
    public String view() {
        populateAllStatus(request);
        return SUCCESS;
    }

    private void reset() {
        getApptStatusMgr().reset();
        audit("reset", "seeded appointment statuses restored to their default description, colour and icon");
        addActionMessage(getText("admin.appt.status.mgr.message.reset"));
    }

    private void changestatus() {
        AppointmentStatus appointmentStatus = getExistingStatus();
        if (appointmentStatus == null) {
            return;
        }
        // editable=0 marks a core status: it can be restyled but never disabled (#3888).
        if (appointmentStatus.getEditable() != 1) {
            addActionError(getText("admin.appt.status.mgr.error.notEditable"));
            return;
        }
        if (active == null || (active != 0 && active != 1)) {
            addActionError(getText("admin.appt.status.mgr.error.invalidActive"));
            return;
        }
        try {
            if (!getApptStatusMgr().changeStatus(id, active)) {
                addActionError(getText("admin.appt.status.mgr.error.notEditable"));
                return;
            }
        } catch (AppointmentStatusStyleException e) {
            addActionError(getText(e.getMessageKey()));
            return;
        }
        audit("changestatus", "appointment_status id=" + id + " active=" + active);
        addActionMessage(getText("admin.appt.status.mgr.message.activationUpdated"));
    }

    /* Applies one inline style edit, reporting a refused value instead of saving it. */
    private void changeStyle(String dispatch, String field, Supplier<Boolean> update) {
        if (getExistingStatus() == null) {
            return;
        }
        try {
            if (!update.get()) {
                addActionError(getText("admin.appt.status.mgr.error.notFound"));
                return;
            }
        } catch (AppointmentStatusStyleException e) {
            addActionError(getText(e.getMessageKey()));
            return;
        }
        audit(dispatch, "appointment_status id=" + id + " " + field + " updated");
        addActionMessage(getText("admin.appt.status.mgr.message.updated"));
    }

    /**
     * Writes the audit row for a saved change. The data names the status and the field changed,
     * never patient data.
     *
     * @param dispatch String the change applied
     * @param data String what changed
     */
    protected void audit(String dispatch, String data) {
        LogAction.addLogSynchronous(LoggedInInfo.getLoggedInInfoFromSession(request),
                "AppointmentStatus2Action." + dispatch, data);
    }

    private AppointmentStatus getExistingStatus() {
        if (id == null || id <= 0) {
            addActionError(getText("admin.appt.status.mgr.error.invalidId"));
            return null;
        }

        AppointmentStatus appointmentStatus = getApptStatusMgr().getStatus(id);
        if (appointmentStatus == null) {
            addActionError(getText("admin.appt.status.mgr.error.notFound"));
        }
        return appointmentStatus;
    }

    /**
     * The status manager this action uses; tests substitute their own.
     *
     * @return AppointmentStatusMgr the manager
     */
    public AppointmentStatusMgr getApptStatusMgr() {
        return new AppointmentStatusMgrImpl();
    }

    private void populateAllStatus(HttpServletRequest request) {
        AppointmentStatusMgr apptStatusMgr = getApptStatusMgr();
        List<AppointmentStatus> allStatus = apptStatusMgr.getAllStatus();
        request.setAttribute("allStatus", allStatus);
        request.setAttribute("iconSet", AppointmentStatusMgr.ICON_SET);
        request.setAttribute("descriptionMaxLength", AppointmentStatusMgr.DESCRIPTION_MAX_LENGTH);
        // A user who can only read sees the controls disabled; the action would refuse their changes.
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        request.setAttribute("canChange",
                securityInfoManager.hasPrivilege(loggedInInfo, SCHEDULE_ADMIN_SECURITY_OBJECT, WRITE, null)
                        || securityInfoManager.hasPrivilege(loggedInInfo, ADMIN_SECURITY_OBJECT, WRITE, null));
        int usedStatusIndex = apptStatusMgr.checkStatusUsuage(allStatus);
        if (usedStatusIndex >= 0 && usedStatusIndex < allStatus.size()) {
            request.setAttribute("useStatus", allStatus.get(usedStatusIndex).getStatus());
        }
    }

    public Integer getId() {
        return id;
    }

    @StrutsParameter
    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getActive() {
        return active;
    }

    @StrutsParameter
    public void setActive(Integer active) {
        this.active = active;
    }

    public String getApptDesc() {
        return apptDesc;
    }

    @StrutsParameter
    public void setApptDesc(String apptDesc) {
        this.apptDesc = apptDesc;
    }

    public String getApptColor() {
        return apptColor;
    }

    @StrutsParameter
    public void setApptColor(String apptColor) {
        this.apptColor = apptColor;
    }

    public String getApptIcon() {
        return apptIcon;
    }

    @StrutsParameter
    public void setApptIcon(String apptIcon) {
        this.apptIcon = apptIcon;
    }
}
