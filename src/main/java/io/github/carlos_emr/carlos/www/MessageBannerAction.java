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
package io.github.carlos_emr.carlos.www;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Method-dispatch base for the two schedule message banners.
 *
 * <p>{@link SystemMessage2Action} and {@link OrganizationMessage2Action} read different stores
 * but share one authorization rule, so the rule lives here once rather than being restated in
 * each {@code execute()}. Subclasses supply only the four methods and their own privilege
 * checker.
 *
 * <p>The rule, from issue #3728: {@code ?method=view} renders the read-only banner that the
 * provider schedule ({@code appointmentprovideradminday.jsp}) fetches on every page load. Both
 * banners carry administrator-authored notices addressed to everyone working in the clinic and
 * hold no PHI, so the read is authorized on an authenticated session alone. Requiring
 * {@code _admin} write on that path turned every non-admin clinician's schedule load into a 403
 * plus a logged {@code SecurityException}. Every other method is the administrative management
 * UI — listing, editing and saving the messages themselves — and stays behind {@code _admin}
 * write.
 *
 * @since 2026-09-18
 */
abstract class MessageBannerAction extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * The privilege checker guarding the management methods. Supplied by the subclass so each
     * action keeps the instance it was wired with rather than sharing one on this base.
     */
    protected abstract SecurityInfoManager securityInfoManager();

    /** Renders the read-only banner. Reached on an authenticated session, with no privilege. */
    public abstract String view();

    /** Administrative: opens one message for editing. Requires {@code _admin} write. */
    public abstract String edit();

    /** Administrative: persists a message. Requires {@code _admin} write. */
    public abstract String save();

    /** Administrative: lists the messages. The default when no method is given. */
    public abstract String list();

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String mtd = request.getParameter("method");

        if ("view".equals(mtd)) {
            // LoginFilter is the canonical session gate; this null check is defence in depth
            // for direct invocation and for filter reordering. The subclass's view() still
            // scopes what it reads to the session's own facility and program domain.
            if (loggedInInfo == null) {
                throw new SecurityException("not logged in");
            }
            return view();
        }

        if (!securityInfoManager().hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        if ("edit".equals(mtd)) {
            return edit();
        } else if ("save".equals(mtd)) {
            return save();
        }
        return list();
    }
}
