/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prevention.gate;

/**
 * View gate for {@code prevention/index.jsp}, the prevention-module
 * landing page launched from the encounter left-nav popup and the demographic
 * "prevention" link. Enforces {@code _prevention r} before forwarding to the
 * JSP at its {@code /WEB-INF/jsp/} location.
 *
 * <p>Replaces the previous direct JSP access
 * ({@code /prevention/index.jsp?demographic_no=...}) that was removed
 * when the JSP was moved behind {@code /WEB-INF/jsp/}. Callers must now
 * target {@code /prevention/ViewPreventionIndex.do?demographic_no=...}
 * instead of {@code /encounter/displayPrevention.do}, which is the encounter
 * left-nav sub-dispatcher and does not render the prevention dashboard.
 *
 * @since 2026-04-13
 */
public final class ViewPreventionIndex2Action extends AbstractPreventionGate2Action {

    @Override
    protected String privilegeType() {
        return "r";
    }
}
