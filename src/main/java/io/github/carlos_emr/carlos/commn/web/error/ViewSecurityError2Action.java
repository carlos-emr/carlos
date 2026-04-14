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
package io.github.carlos_emr.carlos.commn.web.error;

import org.apache.struts2.ActionSupport;

/**
 * Thin public shim that forwards to {@code /WEB-INF/jsp/error/securityError.jsp}.
 *
 * <p>Historically many JSPs top-gate with a {@code <security:oscarSec>} tag
 * whose failure branch does {@code response.sendRedirect(ctx + "/securityError.jsp?type=X")}.
 * After the webapp-root foldering (this PR), the WEB-INF JSP is no longer
 * directly reachable via client redirect. This action provides a stable
 * public {@code /error/securityError.do} URL that forwards internally.
 *
 * <p>No privilege check — error pages must remain anonymous-reachable by
 * definition (the caller by definition lacks the privilege that would have
 * allowed passing through the originating action).
 */
public final class ViewSecurityError2Action extends ActionSupport {

    @Override
    public String execute() throws Exception {
        return SUCCESS;
    }
}
